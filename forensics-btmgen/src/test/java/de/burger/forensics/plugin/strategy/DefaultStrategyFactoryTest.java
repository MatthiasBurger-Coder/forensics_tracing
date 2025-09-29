package de.burger.forensics.plugin.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStrategyFactoryTest {

    private final StrategyFactory factory = new DefaultStrategyFactory();

    @Test
    void picksNullCheck() {
        ConditionStrategy s = factory.from("obj == null");
        assertThat(s.typeName()).contains("NullCheck");
        assertThat(s.toBytemanIf()).isEqualTo("obj == null");
    }

    @Test
    void picksInstanceOf() {
        ConditionStrategy s = factory.from("x instanceof Foo");
        assertThat(s.typeName()).contains("InstanceOf");
        assertThat(s.toBytemanIf()).isEqualTo("x instanceof Foo");
    }

    @Test
    void picksLogical() {
        ConditionStrategy s = factory.from("(a != null) && (b == 1)");
        assertThat(s.typeName()).contains("Logical");
        assertThat(s.toBytemanIf()).isEqualTo("(a != null) && (b == 1)");
    }

    @Test
    void fallsBackToGeneric() {
        ConditionStrategy s = factory.from("safeCall(a?.b())");
        assertThat(s.typeName()).contains("GenericUnsafe");
        assertThat(s.toBytemanIf()).isNotBlank();
    }
}
