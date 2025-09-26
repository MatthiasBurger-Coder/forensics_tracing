package de.burger.forensics.plugin.io;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/**
 * Buffered per-shard writer that streams rules to disk to keep heap usage low.
 */
public final class ShardedWriter implements Closeable, Flushable {
    private static final int DEFAULT_BUFFER_SIZE = 1 << 16;
    private static final int DEFAULT_FLUSH_THRESHOLD = 64 * 1024;

    private final File outDir;
    private final Writer[] writers;
    private final CountingOutputStream[] countingStreams;
    private final boolean[] shardHasRules;
    private final long[] bytesSinceFlush;
    private final long[] currentBytes;
    private final long[] openedAtMillis;
    private final long[] lastFlushAtMillis;
    private final int[] rotationIndex;
    private final Object[] shardLocks;
    private final int shards;
    private final boolean gzip;
    private final String filePrefix;
    private final long rotateMaxBytesPerFile;
    private final long rotateIntervalMillis;
    private final int flushThresholdBytes;
    private final long flushIntervalMillis;
    private final boolean threadSafe;
    private final boolean rotationEnabled;

    private boolean closed;
    private boolean headerWritten;
    private String headerText = "";
    private int headerBytes;

    /**
     * Creates a writer that shards output files into {@code shards} parts using the given prefix.
     */
    public ShardedWriter(File outDir, int shards, boolean gzip, String filePrefix) throws IOException {
        this(outDir, shards, gzip, filePrefix, 4L * 1024 * 1024, 0L, DEFAULT_FLUSH_THRESHOLD, 2000L, false);
    }

    ShardedWriter(File outDir, int shards, boolean gzip, String filePrefix, int flushThresholdBytes) throws IOException {
        this(outDir, shards, gzip, filePrefix, 4L * 1024 * 1024, 0L, flushThresholdBytes, 2000L, false);
    }

