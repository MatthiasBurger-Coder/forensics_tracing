package de.burger.forensics.domain.model.entry;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntryRecordTest {

    @Test
    void createsEntriesFromConvenienceAndCanonicalConstructors() {
        Instant timestamp = Instant.parse("2025-01-01T00:00:00Z");
        RuntimeException cause = new RuntimeException("boom");
        ScanEvent event = new ScanEvent(
                new SourceLocation("com.example.Foo", "work", 12),
                "work(String)",
                RuleTemplate.IF_TRUE,
                "flag",
                "java",
                "boolean"
        );

        ErrorEntry errorWithDefaults = new ErrorEntry("error", "scanner");
        ErrorEntry errorWithCause = new ErrorEntry("error", "scanner", cause);
        ErrorEntry canonicalError = new ErrorEntry(timestamp, "error", "scanner", cause);
        EventEntry eventEntry = new EventEntry(event, "scanner", 3L);
        FileEntry fileEntry = new FileEntry(Path.of("src/main/java/com/example/Foo.java"), 64L);
        FileEntry canonicalFileEntry = new FileEntry(Path.of("src/main/java/com/example/Bar.java"), 128L, timestamp);
        MethodEntry methodEntry = new MethodEntry("com.example.Foo#work(String)", "Foo", "work", List.of("String"), "boolean");
        WarningEntry warningEntry = new WarningEntry("warning", "scanner");
        WarningEntry canonicalWarningEntry = new WarningEntry(timestamp, "warning", "scanner");

        assertThat(errorWithDefaults.timestamp()).isNotNull();
        assertThat(errorWithDefaults.cause()).isNull();
        assertThat(errorWithCause.cause()).isSameAs(cause);
        assertThat(canonicalError.timestamp()).isEqualTo(timestamp);

        assertThat(eventEntry.event()).isEqualTo(event);
        assertThat(eventEntry.source()).isEqualTo("scanner");
        assertThat(eventEntry.index()).isEqualTo(3L);

        assertThat(fileEntry.path()).isEqualTo(Path.of("src/main/java/com/example/Foo.java"));
        assertThat(fileEntry.fileSize()).isEqualTo(64L);
        assertThat(fileEntry.discoveredAt()).isNotNull();
        assertThat(canonicalFileEntry.discoveredAt()).isEqualTo(timestamp);

        assertThat(methodEntry.className()).isEqualTo("Foo");
        assertThat(methodEntry.parameterTypes()).containsExactly("String");

        assertThat(warningEntry.timestamp()).isNotNull();
        assertThat(canonicalWarningEntry.timestamp()).isEqualTo(timestamp);
    }
}
