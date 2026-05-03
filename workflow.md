# workload.md — Stabilize BTM Rule Generation

## Context

The BTM rule generation was reworked and the current generated output was provided as `forensics.btm`. The current repository snapshot was provided as `forensics_tracing.zip`, and a slice-based workflow was provided as `workflow.md`.

The goal of this workload is to make the generator, tests, and generated BTM output consistent and reliable for large Java projects such as WildFly.

## Hard Constraints

* Use Gradle 9.1 via the Gradle Wrapper.
* Use the Java toolchain configured by the repository. Do not change the Java toolchain as part of this workload.
* Do not weaken dependency verification if dependency verification is present.
* Do not manually patch generated BTM files as the primary fix.
* Fix the generator and tests first, then regenerate the BTM output.
* Keep production source code and source comments in English.
* Keep changes slice-based, small, testable, and reviewable.
* Do not mix functional generator changes with unrelated formatting or cleanup.

## Current BTM Output Review

The current `forensics.btm` output is structurally complete at file level:

* Rule blocks found: `81,194`
* `ENDRULE` blocks found: `81,194`
* `helper().` occurrences: `0`
* `ENABLE_LOG` occurrences: `0`
* IF branch rules using `AT LINE`: `28,858`

  * `if-true`: `14,429`
  * `if-false`: `14,429`
* Method enter rules: `15,496`
* Method exit rules: `15,496`
* Return rules: `12,874`
* Throw rules: `3,790`
* Switch rules: `909`
* Switch-case rules: `3,771`

This shows meaningful progress: IF conditions are now emitted at source lines instead of method entry, and helper calls are direct.

## Blocking Findings

### Finding 1 — Source ZIP and generated BTM output are inconsistent

The provided `forensics.btm` contains IF branch rules with `AT LINE <n>`.

Example from generated output:

```btm
RULE df5ccf8ac84134c187c7d8540efce065 : if-true org.jboss.as.appclient.deployment.ActiveApplicationClientProcessor#deploy#deploy
CLASS org.jboss.as.appclient.deployment.ActiveApplicationClientProcessor
METHOD deploy(DeploymentPhaseContext)
HELPER de.burger.forensics.infrastructure.rt.RtTraceHelper
AT LINE 40
IF eval("df5ccf8ac84134c187c7d8540efce065", "!DeploymentTypeMarker.isType(DeploymentType.EAR, $deploymentUnit)", !DeploymentTypeMarker.isType(DeploymentType.EAR, $deploymentUnit))
DO
    onBranch(org.jboss.as.appclient.deployment.ActiveApplicationClientProcessor.class, "deploy", "IF_TRUE");
ENDRULE
```

However, the provided Java source snapshot still renders IF rules with `AT ENTRY` in:

* `src/main/java/de/burger/forensics/plugin/btmgen/render/impl/IfTrueRuleStrategy.java`
* `src/main/java/de/burger/forensics/plugin/btmgen/render/impl/IfFalseRuleStrategy.java`

The current `RuleParams` record also has no line-number field, and `BytemanRuleRenderAdapter` does not pass `SourceLocation.line()` to render strategies.

#### Required correction

Align the committed source with the generated output strategy:

* Extend rule rendering input with the source line.
* Pass `Rule.location().line()` from the domain rule into the renderer.
* Render `AT LINE <line>` for line-bound rules when `line > 0`.
* Fall back explicitly and predictably when `line <= 0`.
* Add tests proving that IF rules generated from scanned source render with `AT LINE`.

### Finding 2 — Switch and switch-case rules still fire at method entry

The generated output still renders switch-related rules as `AT ENTRY`:

* `switch`: `909` rules at `AT ENTRY`
* `switch-case`: `3,771` rules at `AT ENTRY`

This is semantically wrong for branch tracing because these rules fire when the method is entered, not when the switch statement or case is reached.

Current output example:

```btm
RULE 580cd80db4ed339ba955f892f27e2d7d : switch org.jboss.as.appclient.subsystem.parsing.AppClientXml_All#parseSocketBindingGroup#parseSocketBindingGroup
CLASS org.jboss.as.appclient.subsystem.parsing.AppClientXml_All
METHOD parseSocketBindingGroup(XMLExtendedStreamReader, Set, ModelNode, List)
HELPER de.burger.forensics.infrastructure.rt.RtTraceHelper
AT ENTRY
IF true
DO
    onSwitch(org.jboss.as.appclient.subsystem.parsing.AppClientXml_All.class, "parseSocketBindingGroup", "org.jboss.as.appclient.subsystem.parsing.AppClientXml_All#parseSocketBindingGroup" );
ENDRULE
```

