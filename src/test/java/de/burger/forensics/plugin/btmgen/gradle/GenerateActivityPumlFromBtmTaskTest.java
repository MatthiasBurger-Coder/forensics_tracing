package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerateActivityPumlFromBtmTaskTest {

    @Test
    void generateWritesActivityDiagramForParsedRules(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("rules.btm");
        Files.writeString(input, """
            RULE enter
            CLASS com.example.Service
            METHOD handle
            IF true
            DO onEnter(com.example.Service.class, "handle")
            ENDRULE

            RULE ifTrue
            CLASS com.example.Service
            METHOD handle
            IF eval("rule-1", "orderId != null", $1)
            DO onBranch(com.example.Service.class, "handle", "IF_TRUE")
            ENDRULE

            RULE ifFalse
            CLASS com.example.Service
            METHOD handle
            IF eval("rule-2", "orderId != null", $1)
            DO onBranch(com.example.Service.class, "handle", "IF_FALSE")
            ENDRULE

            RULE switch
            CLASS com.example.Service
            METHOD handle
            IF true
            DO onSwitch(com.example.Service.class, "handle", "branch")
            ENDRULE

            RULE switchCase
            CLASS com.example.Service
            METHOD handle
            IF true
            DO onCase(com.example.Service.class, "handle", "A")
            ENDRULE

            RULE throw
            CLASS com.example.Service
            METHOD handle
            IF true
            DO onException(new IllegalStateException("boom"))
            ENDRULE

            RULE ret
            CLASS com.example.Service
            METHOD handle
            IF true
            DO onReturn(com.example.Service.class, "handle", "ok")
            ENDRULE

            RULE exit
            CLASS com.example.Service
            METHOD handle
            IF true
            DO onExit(com.example.Service.class, "handle", "ok")
            ENDRULE
            """);
        Path output = tempDir.resolve("build/forensics/activity.puml");
        GenerateActivityPumlFromBtmTask task = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("generateActivityFromBtm", GenerateActivityPumlFromBtmTask.class)
            .get();
        task.getInputBtm().set(input.toFile());
        task.getOutputPuml().set(output.toFile());
        task.getDiagramTitle().set("Forensics \"Diagram\"\nView");

        task.generate();

        String rendered = Files.readString(output);
        assertThat(rendered).contains("@startuml");
        assertThat(rendered).contains("title Forensics 'Diagram' View");
        assertThat(rendered).contains("|Service|");
        assertThat(rendered).contains(":METHOD_ENTER x1;");
        assertThat(rendered).contains("if (true) then (true)");
        assertThat(rendered).contains(":IF_TRUE rule x1;");
        assertThat(rendered).contains(":IF_FALSE rule x1;");
        assertThat(rendered).contains(":SWITCH rule x1;");
        assertThat(rendered).contains(":SWITCH_CASE rule x1;");
        assertThat(rendered).contains(":THROW rule x1;");
        assertThat(rendered).contains(":RETURN rule x1;");
        assertThat(rendered).contains(":METHOD_EXIT x1;");
    }

    @Test
    void generateRejectsMissingInputs(@TempDir Path tempDir) {
        GenerateActivityPumlFromBtmTask task = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("missingBtm", GenerateActivityPumlFromBtmTask.class)
            .get();
        task.getInputBtm().set(tempDir.resolve("missing.btm").toFile());
        task.getOutputPuml().set(tempDir.resolve("out.puml").toFile());

        assertThatThrownBy(task::generate)
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("BTM input not found");
    }

    @Test
    void generateRejectsFilesWithoutParsableRules(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("invalid.btm");
        Files.writeString(input, """
            RULE invalid
            CLASS com.example.Service
            IF true
            DO onEnter(com.example.Service.class, "handle")
            ENDRULE
            """);
        GenerateActivityPumlFromBtmTask task = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("invalidBtm", GenerateActivityPumlFromBtmTask.class)
            .get();
        task.getInputBtm().set(input.toFile());
        task.getOutputPuml().set(tempDir.resolve("out.puml").toFile());

        assertThatThrownBy(task::generate)
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("No parsable rules found");
    }

    @Test
    void privateConditionExtractionAndHelpersCoverFallbackBranches() throws Exception {
        Method extractCondition = GenerateActivityPumlFromBtmTask.class.getDeclaredMethod("extractCondition", String.class);
        extractCondition.setAccessible(true);
        Method detectEvent = GenerateActivityPumlFromBtmTask.class.getDeclaredMethod("detectEvent", String.class);
        detectEvent.setAccessible(true);
        Method sanitize = GenerateActivityPumlFromBtmTask.class.getDeclaredMethod("sanitize", String.class);
        sanitize.setAccessible(true);
        Method simpleName = GenerateActivityPumlFromBtmTask.class.getDeclaredMethod("simpleName", String.class);
        simpleName.setAccessible(true);

        assertThat(extractCondition.invoke(null, (Object) null)).isNull();
        assertThat(extractCondition.invoke(null, "IF false")).isEqualTo("false");
        assertThat(extractCondition.invoke(null, "IF eval(\"rule-1\", \"orderId != null\", $1)")).isEqualTo("orderId != null");
        assertThat(extractCondition.invoke(null, "IF someCheck()")).isEqualTo("someCheck()");
        assertThat(detectEvent.invoke(null, "DO onEnter(com.example.Service.class, \"handle\")").toString()).isEqualTo("METHOD_ENTER");
        assertThat(detectEvent.invoke(null, "DO onExit(com.example.Service.class, \"handle\", \"ok\")").toString()).isEqualTo("METHOD_EXIT");
        assertThat(detectEvent.invoke(null, "DO onSwitch(com.example.Service.class, \"handle\", \"branch\")").toString()).isEqualTo("SWITCH");
        assertThat(detectEvent.invoke(null, "DO onCase(com.example.Service.class, \"handle\", \"A\")").toString()).isEqualTo("SWITCH_CASE");
        assertThat(detectEvent.invoke(null, "DO onException(new IllegalStateException(\"boom\"))").toString()).isEqualTo("THROW");
        assertThat(detectEvent.invoke(null, "DO onReturn(com.example.Service.class, \"handle\", \"ok\")").toString()).isEqualTo("RETURN");
        assertThat(detectEvent.invoke(null, "DO onBranch(com.example.Service.class, \"handle\", \"IF_TRUE\")").toString()).isEqualTo("IF_TRUE");
        assertThat(detectEvent.invoke(null, "DO onBranch(com.example.Service.class, \"handle\", \"IF_FALSE\")").toString()).isEqualTo("IF_FALSE");
        assertThat(detectEvent.invoke(null, "DO onUnknown()").toString()).isEqualTo("OTHER");
        assertThat(sanitize.invoke(null, "A\"B\r\nC")).isEqualTo("A'B  C");
        assertThat(simpleName.invoke(null, "")).isEqualTo("Unknown");
        assertThat(simpleName.invoke(null, "Service")).isEqualTo("Service");
    }

    @Test
    void privateRuleParsingAndMethodBlockHelpersCoverSkippedAndNegativeBranches() throws Exception {
        Method parseRules = GenerateActivityPumlFromBtmTask.class.getDeclaredMethod("parseRules", String.class);
        parseRules.setAccessible(true);
        Class<?> parsedRuleType = Class.forName("de.burger.forensics.plugin.btmgen.gradle.GenerateActivityPumlFromBtmTask$ParsedRule");
        Method conditionAccessor = parsedRuleType.getDeclaredMethod("condition");
        conditionAccessor.setAccessible(true);
        Method eventTypeAccessor = parsedRuleType.getDeclaredMethod("eventType");
        eventTypeAccessor.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Object> parsedRules = (List<Object>) parseRules.invoke(null, """
            preamble without a rule

            RULE missingClass
            METHOD skipped
            IF true
            DO onEnter(com.example.Service.class, "skipped")
            ENDRULE

            RULE missingMethod
            CLASS com.example.Service
            IF true
            DO onEnter(com.example.Service.class, "skipped")
            ENDRULE

            RULE enterWithoutDoPrefix
            CLASS com.example.Service
            METHOD handle
            IF eval("rule-1", "orderId != null", $1)
            onEnter(com.example.Service.class, "handle")
            ENDRULE

            RULE exitRule
            CLASS com.example.Service
            METHOD finish
            IF someCheck()
            DO onExit(com.example.Service.class, "finish", "ok")
            ENDRULE
            """);

        assertThat(parsedRules).hasSize(2);
        assertThat(conditionAccessor.invoke(parsedRules.get(0))).isEqualTo("orderId != null");
        assertThat(eventTypeAccessor.invoke(parsedRules.get(0)).toString()).isEqualTo("METHOD_ENTER");
        assertThat(eventTypeAccessor.invoke(parsedRules.get(1)).toString()).isEqualTo("METHOD_EXIT");

        Class<?> methodSummaryType = Class.forName("de.burger.forensics.plugin.btmgen.gradle.GenerateActivityPumlFromBtmTask$MethodSummary");
        Constructor<?> constructor = methodSummaryType.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        Method writeMethodBlock = GenerateActivityPumlFromBtmTask.class.getDeclaredMethod(
            "writeMethodBlock",
            StringBuilder.class,
            String.class,
            methodSummaryType
        );
        writeMethodBlock.setAccessible(true);
        Field ifTrueCount = methodSummaryType.getDeclaredField("ifTrueCount");
        ifTrueCount.setAccessible(true);
        Field ifFalseCount = methodSummaryType.getDeclaredField("ifFalseCount");
        ifFalseCount.setAccessible(true);
        Field conditions = methodSummaryType.getDeclaredField("conditions");
        conditions.setAccessible(true);

        Object emptySummary = constructor.newInstance("empty");
        StringBuilder emptyBlock = new StringBuilder();
        writeMethodBlock.invoke(null, emptyBlock, "Lane", emptySummary);
        assertThat(emptyBlock.toString())
            .contains("|Lane|")
            .contains(":empty();")
            .doesNotContain("METHOD_ENTER")
            .doesNotContain("IF_TRUE rule")
            .doesNotContain("SWITCH rule")
            .doesNotContain("METHOD_EXIT");

        Object falseOnlySummary = constructor.newInstance("falseBranch");
        ifFalseCount.setInt(falseOnlySummary, 1);
        StringBuilder falseOnlyBlock = new StringBuilder();
        writeMethodBlock.invoke(null, falseOnlyBlock, "Lane", falseOnlySummary);
        assertThat(falseOnlyBlock.toString())
            .contains("if (condition) then (true)")
            .contains(":IF_TRUE rule x0;")
            .contains(":IF_FALSE rule x1;");

        Object trueOnlySummary = constructor.newInstance("trueBranch");
        ifTrueCount.setInt(trueOnlySummary, 1);
        @SuppressWarnings("unchecked")
        Set<String> trueOnlyConditions = (Set<String>) conditions.get(trueOnlySummary);
        trueOnlyConditions.add("flag");
        StringBuilder trueOnlyBlock = new StringBuilder();
        writeMethodBlock.invoke(null, trueOnlyBlock, "Lane", trueOnlySummary);
        assertThat(trueOnlyBlock.toString())
            .contains("if (flag) then (true)")
            .contains(":IF_TRUE rule x1;")
            .contains(":IF_FALSE rule x0;");
    }
}
