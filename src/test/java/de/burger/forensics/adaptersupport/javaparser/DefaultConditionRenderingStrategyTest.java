package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import de.burger.forensics.adaptersupport.javaparser.scanner.JavaParserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConditionRenderingStrategyTest {

    private InstanceFieldNormalizer normalizer;
    private DefaultConditionRenderingStrategy strategy;
    private MethodEventExtractor helperExtractor;

    @BeforeEach
    void setUp() {
        normalizer = new InstanceFieldNormalizer();
        strategy = new DefaultConditionRenderingStrategy(normalizer);
        helperExtractor = new MethodEventExtractor(strategy);
    }

    @ParameterizedTest
    @MethodSource("renderConditionCases")
    void rendersExpectedConditions(String source, String expected) {
        MethodDeclaration declaration = parseMethod(source);
        MethodScanContext context = helperExtractor.methodContext(declaration);
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();

        String rendered = strategy.renderCondition(condition, context);

        assertThat(rendered).isEqualTo(expected);
    }

    @Test
    void qualifiesWildcardImportedTypeReferences(@TempDir Path root) throws IOException {
        write(root, "org/example/DeploymentType.java", """
            package org.example;

            public enum DeploymentType {
                EAR
            }
            """);
        write(root, "org/example/DeploymentTypeMarker.java", """
            package org.example;

            public final class DeploymentTypeMarker {
                public static boolean isType(DeploymentType type, Object value) {
                    return true;
                }
            }
            """);
        write(root, "sample/Sample.java", """
            package sample;

            import org.example.*;

            class Sample {
                boolean check(Object deploymentUnit) {
                    if (DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
                        return true;
                    }
                    return false;
                }
            }
            """);

        String rendered = renderCondition(root, "sample/Sample.java");

        assertThat(rendered)
                .isEqualTo("org.example.DeploymentTypeMarker.isType(org.example.DeploymentType.EAR, $1)");
    }

    @Test
    void qualifiesStaticWildcardImportedMembers(@TempDir Path root) throws IOException {
        write(root, "org/example/Flags.java", """
            package org.example;

            public final class Flags {
                public static final String ENABLED = "enabled";
            }
            """);
        write(root, "sample/Sample.java", """
            package sample;

            import static org.example.Flags.*;

            class Sample {
                boolean check(String value) {
                    if (ENABLED.equals(value)) {
                        return true;
                    }
                    return false;
                }
            }
            """);

        String rendered = renderCondition(root, "sample/Sample.java");

        assertThat(rendered).isEqualTo("org.example.Flags.ENABLED.equals($1)");
    }

    @Test
    void qualifiesSamePackageTypeReferences(@TempDir Path root) throws IOException {
        write(root, "sample/LocalType.java", """
            package sample;

            public final class LocalType {
                public static boolean enabled() {
                    return true;
                }
            }
            """);
        write(root, "sample/Sample.java", """
            package sample;

            class Sample {
                boolean check() {
                    if (LocalType.enabled()) {
                        return true;
                    }
                    return false;
                }
            }
            """);

        String rendered = renderCondition(root, "sample/Sample.java");

        assertThat(rendered).isEqualTo("sample.LocalType.enabled()");
    }

    @Test
    void qualifiesNestedTypeReferences(@TempDir Path root) throws IOException {
        write(root, "sample/Outer.java", """
            package sample;

            class Outer {
                static final class NestedType {
                    static boolean enabled() {
                        return true;
                    }
                }

                boolean check() {
                    if (NestedType.enabled()) {
                        return true;
                    }
                    return false;
                }
            }
            """);

        String rendered = renderCondition(root, "sample/Outer.java");

        assertThat(rendered).isEqualTo("sample.Outer.NestedType.enabled()");
    }

    @Test
    void leavesUnresolvedTypeReferencesUnchanged(@TempDir Path root) throws IOException {
        write(root, "sample/Sample.java", """
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

        String rendered = renderCondition(root, "sample/Sample.java");

        assertThat(rendered).isEqualTo("UnknownType.enabled()");
    }

    @Test
    void leavesAmbiguousWildcardTypeReferencesUnchanged(@TempDir Path root) throws IOException {
        write(root, "one/Status.java", """
            package one;

            public final class Status {
                public static boolean enabled() {
                    return true;
                }
            }
            """);
        write(root, "two/Status.java", """
            package two;

            public final class Status {
                public static boolean enabled() {
                    return true;
                }
            }
            """);
        write(root, "sample/Sample.java", """
            package sample;

            import one.*;
            import two.*;

            class Sample {
                boolean check() {
                    if (Status.enabled()) {
                        return true;
                    }
                    return false;
                }
            }
            """);

        String rendered = renderCondition(root, "sample/Sample.java");

        assertThat(rendered).isEqualTo("Status.enabled()");
    }

    private static Stream<Arguments> renderConditionCases() {
        return Stream.of(
            Arguments.of("""
                class Sample {
                    boolean check(int value) {
                        if (value > 0) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "$1 > 0"),
            Arguments.of("""
                class Sample {
                    boolean check(int value) {
                        int limit = value + 1;
                        if (limit > value) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "$limit > $1"),
            Arguments.of("""
                class Sample {
                    private int threshold = 10;
                    boolean check(int value) {
                        if (value > threshold) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "$1 > $this.threshold"),
            Arguments.of("""
                class Sample {
                    private static Sample INSTANCE;
                    Sample getInstance() {
                        if (INSTANCE == null) {
                            INSTANCE = new Sample();
                        }
                        return INSTANCE;
                    }
                }
                """, "$CLASS.INSTANCE == null"),
            Arguments.of("""
                import org.example.DeploymentType;
                import org.example.DeploymentTypeMarker;
                class Sample {
                    boolean check(Object deploymentUnit) {
                        if (DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "org.example.DeploymentTypeMarker.isType(org.example.DeploymentType.EAR, $1)"),
            Arguments.of("""
                import static org.example.ModelDescriptionConstants.OUTCOME;
                class Sample {
                    boolean check(Object result) {
                        if (OUTCOME.equals(result)) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "org.example.ModelDescriptionConstants.OUTCOME.equals($1)"),
            Arguments.of("""
                import static org.example.DeploymentTypeMarker.isType;
                class Sample {
                    boolean check(Object deploymentUnit) {
                        if (isType(deploymentUnit)) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "org.example.DeploymentTypeMarker.isType($1)"),
            Arguments.of("""
                import org.example.DeploymentType;
                class Sample {
                    boolean check(Object value) {
                        Object DeploymentType = value;
                        if (DeploymentType == value) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "$DeploymentType == $1")
        );
    }

    private MethodDeclaration parseMethod(String source) {
        return StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow();
    }

    private String renderCondition(Path root, String sourceRelativePath) throws IOException {
        CompilationUnit unit = new JavaParserFactory().create(root)
                .parse(root.resolve(sourceRelativePath))
                .getResult()
                .orElseThrow();
        MethodDeclaration declaration = unit.findFirst(MethodDeclaration.class, method ->
                        "check".equals(method.getNameAsString()))
                .orElseThrow();
        MethodScanContext context = helperExtractor.methodContext(declaration);
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();
        return strategy.renderCondition(condition, context);
    }

    private static void write(Path root, String relativePath, String source) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
