package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import de.burger.forensics.adaptersupport.javaparser.scanner.JavaParserFactory;
import de.burger.forensics.domain.model.ConditionResolutionStatus;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MethodEventExtractorTest {

    private MethodEventExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MethodEventExtractor(new DefaultConditionRenderingStrategy(new InstanceFieldNormalizer()));
    }

    @Test
    void collectsEventsFromTypicalMethod() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                int compute(int value) {
                    if (value > 10) {
                        throw new IllegalArgumentException();
                    }
                    switch (value) {
                        case 1 -> value = 2;
                        default -> value = 3;
                    }
                    return value;
                }
            }
            """);

        List<ScanEvent> events = extractor.collectMethodEvents(declaration, "example");

        assertThat(events).extracting(ScanEvent::kind)
            .contains(RuleTemplate.IF_TRUE, RuleTemplate.IF_FALSE, RuleTemplate.SWITCH,
                RuleTemplate.SWITCH_CASE, RuleTemplate.RETURN, RuleTemplate.THROW);
    }

    @Test
    void resolvesEnclosingTypeIncludingNesting() {
        CompilationUnit unit = StaticJavaParser.parse("""
            class Outer {
                class Inner {
                    void run() {}
                }
            }
            """);
        MethodDeclaration method = unit.findFirst(MethodDeclaration.class).orElseThrow();

        assertThat(extractor.resolveEnclosingType(method)).isEqualTo("Outer$Inner");
    }

    @Test
    void exposesParameterIndexesAndLocalVariables() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                void run(int value, String name) {
                    String suffix = name + value;
                }
            }
            """);

        Map<String, Integer> indexes = extractor.parameterIndexes(declaration);
        Set<String> locals = extractor.localVariableNames(declaration);

        assertThat(indexes).containsEntry("value", 1).containsEntry("name", 2);
        assertThat(locals).contains("suffix");
    }

    @Test
    void includesCatchParametersAsLocalVariables() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                void run() {
                    try {
                        risky();
                    } catch (RuntimeException ex) {
                        throw ex;
                    }
                }
            }
            """);

        List<ScanEvent> events = extractor.collectMethodEvents(declaration, "example");

        String throwCondition = events.stream()
                .filter(event -> event.kind() == RuleTemplate.THROW)
                .map(ScanEvent::conditionText)
                .findFirst()
                .orElseThrow();

        assertThat(throwCondition).isEqualTo("$ex");
    }

    @Test
    void scannerShouldCaptureMethodSignatureFromAstParameters() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                ModelNode execute(ModelNode operation, ModelNode model) {
                    return model;
                }
            }
            """);

        List<ScanEvent> events = extractor.collectMethodEvents(declaration, "example");

        assertThat(events)
                .extracting(ScanEvent::signature)
                .containsOnly("execute(ModelNode, ModelNode)");
    }

    @Test
    void methodContextExposesSourceMetadataAndWildcardImports(@TempDir Path root) throws IOException {
        Path source = write(root, "sample/Outer.java", """
            package sample;

            import org.example.*;
            import static org.example.Flags.*;

            class Outer {
                static class Inner {
                    boolean check(String value) {
                        return value != null;
                    }
                }
            }
            """);
        MethodDeclaration declaration = parseMethod(root, source);

        MethodScanContext context = extractor.methodContext(declaration);

        assertThat(context.packageName()).isEqualTo("sample");
        assertThat(context.sourceFilePath()).isEqualTo(source.toString());
        assertThat(context.sourceTypeName()).isEqualTo("Outer.Inner");
        assertThat(context.fullyQualifiedSourceTypeName()).isEqualTo("sample.Outer.Inner");
        assertThat(context.simpleClassName()).isEqualTo("Inner");
        assertThat(context.methodName()).isEqualTo("check");
        assertThat(context.methodSignature()).isEqualTo("check(String)");
        assertThat(context.wildcardTypeImports()).containsExactly("org.example");
        assertThat(context.wildcardStaticImports()).containsExactly("org.example.Flags");
    }

    @Test
    void methodContextSeparatesImportTableGroups(@TempDir Path root) throws IOException {
        Path source = write(root, "sample/Sample.java", """
            package sample;

            import org.example.TypeName;
            import org.example.*;
            import static org.example.TypeName.MEMBER;
            import static org.example.TypeName.*;

            class Sample {
                boolean check() {
                    return MEMBER != null;
                }
            }
            """);
        MethodDeclaration declaration = parseMethod(root, source);

        ImportTable importTable = extractor.methodContext(declaration).importTable();

        assertThat(importTable.explicitTypeImports()).containsEntry("TypeName", "org.example.TypeName");
        assertThat(importTable.wildcardTypeImports()).containsExactly("org.example");
        assertThat(importTable.explicitStaticMemberImports()).containsEntry("MEMBER", "org.example.TypeName.MEMBER");
        assertThat(importTable.wildcardStaticImports()).containsExactly("org.example.TypeName");
    }

    @Test
    void collectMethodEventsKeepsBtmNestedClassName(@TempDir Path root) throws IOException {
        Path source = write(root, "sample/Outer.java", """
            package sample;

            class Outer {
                static class Inner {
                    boolean check(String value) {
                        if (value != null) {
                            return true;
                        }
                        return false;
                    }
                }
            }
            """);
        MethodDeclaration declaration = parseMethod(root, source);

        List<ScanEvent> events = extractor.collectMethodEvents(declaration, "sample");

        assertThat(events)
                .extracting(event -> event.location().fqcn())
                .containsOnly("sample.Outer$Inner");
    }

    @Test
    void collectMethodEventsAttachesConditionDiagnosticsWithSourceContext(@TempDir Path root) throws IOException {
        Path source = write(root, "sample/Sample.java", """
            package sample;

            class Sample {
                boolean check() {
                    if (UnknownType.enabled()) {
                        return true;
                    }
                    return false;
                }
            }
            """);
        MethodDeclaration declaration = parseMethod(root, source);

        ScanEvent event = extractor.collectMethodEvents(declaration, "sample").stream()
                .filter(candidate -> candidate.kind() == RuleTemplate.IF_TRUE)
                .findFirst()
                .orElseThrow();

        assertThat(event.conditionDiagnostics()).singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.symbol()).isEqualTo("UnknownType");
                    assertThat(diagnostic.resolutionStatus()).isEqualTo(ConditionResolutionStatus.UNRESOLVED);
                    assertThat(diagnostic.location()).isEqualTo(event.location());
                    assertThat(diagnostic.sourceContext().packageName()).isEqualTo("sample");
                    assertThat(diagnostic.sourceContext().sourceFilePath()).isEqualTo(source.toString());
                    assertThat(diagnostic.sourceContext().fullyQualifiedClassName()).isEqualTo("sample.Sample");
                    assertThat(diagnostic.sourceContext().methodName()).isEqualTo("check");
                    assertThat(diagnostic.sourceContext().methodSignature()).isEqualTo("check()");
                });
    }

    private MethodDeclaration parseMethod(String source) {
        return StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow();
    }

    private MethodDeclaration parseMethod(Path root, Path source) throws IOException {
        return new JavaParserFactory().create(root)
                .parse(source)
                .getResult()
                .orElseThrow()
                .findFirst(MethodDeclaration.class)
                .orElseThrow();
    }

    private static Path write(Path root, String relativePath, String source) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }
}
