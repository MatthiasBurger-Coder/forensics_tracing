package de.burger.forensics.application;

import de.burger.forensics.domain.model.MethodScanContext;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.entry.ErrorEntry;
import de.burger.forensics.domain.model.entry.EventEntry;
import de.burger.forensics.domain.model.entry.FileEntry;
import de.burger.forensics.domain.model.entry.WarningEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisContextTest {

    @Test
    void storesCollectedAnalysisStateAndExposesImmutableViews() {
        AnalysisContext context = new AnalysisContext();
        ScanEvent event = new ScanEvent(
                new SourceLocation("com.example.Foo", "work", 17),
                "work()",
                RuleTemplate.IF_TRUE,
                "flag",
                "java",
                "boolean"
        );
        FileEntry fileEntry = new FileEntry(Path.of("src/main/java/com/example/Foo.java"), 42L);
        WarningEntry warning = new WarningEntry("warning", "scanner");
        ErrorEntry error = new ErrorEntry("error", "scanner");

        context.addSourceRoot(Path.of("src/main/java"));
        context.addFileEntry(fileEntry);
        context.addMethodContext("Foo#work()", "Foo", "work", List.of("String"), "boolean", List.of(event));
        context.addEvent(event);
        context.addEventEntry(new EventEntry(event, "scanner", 1L));
        context.addEventWithSource(event, "adapter");
        context.addWarning(warning);
        context.addError(error);
        context.putSetting("safeMode", true);

        assertThat(context.getSourceRoots()).containsExactly(Path.of("src/main/java"));
        assertThat(context.getFileEntries()).containsExactly(fileEntry);
        assertThat(context.getMethodEntries())
                .singleElement()
                .satisfies(methodEntry -> {
                    assertThat(methodEntry.fullyQualifiedMethodName()).isEqualTo("Foo#work()");
                    assertThat(methodEntry.className()).isEqualTo("Foo");
                    assertThat(methodEntry.methodName()).isEqualTo("work");
                    assertThat(methodEntry.parameterTypes()).containsExactly("String");
                    assertThat(methodEntry.returnType()).isEqualTo("boolean");
                });
        assertThat(context.getMethodContexts())
                .containsKey("Foo#work()")
                .extractingByKey("Foo#work()")
                .satisfies(methodContext -> {
                    assertThat(methodContext.getMethodId()).isEqualTo("Foo#work()");
                    assertThat(methodContext.getEvents()).containsExactly(event);
                });
        assertThat(context.getEvents()).containsExactly(event, event);
        assertThat(context.getEventEntries()).hasSize(2);
        assertThat(context.getWarnings()).containsExactly(warning);
        assertThat(context.getErrors()).containsExactly(error);
        assertThat(context.getSettings()).containsEntry("safeMode", true);

        List<Path> sourceRoots = context.getSourceRoots();
        var methodContexts = context.getMethodContexts();
        var settings = context.getSettings();
        Path otherRoot = Path.of("other");
        MethodScanContext otherContext = new MethodScanContext();

        assertThatThrownBy(() -> sourceRoots.add(otherRoot))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> methodContexts.put("other", otherContext))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> settings.put("other", false))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void tracksLifecycleAndDirectMethodContextRegistration() {
        AnalysisContext context = AnalysisContext.builder().build();
        MethodScanContext methodContext = MethodScanContext.builder()
                .methodId("Foo#work()")
                .className("Foo")
                .methodName("work")
                .build();

        assertThat(context.isFinished()).isFalse();

        context.putMethodContext("Foo#work()", methodContext);
        context.markFinished();

        assertThat(context.getMethodContexts()).containsEntry("Foo#work()", methodContext);
        assertThat(context.getEndTime()).isNotNull();
        assertThat(context.isFinished()).isTrue();
    }
}