    public ShardedWriter(
        File outDir,
        int shards,
        boolean gzip,
        String filePrefix,
        long rotateMaxBytesPerFile,
        long rotateIntervalSeconds,
        int flushThresholdBytes,
        long flushIntervalMillis,
        boolean threadSafe
    ) throws IOException {
        Objects.requireNonNull(outDir, "outDir");
        Objects.requireNonNull(filePrefix, "filePrefix");
        int shardCount = Math.max(1, shards);
        this.outDir = outDir;
        this.gzip = gzip;
        this.filePrefix = filePrefix;
        this.rotateMaxBytesPerFile = Math.max(0L, rotateMaxBytesPerFile);
        this.rotateIntervalMillis = rotateIntervalSeconds <= 0L ? 0L : rotateIntervalSeconds * 1000L;
        this.flushThresholdBytes = flushThresholdBytes <= 0 ? 0 : flushThresholdBytes;
        this.flushIntervalMillis = Math.max(0L, flushIntervalMillis);
        this.threadSafe = threadSafe;
        this.rotationEnabled = this.rotateMaxBytesPerFile > 0L || this.rotateIntervalMillis > 0L;
        this.shards = shardCount;
        this.writers = new Writer[shardCount];
        this.countingStreams = new CountingOutputStream[shardCount];
        this.shardHasRules = new boolean[shardCount];
        this.bytesSinceFlush = new long[shardCount];
        this.currentBytes = new long[shardCount];
        this.openedAtMillis = new long[shardCount];
        this.lastFlushAtMillis = new long[shardCount];
        this.rotationIndex = new int[shardCount];
        this.shardLocks = threadSafe ? new Object[shardCount] : null;
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Cannot create output dir: " + outDir);
        }
        long now = now();
        for (int i = 0; i < shardCount; i++) {
            if (threadSafe) {
                shardLocks[i] = new Object();
            }
            rotationIndex[i] = rotationEnabled ? 1 : 0;
            openedAtMillis[i] = now;
            lastFlushAtMillis[i] = now;
            openShardWriter(i);
        }
    }

    /**
     * Writes the shared header to all shard files. Must be invoked before appending rules.
     */
    public synchronized void writeHeader(String header) throws IOException {
        ensureOpen();
        if (headerWritten) {
            return;
        }
        headerText = header == null ? "" : header;
        headerBytes = headerText.getBytes(StandardCharsets.UTF_8).length;
        for (int i = 0; i < writers.length; i++) {
            Writer writer = writers[i];
            writer.write(headerText);
            if (headerBytes > 0) {
                bytesSinceFlush[i] += headerBytes;
            }
            updateCurrentBytes(i);
        }
        headerWritten = true;
    }

    /**
     * Appends one rule to the target shard. Rules are separated by blank lines.
     */
    public void append(int shard, String rule) throws IOException {
        ensureOpen();
        if (!headerWritten) {
            throw new IllegalStateException("writeHeader must be called before append");
        }
        int target = normalizeShard(shard);
        if (threadSafe) {
            synchronized (shardLocks[target]) {
                appendInternal(target, rule);
            }
        } else {
            appendInternal(target, rule);
        }
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        if (threadSafe) {
            for (int i = 0; i < writers.length; i++) {
                synchronized (shardLocks[i]) {
                    flushShardLocked(i);
                }
            }
        } else {
            for (int i = 0; i < writers.length; i++) {
                flushShardLocked(i);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        IOException failure = null;
        for (int i = 0; i < writers.length; i++) {
            if (threadSafe) {
                synchronized (shardLocks[i]) {
                    failure = closeShard(i, failure);
                }
            } else {
                failure = closeShard(i, failure);
            }
        }
        closed = true;
        if (failure != null) {
            throw failure;
        }
    }

    private int normalizeShard(int shard) {
        if (shard < 0 || shard >= shards) {
            return 0;
        }
        return shard;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("ShardedWriter is closed");
        }
    }

    private void appendInternal(int shard, String rule) throws IOException {
        updateCurrentBytes(shard);
        long now = now();
        if (rotateIntervalMillis > 0 && now - openedAtMillis[shard] >= rotateIntervalMillis) {
            rotateShardLocked(shard);
        }
        long estimatedBytes = estimateBytes(rule);
        long projected = currentBytes[shard] + bytesSinceFlush[shard] + estimatedBytes;
        if (rotateMaxBytesPerFile > 0 && projected > rotateMaxBytesPerFile) {
            rotateShardLocked(shard);
        }
        Writer writer = writers[shard];
        writer.write(rule);
        writer.write("\n\n");
        shardHasRules[shard] = true;
        bytesSinceFlush[shard] += estimatedBytes;
        updateCurrentBytes(shard);
        boolean flushed = false;
        if (flushThresholdBytes > 0 && bytesSinceFlush[shard] >= flushThresholdBytes) {
            flushShardLocked(shard);
            flushed = true;
        }
        long currentTime = now();
        if (!flushed && flushIntervalMillis > 0 && currentTime - lastFlushAtMillis[shard] >= flushIntervalMillis) {
            flushShardLocked(shard);
        }
    }

    private void flushShardLocked(int shard) throws IOException {
        Writer writer = writers[shard];
        writer.flush();
        updateCurrentBytes(shard);
        bytesSinceFlush[shard] = 0L;
        lastFlushAtMillis[shard] = now();
    }

    private void rotateShardLocked(int shard) throws IOException {
        if (!rotationEnabled) {
            return;
        }
        Writer writer = writers[shard];
        writer.flush();
        updateCurrentBytes(shard);
        writer.close();
        int nextIndex = rotationIndex[shard] <= 0 ? 1 : rotationIndex[shard] + 1;
        rotationIndex[shard] = nextIndex;
        openShardWriter(shard);
    }

    private void openShardWriter(int shard) throws IOException {
        String name = fileName(shard, rotationIndex[shard]);
        File file = new File(outDir, name);
        OutputStream os = new FileOutputStream(file);
        CountingOutputStream counting = new CountingOutputStream(os);
        OutputStream target = counting;
        if (gzip) {
            target = new GZIPOutputStream(target, DEFAULT_BUFFER_SIZE);
        }
        Writer writer = new BufferedWriter(new OutputStreamWriter(target, StandardCharsets.UTF_8), DEFAULT_BUFFER_SIZE);
        writers[shard] = writer;
        countingStreams[shard] = counting;
        currentBytes[shard] = 0L;
        bytesSinceFlush[shard] = 0L;
        openedAtMillis[shard] = now();
        lastFlushAtMillis[shard] = openedAtMillis[shard];
        if (headerWritten && headerText.length() > 0) {
            writer.write(headerText);
            if (headerBytes > 0) {
                bytesSinceFlush[shard] += headerBytes;
            }
            updateCurrentBytes(shard);
        }
    }

    private IOException closeShard(int shard, IOException failure) {
        Writer writer = writers[shard];
        if (writer == null) {
            return failure;
        }
        try {
            if (!shardHasRules[shard]) {
                writer.write("# No matching sources were found.\n");
            }
            writer.flush();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            }
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }
        writers[shard] = null;
        countingStreams[shard] = null;
        return failure;
    }

    private void updateCurrentBytes(int shard) {
        CountingOutputStream counting = countingStreams[shard];
        if (counting != null) {
            currentBytes[shard] = counting.getCount();
        }
    }

    private long estimateBytes(String rule) {
        if (rule == null || rule.isEmpty()) {
            return 2L;
        }
        return rule.getBytes(StandardCharsets.UTF_8).length + 2L;
    }

    private String fileName(int shardIndex, int rotationIndex) {
        int shardOneBased = shardIndex + 1;
        String base = String.format("%s%04d", filePrefix, shardOneBased);
        if (rotationIndex > 0) {
            base = String.format("%s-%05d", base, rotationIndex);
        }
        String suffix = gzip ? ".btm.gz" : ".btm";
        return base + suffix;
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;

        CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
            count += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        long getCount() {
            return count;
        }
    }
}