#### Required correction

Switch tracing must be line-aware as well:

* `SWITCH` should render at the selector/source line using `AT LINE <line>`.
* `SWITCH_CASE` should render at the case/source line when a reliable line exists.
* The switch selector expression must not be lost.
* The generated action should include useful selector or label metadata.
* Add tests proving switch and switch-case rules no longer default to `AT ENTRY` when line data is available.

### Finding 3 — Return rules for boolean methods lose `false` return values

The generated output contains `1,319` return rules with:

```btm
IF $!
```

Example:

```btm
RULE 4bf1a0af98603ed89878e5ff108aa036 : return org.jboss.as.appclient.component.ApplicationClientComponentDescription#isIntercepted#isIntercepted
CLASS org.jboss.as.appclient.component.ApplicationClientComponentDescription
METHOD isIntercepted()
HELPER de.burger.forensics.infrastructure.rt.RtTraceHelper
AT EXIT
IF $!
DO
    onExit(org.jboss.as.appclient.component.ApplicationClientComponentDescription.class, "isIntercepted", $! );
ENDRULE
```

This means the return-value rule fires only when the returned boolean is `true`. If the method returns `false`, the rule does not run and the actual returned value is not captured.

#### Required correction

Return value tracing must not be filtered by the returned value:

* Return value rules should use `IF true` when they are intended to capture the return value.
* For non-void methods, `AT EXIT` may use `$!` as the return value.
* For void methods, do not use `$!`.
* Do not create a condition strategy that turns boolean return values into `IF $!`.
* Add tests for boolean methods returning both `true` and `false` expectations at rule-rendering level.

### Finding 4 — Rule display names duplicate the method name

Most generated rule names contain the method name twice:

```btm
RULE 2a448d3bed00317092cb992f4f671a67 : enter org.jboss.as.appclient.component.ApplicationClientComponentDescription#create#create
```

Observed count:

* Duplicated `class#method#method` style rule names: `75,932`

This is mostly cosmetic, but it makes large BTM files harder to read and review.

#### Required correction

Normalize the display naming contract:

* Either `displayName` is the full `class#method` label and strategies must not append `#methodName` again.
* Or `displayName` is only a human label, and strategies compose `class#method` themselves.
* Apply one consistent rule across all render strategies.
* Add renderer tests that assert rule names are not duplicated.

### Finding 5 — Safe `eval` currently records both condition evaluation and taken branch semantics through the same event path

The generated IF rules use:

```btm
IF eval("ruleId", "expression", expression)
DO
    onBranch(Target.class, "method", "IF_TRUE");
ENDRULE
```

The helper method `eval(...)` records the evaluated boolean value, while the `DO` block records the actually taken branch. This can be useful, but it must be explicit because otherwise branch traces may look duplicated or misleading.

#### Required correction

Decide and document the semantics:

* `eval(...)` should represent condition evaluation, not branch taken.
* `onBranch(...)` should represent branch taken.
* If both are kept, use separate event types or clearly distinct labels.
* If only branch-taken tracing is required, remove branch event emission from `eval(...)` and keep it only for safe failure handling.
* Add tests around helper behavior and expected output semantics.

### Finding 6 — Unqualified source type names may fail Byteman type checking

Some generated IF expressions contain unqualified source-level type references, for example:

```btm
DeploymentTypeMarker.isType(DeploymentType.EAR, $deploymentUnit)
SubsystemResourceRegistration.of("infinispan")
```

Byteman rule expressions are type-checked Java-like expressions at injection time. Without imports or fully qualified names, these expressions may fail depending on target classloader and type resolution.

#### Required correction

Add an explicit validation strategy:

* Preserve source imports during scanning, or qualify static/type references where possible.
* Alternatively, generate `IMPORT` lines if supported by the chosen Byteman usage mode.
* At minimum, create a validation/report mode that flags expressions containing unresolved simple type names.
* Add tests for imported static/type references in conditions.

## Target Architecture Change

### Rendering input must become line-aware

Extend `RuleParams` or introduce a better value object:

```java
public record RuleParams(
        String id,
        String className,
        String methodName,
        String methodDesc,
        String displayName,
        String condition,
        String sqlHint,
        String helperFqn,
        int line
) {
    public static final String DEFAULT_HELPER_FQN = "de.burger.forensics.infrastructure.rt.RtTraceHelper";
}
```

If constructor compatibility is important, add an overloaded factory or compact compatibility constructor instead of breaking all tests at once.

### Introduce location rendering helper

Create one shared method in the rendering SPI:

```java
protected static String atLineOrEntry(int line) {
    return line > 0 ? "AT LINE " + line : "AT ENTRY";
}
```

