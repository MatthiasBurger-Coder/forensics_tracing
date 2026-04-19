package de.burger.forensics.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleIdTest {

    @Test
    void rejectsBlankValues() {
        assertThatThrownBy(() -> new RuleId(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullValues() {
        assertThatThrownBy(() -> new RuleId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNonBlankValues() {
        assertThatCode(() -> new RuleId("abc"))
            .doesNotThrowAnyException();
    }
}
