package de.burger.forensics.application.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationRequestTest {

    @Test
    void copiesOptionalCollections() {
        GenerationRequest request = new GenerationRequest(
            Path.of("src"),
            "org.example.Helper",
            true,
            true,
            List.of("com.example"),
            List.of("com.example.generated"),
            1,
            true,
            List.of("value")
        );

        assertThat(request.packagePrefixes()).containsExactly("com.example");
        assertThat(request.excludedPackagePrefixes()).containsExactly("com.example.generated");
        assertThat(request.strictConditionValidation()).isTrue();
        assertThat(request.trackedVariables()).containsExactly("value");
    }

    @Test
    void replacesNullCollectionsWithEmptyLists() {
        GenerationRequest request = new GenerationRequest(
            Path.of("src"),
            "org.example.Helper",
            true,
            true,
            null,
            null,
            0,
            false,
            null
        );

        assertThat(request.packagePrefixes()).isEmpty();
        assertThat(request.excludedPackagePrefixes()).isEmpty();
        assertThat(request.trackedVariables()).isEmpty();
    }

    @Test
    void replacesBlankHelperWithDefault() {
        GenerationRequest request = new GenerationRequest(
            Path.of("src"),
            "  ",
            true,
            true,
            List.of(),
            0,
            false,
            List.of()
        );

        assertThat(request.helperFqcn()).isEqualTo(GenerationRequest.DEFAULT_HELPER_FQCN);
    }

    @Test
    void requiresMandatoryFields() {
        List<String> emptyList = List.of();
        assertThatThrownBy(() -> new GenerationRequest(
            null,
            "helper",
            true,
            true,
            emptyList,
            0,
            false,
            emptyList
        )).isInstanceOf(NullPointerException.class);

        Path root = Path.of("src");
        assertThatThrownBy(() -> new GenerationRequest(
            root,
            null,
            true,
            true,
            emptyList,
            0,
            false,
            emptyList
        )).isInstanceOf(NullPointerException.class);
    }
}