Use a different helper for return/exit rules:

```java
protected static String atExit() {
    return "AT EXIT";
}
```

The goal is to avoid each strategy inventing its own fallback rules.

### Recommended rule location policy

| Rule type                         | Preferred location                          | Reason                                                     |
| --------------------------------- | ------------------------------------------- | ---------------------------------------------------------- |
| METHOD_ENTER                      | `AT ENTRY`                                  | Method boundary                                            |
| METHOD_EXIT for void/generic exit | `AT EXIT`                                   | Method boundary                                            |
| RETURN value capture              | `AT EXIT`                                   | `$!` is only valid at method exit for return value capture |
| IF_TRUE                           | `AT LINE <condition line>`                  | Branch condition line                                      |
| IF_FALSE                          | `AT LINE <condition line>`                  | Branch condition line                                      |
| SWITCH                            | `AT LINE <selector line>`                   | Switch reached                                             |
| SWITCH_CASE                       | `AT LINE <case line>` if reliable           | Case reached, with known limits                            |
| THROW                             | `AT THROW`                                  | Throwable available as `$^`                                |
| JDBC_EXECUTE                      | `AT ENTRY` + `AT EXIT` around JDBC call     | IO timing boundary                                         |
| THREAD_LIFECYCLE                  | `AT ENTRY` on `Thread.start()` / `join(..)` | Lifecycle call boundary                                    |

## Slice Plan

### Slice 0 — Baseline and reproducibility

Goal: Establish current behavior without changing production logic.

Files likely inspected:

* `forensics.btm`
* `src/main/java/de/burger/forensics/plugin/btmgen/render/api/RuleParams.java`
* `src/main/java/de/burger/forensics/plugin/btmgen/internal/BytemanRuleRenderAdapter.java`
* `src/main/java/de/burger/forensics/plugin/btmgen/render/impl/*RuleStrategy.java`
* `src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java`
* `src/main/java/de/burger/forensics/domain/strategy/DefaultStrategyFactory.java`
* `src/test/java/de/burger/forensics/plugin/btmgen/**`

Commands:

```bash
git status --short
git diff
git diff --cached
./gradlew test --stacktrace
```

Expected result:

* Current tests may pass even though the generated output is semantically incomplete.
* If Gradle Wrapper must download Gradle and network is unavailable, record the blocker exactly.

### Slice 1 — Make rendering line-aware

Goal: Pass `SourceLocation.line()` into renderer strategies.

Expected changes:

* Extend `RuleParams` with `line` or introduce `RuleLocationParams`.
* Update `BytemanRuleRenderAdapter` to pass `rule.location().line()`.
* Add shared SPI helper for `AT LINE` fallback.
* Update tests for constructor/factory changes.

Expected tests:

```bash
./gradlew test --tests "de.burger.forensics.plugin.btmgen.render.impl.*" --stacktrace
./gradlew test --tests "de.burger.forensics.plugin.btmgen.gradle.GenerateBtmTaskTest" --stacktrace
```

### Slice 2 — Render IF rules at source lines from the committed source

Goal: Ensure source and generated BTM output are aligned for IF rules.

Expected changes:

* `IfTrueRuleStrategy` uses `AT LINE <line>` when line is available.
* `IfFalseRuleStrategy` uses `AT LINE <line>` when line is available.
* Tests assert no `AT ENTRY` for IF rules with line data.

Acceptance criteria:

* IF rules generated from scanned Java source contain `AT LINE`.
* IF fallback is explicit for missing line numbers.
* No `ENABLE_LOG` appears.
* No `helper().` appears.

### Slice 3 — Fix switch and switch-case rendering semantics

Goal: Prevent switch/case rules from firing at method entry.

Expected changes:

* `SwitchRuleStrategy` uses line-aware location.
* `SwitchCaseRuleStrategy` uses line-aware location where available.
* Preserve selector/label metadata correctly.
* Do not discard `Rule.condition()` for `SWITCH`.

Expected tests:

* Switch rule generated from a source `switch` contains `AT LINE`.
* Switch-case generated from a source case contains `AT LINE` when line is available.
* `onSwitch(...)` contains useful selector metadata.
* `onCase(...)` contains the case label only, not a duplicated method label.

### Slice 4 — Fix return-value tracing for boolean methods

Goal: Boolean return values must be traced for both `true` and `false`.

Expected changes:

* `DefaultStrategyFactory` must not turn boolean return tracing into `IF $!`.
* `ReturnRuleStrategy` should render `IF true` for return value capture.
* Keep `$!` only in the `DO` action for non-void return value capture.
* Avoid `$!` for void methods.

Expected tests:

