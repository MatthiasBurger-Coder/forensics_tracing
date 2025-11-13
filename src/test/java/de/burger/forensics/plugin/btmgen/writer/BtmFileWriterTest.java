package de.burger.forensics.plugin.btmgen.writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class BtmFileWriterTest {

    private static final String TIMESTAMP_PREFIX = "# Timestamp: ";

    @TempDir
    Path tempDir;

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2024-02-29T10:15:30.123456789Z"), ZoneId.of("UTC"));
    }

    private static Stream<WriterVariant> writerVariants() {
        return Stream.of(
                new WriterVariant(
                        "Explicit clock constructor",
                        BtmFileWriter::new,
                        true),
                new WriterVariant(
                        "System clock constructor",
                        (clock, output) -> new BtmFileWriter(output),
                        false));
    }

    private static Stream<WriterVariant> deterministicVariants() {
        return writerVariants().filter(WriterVariant::deterministicTimestamp);
    }

    private static Stream<RuleVariant> ruleVariants() {
        return Stream.of(
                new RuleVariant("null rules", null, List.of()),
                new RuleVariant("empty rules", List.of(), List.of()),
                new RuleVariant("single rule", List.of("RULE single"), List.of("RULE single", "")),
                new RuleVariant(
                        "multiple rules",
                        List.of("RULE first", "RULE second"),
                        List.of("RULE first", "", "RULE second", "")));
    }

    private static Stream<Arguments> writerAndRuleVariants() {
        return writerVariants().flatMap(writer -> ruleVariants().map(rule -> Arguments.of(writer, rule)));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("writerAndRuleVariants")
    void writeHandlesAllRuleVariants(WriterVariant writerVariant, RuleVariant ruleVariant) throws IOException {
        Path output = tempDir.resolve(safeFileName(writerVariant.name()) + "_" + safeFileName(ruleVariant.name()) + ".btm");
        var writer = writerVariant.factory().create(fixedClock(), output);

        writer.write(ruleVariant.rules());

        List<String> lines = Files.readAllLines(output);
        assertHeaderSection(lines);
        assertEquals(ruleVariant.expectedSuffix(), lines.subList(3, lines.size()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writerVariants")
    void writeCreatesMissingDirectories(WriterVariant writerVariant) {
        Path nestedFile = tempDir.resolve("deep/nested/dir/rules.btm");
        var writer = writerVariant.factory().create(fixedClock(), nestedFile);

        writer.write(List.of("RULE placeholder"));

        assertTrue(Files.exists(nestedFile), "Writer should create parent directories on demand");
    }

    @Test
    void writeCreatesFileIfMissing() throws IOException {
        Clock clock = Clock.systemUTC();
        Path outFile = tempDir.resolve("newfile.btm");

        assertFalse(Files.exists(outFile));

        BtmFileWriter writer = new BtmFileWriter(clock, outFile);
        writer.write(List.of());

        assertTrue(Files.exists(outFile), "Writer must create file automatically");
        assertHeaderSection(Files.readAllLines(outFile));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deterministicVariants")
    void writeUsesProvidedClockForTimestamp(WriterVariant writerVariant) throws IOException {
        Path output = tempDir.resolve("deterministic_" + safeFileName(writerVariant.name()) + ".btm");
        var writer = writerVariant.factory().create(fixedClock(), output);

        writer.write(List.of());

        List<String> lines = Files.readAllLines(output);
        assertEquals("# Timestamp: 2024-02-29T10:15:30.123456789Z", lines.get(1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writerVariants")
    void writeWrapsIoExceptionsInUncheckedIOException(WriterVariant writerVariant) throws IOException {
        Path blockingParent = tempDir.resolve("blockingParent");
        Files.createFile(blockingParent);
        Path output = blockingParent.resolve("cannotCreate.btm");
        var writer = writerVariant.factory().create(fixedClock(), output);

        UncheckedIOException exception = assertThrows(UncheckedIOException.class, () -> writer.write(List.of("RULE test")));
        assertNotNull(exception.getCause(), "The original IOException should be preserved as cause");
    }

    @Test
    void writeTruncatesExistingContentBeforeWriting() throws IOException {
        Path output = tempDir.resolve("truncate/rules.btm");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "OUTDATED CONTENT");

        var writer = new BtmFileWriter(fixedClock(), output);
        writer.write(List.of("RULE overwritten"));

        List<String> lines = Files.readAllLines(output);
        assertHeaderSection(lines);
        assertFalse(lines.stream().anyMatch(line -> line.contains("OUTDATED")),
                "TRUNCATE_EXISTING should remove the previous file content");
        assertEquals("RULE overwritten", lines.get(3));
    }

    @Test
    void writeSeparatesRuleBlocksWithBlankLines() throws IOException {
        Path output = tempDir.resolve("ruleSpacing/rules.btm");
        var writer = new BtmFileWriter(fixedClock(), output);

        writer.write(List.of("RULE alpha", "RULE beta"));

        List<String> lines = Files.readAllLines(output);
        assertHeaderSection(lines);
        assertEquals("RULE alpha", lines.get(3));
        assertEquals("", lines.get(4));
        assertEquals("RULE beta", lines.get(5));
        assertEquals("", lines.get(6));
    }

    @Test
    void constructorRequiresClock() {
        Path output = tempDir.resolve("missing_clock.btm");
        var exception = assertThrows(NullPointerException.class, () -> new BtmFileWriter(null, output));
        assertEquals("clock", exception.getMessage());
    }

    @Test
    void constructorRequiresOutput() {
        var exception = assertThrows(NullPointerException.class, () -> new BtmFileWriter((Path) null));
        assertEquals("output", exception.getMessage());
    }

    @Test
    void writeCreatesDirectoriesAndPersistsHeaderAndRules() throws IOException {
        Clock fixedClock = Clock.fixed(Instant.parse("2024-05-01T12:34:56Z"), ZoneOffset.UTC);
        Path output = tempDir.resolve("nested/dir/sample.btm");
        List<String> rules = List.of("RULE first\nENDRULE", "RULE second\nENDRULE");

        new BtmFileWriter(fixedClock, output).write(rules);

        assertTrue(Files.exists(output), "output file should be created");
        List<String> lines = Files.readAllLines(output);

        assertEquals("# Generated Byteman rules", lines.get(0));
        assertEquals("# Timestamp: 2024-05-01T12:34:56Z", lines.get(1));
        assertEquals("", lines.get(2));
        assertEquals("RULE first", lines.get(3));
        assertEquals("ENDRULE", lines.get(4));
        assertEquals("", lines.get(5));
        assertEquals("RULE second", lines.get(6));
        assertEquals("ENDRULE", lines.get(7));
        assertEquals("", lines.get(8));
    }

    @Test
    void writeHandlesNullRulesWithoutAppendingContent() throws IOException {
        Clock fixedClock = Clock.fixed(Instant.parse("2023-01-01T00:00:00Z"), ZoneOffset.UTC);
        Path output = tempDir.resolve("onlyHeader.btm");

        new BtmFileWriter(fixedClock, output).write(null);

        List<String> lines = Files.readAllLines(output);
        assertEquals(3, lines.size(), "only header and trailing blank line expected");
        assertEquals("# Generated Byteman rules", lines.get(0));
        assertEquals("# Timestamp: 2023-01-01T00:00:00Z", lines.get(1));
        assertEquals("", lines.get(2));
    }

    @Test
    void writeHandlesEmptyRuleListWithoutAppendingContent() throws IOException {
        Clock fixedClock = Clock.fixed(Instant.parse("2023-06-15T10:15:30Z"), ZoneOffset.UTC);
        Path output = tempDir.resolve("emptyList.btm");

        new BtmFileWriter(fixedClock, output).write(List.of());

        List<String> lines = Files.readAllLines(output);
        assertEquals(3, lines.size(), "only header and trailing blank line expected");
        assertEquals("# Generated Byteman rules", lines.get(0));
        assertEquals("# Timestamp: 2023-06-15T10:15:30Z", lines.get(1));
        assertEquals("", lines.get(2));
    }

    @Test
    void constructorValidatesArguments() {
        Path dummy = tempDir.resolve("dummy.btm");

        assertThrows(NullPointerException.class, () -> new BtmFileWriter(null, dummy));
        assertThrows(NullPointerException.class, () -> new BtmFileWriter(Clock.systemUTC(), null));
    }

    @Test
    void writeWrapsIoExceptionsInUncheckedIOExceptionExplicitScenario() throws IOException {
        Path file = tempDir.resolve("blocked");
        Files.createFile(file);
        Path output = file.resolve("illegal.btm");
        BtmFileWriter writer = new BtmFileWriter(Clock.systemUTC(), output);

        assertThrows(UncheckedIOException.class, () -> writer.write(List.of("RULE X")));
    }

    private static void assertHeaderSection(List<String> lines) {
        assertTrue(lines.size() >= 3, "Header must contain at least three lines");
        assertEquals("# Generated Byteman rules", lines.get(0));
        assertValidTimestampLine(lines.get(1));
        assertEquals("", lines.get(2));
    }

    private static void assertValidTimestampLine(String line) {
        assertTrue(line.startsWith(TIMESTAMP_PREFIX), "Timestamp line must start with the expected prefix");
        String timestamp = line.substring(TIMESTAMP_PREFIX.length());
        assertDoesNotThrow(() -> ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    private static String safeFileName(String raw) {
        return raw.replaceAll("[^A-Za-z0-9]+", "_");
    }

    private record WriterVariant(String name, WriterFactory factory, boolean deterministicTimestamp) {
        @Override
        public @NotNull String toString() {
            return name;
        }
    }

    private record RuleVariant(String name, List<String> rules, List<String> expectedSuffix) {
        @Override
        public @NotNull String toString() {
            return name;
        }
    }

    @FunctionalInterface
    private interface WriterFactory {
        BtmFileWriter create(Clock clock, Path output);
    }
}
