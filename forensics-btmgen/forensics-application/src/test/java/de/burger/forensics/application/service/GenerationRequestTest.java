package de.burger.forensics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationRequestTest {

    @Test
    void copiesOptionalCollections() {
        GenerationRequest request = new GenerationRequest(
            Path.of("src"),
            "org.example.Helper",
            true,
            true,
            List.of("com.example"),
            1,
            List.of("value")
        );

        assertThat(request.packagePrefixes()).containsExactly("com.example");
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
            0,
            null
        );

        assertThat(request.packagePrefixes()).isEmpty();
        assertThat(request.trackedVariables()).isEmpty();
    }

    @Test
    void requiresMandatoryFields() {
        assertThatThrownBy(() -> new GenerationRequest(
            null,
            "helper",
            true,
            true,
            List.of(),
            0,
            List.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new GenerationRequest(
            Path.of("src"),
            null,
            true,
            true,
            List.of(),
            0,
            List.of()
        )).isInstanceOf(NullPointerException.class);
    }
}