* Boolean return rule uses `IF true`.
* Boolean return rule action still passes `$!`.
* Non-boolean return rule uses `IF true`.
* Void method exit uses `null` and does not use `$!`.

### Slice 5 — Normalize display names

Goal: Remove duplicated `class#method#method` names.

Expected changes:

* Define one display-name contract.
* Update all strategy rule titles consistently.
* Keep action payloads stable unless explicitly changed.

Expected tests:

* Rule title contains `com.example.Foo#bar`.
* Rule title does not contain `com.example.Foo#bar#bar`.
* Switch-case rule title does not become `CASE#method` unless that is intentionally documented.

### Slice 6 — Clarify safe-eval event semantics

Goal: Avoid misleading duplicate branch telemetry.

Expected changes:

Choose one of these policies:

A. Keep both evaluation and branch-taken events, but rename/structure evaluation output so it is not emitted as `BRANCH_TAKEN`.

B. Use `eval(...)` only as a safe boolean guard and emit branch events only in the `DO` block.

Recommended: Policy B for lower noise in large traces.

Expected tests:

* `eval(...)` returns the condition value.
* `eval(...)` records condition errors.
* `eval(...)` does not emit a misleading branch-taken event for the non-taken branch.
* `onBranch(...)` remains the only branch-taken event.

### Slice 7 — Add validation/reporting for unresolved type references

Goal: Make Byteman injection risks visible before loading a huge `.btm` file.

Expected changes:

* Add a lightweight validation/report object for expressions that contain suspicious unresolved simple type names.
* Do not fail generation by default unless strict validation mode is enabled.
* Document known limitations around imports, debug information, and local variable names.

Expected tests:

* Condition with imported simple type name is reported or qualified.
* Condition with already fully qualified type is accepted.
* Local variables and parameters using `$name` / `$1` are not falsely reported as unresolved types.

### Slice 8 — Regenerate and verify `forensics.btm`

Goal: Produce a new generated output that matches the fixed source.

Expected commands:

```bash
./gradlew clean test --stacktrace
./gradlew generateForensicsRules --stacktrace
```

If testing against WildFly or another external project:

```bash
./gradlew generateForensicsRules --stacktrace
```

Expected output checks:

```bash
grep -c '^RULE ' build/forensics/forensics.btm
grep -c '^ENDRULE' build/forensics/forensics.btm
grep -c 'helper().' build/forensics/forensics.btm
grep -c 'ENABLE_LOG' build/forensics/forensics.btm
grep -c 'AT ENTRY' build/forensics/forensics.btm
grep -c 'AT LINE' build/forensics/forensics.btm
grep -c 'IF \$!' build/forensics/forensics.btm
grep -E ':[[:space:]]+[a-z-]+ .*#([^#]+)#\1$' build/forensics/forensics.btm
```

Acceptance criteria:

* `RULE` count equals `ENDRULE` count.
* `helper().` count is `0`.
* `ENABLE_LOG` count is `0`.
* IF branch rules use `AT LINE` when line data exists.
* Switch rules no longer default to `AT ENTRY` when line data exists.
* Switch-case rules no longer default to `AT ENTRY` when line data exists.
* Return value rules do not use `IF $!`.
* No duplicated `class#method#method` rule display names remain.

### Slice 9 — Final quality gate and report

Goal: Verify final state and document remaining limits.

Commands:

```bash
git status --short
./gradlew test --stacktrace
./gradlew check --stacktrace
```

If `QUALITY.md` defines a broader command, use that command exactly.

Final report must include:

* Files changed
* Tests executed
* Quality gate result
* BTM rule count summary
* Known Byteman limitations
* Any unresolved validation warnings
* Whether the generated `forensics.btm` was regenerated from source

## Definition of Done

The workload is complete only when all of the following are true:

* The committed source can actually generate the observed BTM strategy.
* IF branch rules are line-aware.
* Switch and switch-case rules are line-aware where possible.
* Boolean `false` return values are not lost.
* Rule display names are not duplicated.
* Helper calls remain direct.
* No `ENABLE_LOG` placeholder leaks into generated rules.
* Tests cover line-aware rendering, return-value tracing, switch rendering, and display-name normalization.
* The final BTM output is regenerated from the fixed generator.
* Remaining Byteman limitations are explicitly documented instead of hidden.

## Recommended Commit Structure

```text
test: capture current btm rendering expectations
feat: pass source line metadata into btm rule rendering
fix: render branch rules at source line locations
fix: render switch rules at source line locations
fix: trace boolean return values without filtering false results
fix: normalize btm rule display names
docs: document btm generation limits and validation workflow
```

Do not squash these slices until review is complete.
