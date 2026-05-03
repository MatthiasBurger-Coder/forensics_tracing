package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTypeReferenceQualifierTest {

    private MethodEventExtractor extractor;
    private TypeReferenceQualifier qualifier;

    @BeforeEach
    void setUp() {
        InstanceFieldNormalizer normalizer = new InstanceFieldNormalizer();
        extractor = new MethodEventExtractor(new DefaultConditionRenderingStrategy(normalizer));
        qualifier = new DefaultTypeReferenceQualifier(normalizer, new StaticFieldQualifier());
    }

    @Test
    void qualifiesExplicitTypeReferencesBeforeRendering() {
        MethodDeclaration declaration = parseMethod("""
            import org.example.DeploymentTypeMarker;

            class Sample {
                boolean check(Object deploymentUnit) {
                    if (DeploymentTypeMarker.isType(deploymentUnit)) {
                        return true;
                    }
                    return false;
                }
            }
            """);

        String rendered = qualify(declaration);

        assertThat(rendered).isEqualTo("org.example.DeploymentTypeMarker.isType($1)");
    }

    @Test
    void leavesAmbiguousWildcardTypeReferencesUnchanged() {
        MethodDeclaration declaration = parseMethod("""
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

        String rendered = qualify(declaration);

        assertThat(rendered).isEqualTo("Status.enabled()");
    }

    private String qualify(MethodDeclaration declaration) {
        MethodScanContext context = extractor.methodContext(declaration);
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();
        return qualifier.qualify(condition, context).expression().toString();
    }

    private MethodDeclaration parseMethod(String source) {
        return StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow();
    }
}
