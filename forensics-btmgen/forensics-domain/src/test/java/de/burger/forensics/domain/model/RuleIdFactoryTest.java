package de.burger.forensics.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuleIdFactoryTest {

    private final SourceLocation location = new SourceLocation("com.example.Demo", "test", 42);

    @Test
    void createsDeterministicIdFromScanEvent() {
        ScanEvent event = new ScanEvent(location, "test()", RuleType.IF_TRUE, "value > 0", "java");

        RuleId id1 = RuleIdFactory.from(event, "value > 0");
        RuleId id2 = RuleIdFactory.from(event, "value > 0");

        assertThat(id1.value()).isEqualTo(id2.value()).hasSize(32);
    }

    @Test
    void createsIdFromLocationAndType() {
        RuleId id = RuleIdFactory.from(location, RuleType.ENTRY);

        assertThat(id.value()).isNotBlank().hasSize(32);
    }

    @Test
    void rejectsNullArguments() {
        ScanEvent event = new ScanEvent(location, "test()", RuleType.IF_TRUE, "value > 0", "java");

        assertThatThrownBy(() -> RuleIdFactory.from(null, "foo"))
            .isInstanceOf(NullPointerException.class);
        assertThatCode(() -> RuleIdFactory.from(event, null))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> RuleIdFactory.from((SourceLocation) null, RuleType.ENTRY))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RuleIdFactory.from(location, null))
            .isInstanceOf(NullPointerException.class);
    }
}
