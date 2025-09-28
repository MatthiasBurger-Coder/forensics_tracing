// DEST: src/main/java/de/burger/forensics/plugin/BtmGenExtension.java
package de.burger.forensics.plugin;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;

public abstract class BtmGenExtension {

    private final ListProperty<String> srcDirs;
    private final Property<String> pkgPrefix;
    private final ListProperty<String> pkgPrefixes;
    private final Property<String> helperFqn;
    private final Property<Boolean> entryExit;
    private final ListProperty<String> trackedVars;
    private final Property<Boolean> includeJava;
    private final Property<Boolean> includeTimestamp;
    private final ListProperty<String> include;
    private final ListProperty<String> exclude;
    private final Property<Integer> parallelism;
    private final Property<Integer> shardsProperty;
    private final Property<Boolean> gzipOutputProperty;
    private final Property<String> filePrefixProperty;
    private final Property<Long> rotateMaxBytesPerFileProperty;
    private final Property<Long> rotateIntervalSecondsProperty;
    private final Property<Integer> flushThresholdBytesProperty;
    private final Property<Long> flushIntervalMillisProperty;
    private final Property<Boolean> writerThreadSafeProperty;
    private final Property<Integer> minBranchesPerMethod;
    private final Property<Boolean> safeMode;
    private final Property<Boolean> forceHelperForWhitelist;
    private final Property<Long> maxFileBytes;
    private final Property<Boolean> useAstScanner;
    private final DirectoryProperty outputDir;
    private final Property<Integer> maxStringLength;
    private final Property<String> logLevel;
    private final Property<Boolean> logToFile;
    private final Property<String> logFilePath;

    @Inject
    protected BtmGenExtension(@NotNull ObjectFactory objects, @NotNull ProjectLayout layout) {
        this.srcDirs = objects.listProperty(String.class);
        this.pkgPrefix = objects.property(String.class);
        this.pkgPrefixes = objects.listProperty(String.class);
        this.helperFqn = objects.property(String.class);
        this.entryExit = objects.property(Boolean.class);
        this.trackedVars = objects.listProperty(String.class);
        this.includeJava = objects.property(Boolean.class);
        this.includeTimestamp = objects.property(Boolean.class);
        this.include = objects.listProperty(String.class);
        this.exclude = objects.listProperty(String.class);
        this.parallelism = objects.property(Integer.class);
        this.shardsProperty = objects.property(Integer.class);
        this.gzipOutputProperty = objects.property(Boolean.class);
        this.filePrefixProperty = objects.property(String.class);
        this.rotateMaxBytesPerFileProperty = objects.property(Long.class);
        this.rotateIntervalSecondsProperty = objects.property(Long.class);
        this.flushThresholdBytesProperty = objects.property(Integer.class);
        this.flushIntervalMillisProperty = objects.property(Long.class);
        this.writerThreadSafeProperty = objects.property(Boolean.class);
        this.minBranchesPerMethod = objects.property(Integer.class);
        this.safeMode = objects.property(Boolean.class);
        this.forceHelperForWhitelist = objects.property(Boolean.class);
        this.maxFileBytes = objects.property(Long.class);
        this.useAstScanner = objects.property(Boolean.class);
        this.outputDir = objects.directoryProperty();
        this.maxStringLength = objects.property(Integer.class);
        this.logLevel = objects.property(String.class);
        this.logToFile = objects.property(Boolean.class);
        this.logFilePath = objects.property(String.class);

        int processors = Math.max(Runtime.getRuntime().availableProcessors(), 1);
        this.srcDirs.convention(List.of("src/main/java", "src/main/kotlin"));
        this.pkgPrefix.convention("");
        this.pkgPrefixes.convention(Collections.emptyList());
        this.helperFqn.convention("de.burger.forensics.ForensicsHelper");
        this.entryExit.convention(true);
        this.trackedVars.convention(Collections.emptyList());
        this.includeJava.convention(true);
        this.includeTimestamp.convention(false);
        this.include.convention(Collections.emptyList());
        this.exclude.convention(Collections.emptyList());
        this.parallelism.convention(processors);
        this.shardsProperty.convention(processors);
        this.gzipOutputProperty.convention(false);
        this.filePrefixProperty.convention("tracing-");
        this.rotateMaxBytesPerFileProperty.convention(4L * 1024 * 1024);
        this.rotateIntervalSecondsProperty.convention(0L);
        this.flushThresholdBytesProperty.convention(64 * 1024);
        this.flushIntervalMillisProperty.convention(2000L);
        this.writerThreadSafeProperty.convention(false);
        this.minBranchesPerMethod.convention(0);
        this.outputDir.convention(layout.getBuildDirectory().dir("forensics"));
        this.maxStringLength.convention(0);
        this.safeMode.convention(false);
        this.forceHelperForWhitelist.convention(false);
        this.maxFileBytes.convention(2_000_000L);
        this.useAstScanner.convention(true);
        this.logLevel.convention("ERROR");
        this.logToFile.convention(true);
        this.logFilePath.convention("logs/forensics-btmgen.log");
    }

