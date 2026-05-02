package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import de.burger.forensics.domain.model.cache.DependencyKind;
import de.burger.forensics.domain.model.cache.ScanDependency;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaParserDependencyExtractorTest {

    private final JavaParserDependencyExtractor extractor = new JavaParserDependencyExtractor();

    @Test
    void extractsSyntacticDependencyKindsWithoutAstState() {
        CompilationUnit compilationUnit = new JavaParser().parse("""
            package example;
            import java.util.List;

            @Marker
            class Sample extends Base implements Runnable {
                private Dependency dependency;
                private int value;

                @Trace
                Result run(Input input) throws Problem {
                    this.value = input.value;
                    dependency.call();
                    List<String> names = new java.util.ArrayList<>();
                    return new Result(new Helper());
                }
            }
            """).getResult().orElseThrow();

        List<ScanDependency> dependencies = extractor.extract(compilationUnit, "example/Sample.java");

        assertThat(dependencies)
                .extracting(dependency -> dependency.kind() + ":" + dependency.target())
                .contains(
                        DependencyKind.IMPORT + ":java.util.List",
                        DependencyKind.EXTENDS + ":Base",
                        DependencyKind.IMPLEMENTS + ":Runnable",
                        DependencyKind.ANNOTATION + ":Marker",
                        DependencyKind.ANNOTATION + ":Trace",
                        DependencyKind.THROWN_TYPE + ":Problem",
                        DependencyKind.RETURN_TYPE + ":Result",
                        DependencyKind.PARAMETER_TYPE + ":Input",
                        DependencyKind.FIELD_ACCESS + ":this.value",
                        DependencyKind.FIELD_ACCESS + ":input.value",
                        DependencyKind.METHOD_CALL + ":dependency.call",
                        DependencyKind.CONSTRUCTOR_CALL + ":java.util.ArrayList",
                        DependencyKind.CONSTRUCTOR_CALL + ":Result",
                        DependencyKind.CONSTRUCTOR_CALL + ":Helper");
    }

    @Test
    void recordsOwnerAndCoordinatesForMethodDependencies() {
        CompilationUnit compilationUnit = new JavaParser().parse("""
            package example;
            class Sample {
                void run() {
                    dependency.call();
                }
            }
            """).getResult().orElseThrow();

        List<ScanDependency> dependencies = extractor.extract(compilationUnit, "example/Sample.java");

        assertThat(dependencies)
                .filteredOn(dependency -> dependency.kind() == DependencyKind.METHOD_CALL)
                .singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.sourceRelativePath()).isEqualTo("example/Sample.java");
                    assertThat(dependency.ownerType()).isEqualTo("example.Sample");
                    assertThat(dependency.ownerMember()).isEqualTo("run");
                    assertThat(dependency.target()).isEqualTo("dependency.call");
                    assertThat(dependency.line()).isPositive();
                    assertThat(dependency.column()).isPositive();
                });
    }

    @Test
    void rejectsBlankRelativePath() {
        CompilationUnit compilationUnit = new JavaParser().parse("class Sample {}")
                .getResult()
                .orElseThrow();

        assertThatThrownBy(() -> extractor.extract(compilationUnit, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source relative path must not be blank");
    }

    @Test
    void extractsEnumRecordAndConstructorDependencies() {
        CompilationUnit compilationUnit = new JavaParser().parse("""
            package example;
            enum Mode implements Runnable {
                ONE;
                public void run() {}
            }
            record Item(Input input) implements java.io.Serializable {}
            class Sample {
                Sample(Input input) throws Problem {}
            }
            """).getResult().orElseThrow();

        List<ScanDependency> dependencies = extractor.extract(compilationUnit, "example/Sample.java");

        assertThat(dependencies)
                .extracting(dependency -> dependency.kind() + ":" + dependency.ownerType() + ":" + dependency.target())
                .contains(
                        DependencyKind.IMPLEMENTS + ":example.Mode:Runnable",
                        DependencyKind.IMPLEMENTS + ":example.Item:java.io.Serializable",
                        DependencyKind.PARAMETER_TYPE + ":example.Item:Input",
                        DependencyKind.PARAMETER_TYPE + ":example.Sample:Input",
                        DependencyKind.THROWN_TYPE + ":example.Sample:Problem");
    }

    @Test
    void usesSourceRelativePathAsOwnerWhenCompilationUnitHasNoTypes() {
        CompilationUnit compilationUnit = new JavaParser().parse("""
            package example;
            import java.util.Map;
            """).getResult().orElseThrow();

        List<ScanDependency> dependencies = extractor.extract(compilationUnit, "example/package-info.java");

        assertThat(dependencies)
                .singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.kind()).isEqualTo(DependencyKind.IMPORT);
                    assertThat(dependency.ownerType()).isEqualTo("example.package-info");
                    assertThat(dependency.target()).isEqualTo("java.util.Map");
                });
    }
}
