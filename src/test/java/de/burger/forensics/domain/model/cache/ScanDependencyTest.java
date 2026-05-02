package de.burger.forensics.domain.model.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanDependencyTest {

    @Test
    void keepsParserDependencyCoordinates() {
        ScanDependency dependency = new ScanDependency(
                DependencyKind.METHOD_CALL,
                "sample/Sample.java",
                "sample.Sample",
                "run",
                "sample.Target.call",
                12,
                8);

        assertThat(dependency.kind()).isEqualTo(DependencyKind.METHOD_CALL);
        assertThat(dependency.sourceRelativePath()).isEqualTo("sample/Sample.java");
        assertThat(dependency.ownerType()).isEqualTo("sample.Sample");
        assertThat(dependency.ownerMember()).isEqualTo("run");
        assertThat(dependency.target()).isEqualTo("sample.Target.call");
        assertThat(dependency.line()).isEqualTo(12);
        assertThat(dependency.column()).isEqualTo(8);
    }

    @Test
    void rejectsMissingTarget() {
        assertThatThrownBy(() -> new ScanDependency(
                DependencyKind.METHOD_CALL,
                "sample/Sample.java",
                "sample.Sample",
                "run",
                " ",
                12,
                8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingSourceRelativePath() {
        assertThatThrownBy(() -> new ScanDependency(
                DependencyKind.METHOD_CALL,
                null,
                "sample.Sample",
                "run",
                "sample.Target.call",
                12,
                8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanDependency(
                DependencyKind.METHOD_CALL,
                " ",
                "sample.Sample",
                "run",
                "sample.Target.call",
                12,
                8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingOwnerType() {
        assertThatThrownBy(() -> new ScanDependency(
                DependencyKind.METHOD_CALL,
                "sample/Sample.java",
                null,
                "run",
                "sample.Target.call",
                12,
                8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanDependency(
                DependencyKind.METHOD_CALL,
                "sample/Sample.java",
                " ",
                "run",
                "sample.Target.call",
                12,
                8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingKind() {
        assertThatThrownBy(() -> new ScanDependency(
                null,
                "sample/Sample.java",
                "sample.Sample",
                "run",
                "sample.Target.call",
                12,
                8))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThatThrownBy(() -> new ScanDependency(
                DependencyKind.METHOD_CALL,
                "sample/Sample.java",
                "sample.Sample",
                "run",
                "sample.Target.call",
                -1,
                8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanDependency(
                DependencyKind.METHOD_CALL,
                "sample/Sample.java",
                "sample.Sample",
                "run",
                "sample.Target.call",
                12,
                -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
