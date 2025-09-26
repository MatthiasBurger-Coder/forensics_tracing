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
import java.util.zip.GZIPOutputStream;

/**
 * Buffered per-shard writer that streams rules to disk to keep heap usage low.
 */
public final class ShardedWriter implements Closeable, Flushable {
    private static final int DEFAULT_BUFFER_SIZE = 1 << 16;
    private static final int DEFAULT_FLUSH_THRESHOLD = 64 * 1024;

    private final Writer[] writers;
    private final boolean[] hasRules;
    private final int[] bytesSinceFlush;
    private final int shards;
    private final int flushThresholdBytes;
    private boolean closed;
    private boolean headerWritten;

    /**
     * Creates a writer that shards output files into {@code shards} parts using the given prefix.
     */
    public ShardedWriter(File outDir, int shards, boolean gzip, String filePrefix) throws IOException {
        this(outDir, shards, gzip, filePrefix, DEFAULT_FLUSH_THRESHOLD);
    }

    ShardedWriter(File outDir, int shards, boolean gzip, String filePrefix, int flushThresholdBytes) throws IOException {
        int shardCount = Math.max(1, shards);
        this.flushThresholdBytes = flushThresholdBytes <= 0 ? DEFAULT_FLUSH_THRESHOLD : flushThresholdBytes;
        this.shards = shardCount;
        this.writers = new Writer[shardCount];
        this.hasRules = new boolean[shardCount];
        this.bytesSinceFlush = new int[shardCount];
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Cannot create output dir: " + outDir);
        }
        for (int i = 0; i < shardCount; i++) {
            String suffix = gzip ? ".btm.gz" : ".btm";
            String name = String.format("%s%04d%s", filePrefix, i + 1, suffix);
            File file = new File(outDir, name);
            OutputStream os = new FileOutputStream(file);
            if (gzip) {
                os = new GZIPOutputStream(os, DEFAULT_BUFFER_SIZE);
            }
            writers[i] = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), DEFAULT_BUFFER_SIZE);
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
        String text = header == null ? "" : header;
        for (Writer writer : writers) {
            writer.write(text);
        }
        headerWritten = true;
    }

    /**
     * Appends one rule to the target shard. Rules are separated by blank lines.
     */
    public synchronized void append(int shard, String rule) throws IOException {
        ensureOpen();
        if (!headerWritten) {
            throw new IllegalStateException("writeHeader must be called before append");
        }
        int target = normalizeShard(shard);
        Writer writer = writers[target];
        writer.write('\n');
        writer.write(rule);
        writer.write('\n');
        hasRules[target] = true;
        int estimated = rule.length() + 2;
        bytesSinceFlush[target] += estimated;
        if (bytesSinceFlush[target] >= flushThresholdBytes) {
            writer.flush();
            bytesSinceFlush[target] = 0;
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        ensureOpen();
        for (int i = 0; i < writers.length; i++) {
            writers[i].flush();
            bytesSinceFlush[i] = 0;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        IOException failure = null;
        for (int i = 0; i < writers.length; i++) {
            Writer writer = writers[i];
            try {
                if (!hasRules[i]) {
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
}
