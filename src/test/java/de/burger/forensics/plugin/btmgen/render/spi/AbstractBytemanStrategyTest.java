package de.burger.forensics.plugin.btmgen.render.spi;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractBytemanStrategyTest {

    private final Probe probe = new Probe();

    @Test
    void safeIdGeneratesValuesForNullAndBlankInputs() {
        assertThat(probe.safeIdValue("rule-1")).isEqualTo("rule-1");
        assertThat(probe.safeIdValue(null)).isNotBlank();
        assertThat(probe.safeIdValue("   ")).isNotBlank();
    }

    @Test
    void fallbackHelpersHandleNullAndBlankInputs() {
        assertThat(probe.ifClauseValue(null)).isEqualTo("IF true");
        assertThat(probe.ifClauseValue("flag")).isEqualTo("IF flag");
        assertThat(probe.atLineOrEntryValue(42)).isEqualTo("AT LINE 42");
        assertThat(probe.atLineOrEntryValue(0)).isEqualTo("AT ENTRY");
        assertThat(probe.atLineOrEntryValue(-1)).isEqualTo("AT ENTRY");
        assertThat(probe.atExitValue()).isEqualTo("AT EXIT");
        assertThat(probe.orValue("label", "fallback")).isEqualTo("label");
        assertThat(probe.orValue("  ", "fallback")).isEqualTo("fallback");
        assertThat(probe.ruleTargetValue(params("com.example.Foo#work")))
                .isEqualTo("com.example.Foo#work");
        assertThat(probe.ruleTargetValue(params("case 1")))
                .isEqualTo("com.example.Foo#work");
        assertThat(probe.ruleTargetValue(params("com.example.Foo#work#work")))
                .isEqualTo("com.example.Foo#work");
        assertThat(probe.ruleTargetValue(params(" ")))
                .isEqualTo("com.example.Foo#work");
        assertThat(probe.ruleTargetValue(params(null, null, null)))
                .isEqualTo("<unknown>#<method>");
        assertThat(probe.ruleTargetValue(params(null, " ", " ")))
                .isEqualTo("<unknown>#<method>");
        assertThat(probe.methodSigValue("work", null)).isEqualTo("work");
        assertThat(probe.methodSigValue("work", "(I)V")).isEqualTo("work(I)V");
    }

    @Test
    void escAndGuardedConditionEscapeSpecialCharactersAndFallbackToFalse() {
        assertThat(probe.escValue(null)).isEmpty();
        assertThat(probe.escValue("a\\b\"c")).isEqualTo("a\\\\b\\\"c");
        assertThat(probe.guardedConditionValue("rule\"1", null, null))
                .isEqualTo("eval(\"rule\\\"1\", \"false\", false)");
        assertThat(probe.guardedConditionValue("rule-2", "value\\\"x", "flag"))
                .isEqualTo("eval(\"rule-2\", \"value\\\\\\\"x\", flag)");
    }

    @Test
    void sanitizeConditionRemovesPlaceholdersAndKeepsMeaningfulConditions() {
        assertThat(probe.sanitizeConditionValue(null)).isNull();
        assertThat(probe.sanitizeConditionValue("   ")).isNull();
        assertThat(probe.sanitizeConditionValue("ENABLE_LOG")).isNull();
        assertThat(probe.sanitizeConditionValue("!(ENABLE_LOG)")).isNull();
        assertThat(probe.sanitizeConditionValue("  flag  ")).isEqualTo("flag");
    }

    @Test
    void classPlaceholderAndStaticNullCheckHelpersHandleNullsAndMatches() {
        assertThat(probe.resolveClassPlaceholderValue(null, null)).isNull();
        assertThat(probe.resolveClassPlaceholderValue("   ", "$CLASS.INSTANCE")).isEqualTo("$CLASS.INSTANCE");
        assertThat(probe.resolveClassPlaceholderValue("com.example.Foo", "$CLASS.INSTANCE"))
                .isEqualTo("com.example.Foo.INSTANCE");

        assertThat(probe.qualifyStaticNullCheckValue("   ", "INSTANCE == null")).isEqualTo("INSTANCE == null");
        assertThat(probe.qualifyStaticNullCheckValue("com.example.Foo", null)).isNull();
        assertThat(probe.qualifyStaticNullCheckValue("com.example.Foo", "$value == null")).isEqualTo("$value == null");
        assertThat(probe.qualifyStaticNullCheckValue("com.example.Foo", "(INSTANCE != null)"))
                .isEqualTo("com.example.Foo.INSTANCE != null");
    }

    @Test
    void requireSourceLineReturnsPositiveValuesAndRejectsMissingLocations() {
        RuleParams withLine = new RuleParams(
                "rule-1",
                "com.example.Foo",
                "work",
                "()V",
                "Foo#work",
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                23,
                null
        );
        RuleParams withoutLine = new RuleParams(
                "rule-2",
                " ",
                "",
                "()V",
                "Foo#work",
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                RuleParams.UNKNOWN_SOURCE_LINE,
                null
        );

        assertThat(probe.requireSourceLineValue(withLine, "IF_TRUE")).isEqualTo(23);
        assertThatThrownBy(() -> probe.requireSourceLineValue(withoutLine, "IF_TRUE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IF_TRUE rule requires a valid source line for <unknown>#<method>");
    }

    private static final class Probe extends AbstractBytemanStrategy {
        private String safeIdValue(String value) {
            return safeId(value);
        }

        private String ifClauseValue(String condition) {
            return ifClause(condition);
        }

        private String atLineOrEntryValue(int line) {
            return atLineOrEntry(line);
        }

        private String atExitValue() {
            return atExit();
        }

        private String orValue(String fallback, String value) {
            return or(fallback, value);
        }

        private String ruleTargetValue(RuleParams params) {
            return ruleTarget(params);
        }

        private String escValue(String value) {
            return esc(value);
        }

        private String methodSigValue(String name, String desc) {
            return methodSig(name, desc);
        }

        private String sanitizeConditionValue(String condition) {
            return sanitizeCondition(condition);
        }

        private String resolveClassPlaceholderValue(String className, String expression) {
            return resolveClassPlaceholder(className, expression);
        }

        private String qualifyStaticNullCheckValue(String className, String condition) {
            return qualifyStaticNullCheck(className, condition);
        }

        private int requireSourceLineValue(RuleParams params, String templateId) {
            return requireSourceLine(params, templateId);
        }

        private String guardedConditionValue(String ruleId, String expression, String evaluation) {
            return guardedCondition(ruleId, expression, evaluation);
        }
    }

    private static RuleParams params(String displayName) {
        return params(displayName, "com.example.Foo", "work");
    }

    private static RuleParams params(String displayName, String className, String methodName) {
        return new RuleParams(
                "rule-target",
                className,
                methodName,
                "()V",
                displayName,
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );
    }
}
