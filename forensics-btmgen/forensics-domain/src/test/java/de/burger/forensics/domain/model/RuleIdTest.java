package de.burger.forensics.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuleIdTest {

    @Test
    void rejectsBlankValues() {
        assertThatThrownBy(() -> new RuleId(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNonBlankValues() {
        assertThatCode(() -> new RuleId("abc"))
            .doesNotThrowAnyException();
    }
}
