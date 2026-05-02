package de.burger.forensics.domain.model.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyKindTest {

    @Test
    void mapsCacheTokensToDependencyKinds() {
        assertThat(DependencyKind.fromCacheToken("import")).isEqualTo(DependencyKind.IMPORT);
        assertThat(DependencyKind.fromCacheToken("extends")).isEqualTo(DependencyKind.EXTENDS);
        assertThat(DependencyKind.fromCacheToken("implements")).isEqualTo(DependencyKind.IMPLEMENTS);
        assertThat(DependencyKind.fromCacheToken("annotation")).isEqualTo(DependencyKind.ANNOTATION);
        assertThat(DependencyKind.fromCacheToken("thrown-type")).isEqualTo(DependencyKind.THROWN_TYPE);
        assertThat(DependencyKind.fromCacheToken("return-type")).isEqualTo(DependencyKind.RETURN_TYPE);
        assertThat(DependencyKind.fromCacheToken("parameter-type")).isEqualTo(DependencyKind.PARAMETER_TYPE);
        assertThat(DependencyKind.fromCacheToken("field-access")).isEqualTo(DependencyKind.FIELD_ACCESS);
        assertThat(DependencyKind.fromCacheToken("method-call")).isEqualTo(DependencyKind.METHOD_CALL);
        assertThat(DependencyKind.fromCacheToken("constructor-call")).isEqualTo(DependencyKind.CONSTRUCTOR_CALL);
    }

    @Test
    void exposesStableCacheTokens() {
        assertThat(DependencyKind.IMPORT.cacheToken()).isEqualTo("import");
        assertThat(DependencyKind.EXTENDS.cacheToken()).isEqualTo("extends");
        assertThat(DependencyKind.IMPLEMENTS.cacheToken()).isEqualTo("implements");
        assertThat(DependencyKind.ANNOTATION.cacheToken()).isEqualTo("annotation");
        assertThat(DependencyKind.THROWN_TYPE.cacheToken()).isEqualTo("thrown-type");
        assertThat(DependencyKind.RETURN_TYPE.cacheToken()).isEqualTo("return-type");
        assertThat(DependencyKind.PARAMETER_TYPE.cacheToken()).isEqualTo("parameter-type");
        assertThat(DependencyKind.FIELD_ACCESS.cacheToken()).isEqualTo("field-access");
        assertThat(DependencyKind.METHOD_CALL.cacheToken()).isEqualTo("method-call");
        assertThat(DependencyKind.CONSTRUCTOR_CALL.cacheToken()).isEqualTo("constructor-call");
    }

    @Test
    void rejectsUnknownCacheTokens() {
        assertThatThrownBy(() -> DependencyKind.fromCacheToken("database-row"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