    @NotNull
    public ListProperty<String> getSrcDirs() {
        return srcDirs;
    }

    @NotNull
    public Property<String> getPkgPrefix() {
        return pkgPrefix;
    }

    @NotNull
    public ListProperty<String> getPkgPrefixes() {
        return pkgPrefixes;
    }

    @NotNull
    public Property<String> getHelperFqn() {
        return helperFqn;
    }

    @NotNull
    public Property<Boolean> getEntryExit() {
        return entryExit;
    }

    @NotNull
    public ListProperty<String> getTrackedVars() {
        return trackedVars;
    }

    @NotNull
    public Property<Boolean> getIncludeJava() {
        return includeJava;
    }

    @NotNull
    public Property<Boolean> getIncludeTimestamp() {
        return includeTimestamp;
    }

    @NotNull
    public ListProperty<String> getInclude() {
        return include;
    }

    @NotNull
    public ListProperty<String> getExclude() {
        return exclude;
    }

    @NotNull
    public Property<Integer> getParallelism() {
        return parallelism;
    }

    @NotNull
    public Property<Integer> getShardsProperty() {
        return shardsProperty;
    }

    @NotNull
    public Property<Boolean> getGzipOutputProperty() {
        return gzipOutputProperty;
    }

    @NotNull
    public Property<String> getFilePrefixProperty() {
        return filePrefixProperty;
    }

    @NotNull
    public Property<Long> getRotateMaxBytesPerFileProperty() {
        return rotateMaxBytesPerFileProperty;
    }

    @NotNull
    public Property<Long> getRotateIntervalSecondsProperty() {
        return rotateIntervalSecondsProperty;
    }

    @NotNull
    public Property<Integer> getFlushThresholdBytesProperty() {
        return flushThresholdBytesProperty;
    }

    @NotNull
    public Property<Long> getFlushIntervalMillisProperty() {
        return flushIntervalMillisProperty;
    }

    @NotNull
    public Property<Boolean> getWriterThreadSafeProperty() {
        return writerThreadSafeProperty;
    }

    @NotNull
    public Property<Integer> getMinBranchesPerMethod() {
        return minBranchesPerMethod;
    }

    @NotNull
    public Property<Boolean> getSafeMode() {
        return safeMode;
    }

    @NotNull
    public Property<Boolean> getForceHelperForWhitelist() {
        return forceHelperForWhitelist;
    }

    @NotNull
    public Property<Long> getMaxFileBytes() {
        return maxFileBytes;
    }

    @NotNull
    public Property<Boolean> getUseAstScanner() {
        return useAstScanner;
    }

    @NotNull
    public DirectoryProperty getOutputDir() {
        return outputDir;
    }

    @NotNull
    public Property<Integer> getMaxStringLength() {
        return maxStringLength;
    }

    @NotNull
    public Property<String> getLogLevel() {
        return logLevel;
    }

    @NotNull
    public Property<Boolean> getLogToFile() {
        return logToFile;
    }

    @NotNull
    public Property<String> getLogFilePath() {
        return logFilePath;
    }

    public int getShards() {
        Integer value = shardsProperty.getOrNull();
        if (value == null) {
            return Math.max(Runtime.getRuntime().availableProcessors(), 1);
        }
        return value;
    }

    public void setShards(int value) {
        shardsProperty.set(value);
    }

    public boolean getGzipOutput() {
        Boolean value = gzipOutputProperty.getOrNull();
        return value != null ? value : false;
    }

    public void setGzipOutput(boolean value) {
        gzipOutputProperty.set(value);
    }

    @NotNull
    public String getFilePrefix() {
        String value = filePrefixProperty.getOrNull();
        return value != null ? value : "tracing-";
    }

    public void setFilePrefix(@NotNull String value) {
        filePrefixProperty.set(value);
    }

    public long getRotateMaxBytesPerFile() {
        Long value = rotateMaxBytesPerFileProperty.getOrNull();
        return value != null ? value : 4L * 1024 * 1024;
    }

    public void setRotateMaxBytesPerFile(long value) {
        rotateMaxBytesPerFileProperty.set(value);
    }

    public long getRotateIntervalSeconds() {
        Long value = rotateIntervalSecondsProperty.getOrNull();
        return value != null ? value : 0L;
    }

    public void setRotateIntervalSeconds(long value) {
        rotateIntervalSecondsProperty.set(value);
    }

    public int getFlushThresholdBytes() {
        Integer value = flushThresholdBytesProperty.getOrNull();
        return value != null ? value : 64 * 1024;
    }

    public void setFlushThresholdBytes(int value) {
        flushThresholdBytesProperty.set(value);
    }

    public long getFlushIntervalMillis() {
        Long value = flushIntervalMillisProperty.getOrNull();
        return value != null ? value : 2000L;
    }

    public void setFlushIntervalMillis(long value) {
        flushIntervalMillisProperty.set(value);
    }

    public boolean getWriterThreadSafe() {
        Boolean value = writerThreadSafeProperty.getOrNull();
        return value != null ? value : false;
    }

    public void setWriterThreadSafe(boolean value) {
        writerThreadSafeProperty.set(value);
    }
}
