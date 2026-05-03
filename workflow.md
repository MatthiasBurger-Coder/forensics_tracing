# workflow.md — Fix BTM Generation Bugs from the WildFly Run

## Role

You are working as a Codex agent in the Forensics Tracing repository.

You are expected to be strong in:

* Java 17
* Gradle Wrapper based builds
* Gradle dependency verification
* Maven Mojo adapter development
* Byteman rule syntax
* JavaParser based source analysis
* JUnit 5 regression tests
* ArchUnit and hexagonal architecture boundaries

All source code, source comments, JavaDoc, test names, repository documentation, and commit messages must be written in English.

## Goal

The latest WildFly run generated a large `forensics.btm` successfully, but the generated rules still contain several semantic bugs and one serious diagnostics problem.

The goal of this workflow is to fix the generator, not to manually patch the generated `.btm` file.

After this workflow, a regenerated BTM file must satisfy these core contracts:

* `RULE` count equals `ENDRULE` count.
* No generated rule name contains duplicated `class#method#method` style titles.
* IF branch rules use `AT LINE <line>`.
* Switch and switch-case rules use `AT LINE <line>` when line information exists.
* Return value rules never use `IF $!`.
* A method must not get both a generic `METHOD_EXIT` rule and a `RETURN` value rule at the same `AT EXIT` join point.
* Successful condition evaluation must not be logged as `BRANCH_TAKEN` when the branch was not actually taken.
* Unresolved type reference diagnostics must be useful, summarized, and must not flood the console with thousands of multiline warnings.
* Maven and Gradle adapters must keep delegating to the shared generation runner.

## Environment Guard

The uploaded repository snapshot declares Java 17 and Gradle 9.4.0 in several files, while the project-level operating constraint for this task is Gradle 9.1 and JDK 17.

Do not silently mix Gradle baselines.

Before implementing BTM fixes:

1. Inspect:

```bash
git status --short
cat gradle/wrapper/gradle-wrapper.properties
cat AGENTS.md
cat README.md | grep -n "Gradle" || true
cat QUALITY.md
```

2. If the active repository baseline must be Gradle 9.1, align the wrapper and documentation in a separate baseline slice before BTM generator work.
3. If the repository intentionally moved to Gradle 9.4.0, report the mismatch and continue only after the project owner explicitly accepts that baseline.
4. Do not mix Gradle baseline changes with BTM rule-generation fixes.

Java must stay on JDK 17 for this workflow.

## Evidence from the Latest Run

The supplied artifacts were:

* `forensics.btm`
* `log.log`
* `forensics_tracing.zip`

Observed output from `forensics.btm`:

```text
BTM file size: about 38 MB
BTM line count: 893,204
RULE blocks: 81,194
ENDRULE blocks: 81,194
Malformed block count from header/end balance: 0
helper(). occurrences: 0
ENABLE_LOG occurrences: 0
```

Generated rule types:

```text
METHOD_ENTER: 15,496
METHOD_EXIT:  15,496
RETURN:       12,874
IF_TRUE:      14,429
IF_FALSE:     14,429
THROW:         3,790
SWITCH:          909
SWITCH_CASE:   3,771
```

The Maven run itself succeeded:

```text
Generated 81194 rules -> D:\Projects\wildfly\target\forensics\forensics.btm
Generated 81194 BTM rules.
BUILD SUCCESS
Total time: 13.942 s
Finished at: 2026-05-03T15:06:20+02:00
```

This means generation now scales far better than before. The remaining problems are semantic correctness and diagnostic quality.

## Current Defects

### Defect 1 — Rule display names still duplicate the method name

Observed count:

```text
Duplicated class#method#method style rule titles: 75,932
```

Example:

```btm
RULE 2a448d3bed00317092cb992f4f671a67 : enter org.jboss.as.appclient.component.ApplicationClientComponentDescription#create#create
```

Source cause to inspect:

```text
src/main/java/de/burger/forensics/plugin/btmgen/internal/BytemanRuleRenderAdapter.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/MethodEnterRuleStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/MethodExitRuleStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/ReturnRuleStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/ThrowRuleStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/AbstractIfRuleStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/SwitchRuleStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/SwitchCaseRuleStrategy.java
```

Current likely cause:

* `BytemanRuleRenderAdapter` sets `displayName` to `className + "#" + methodName`.
* Several render strategies then render `%s#%s` with `displayName` and `methodName`.
* This produces `class#method#method`.

Required behavior:

```btm
RULE <id> : enter org.example.Foo#bar
```

not:

```btm
RULE <id> : enter org.example.Foo#bar#bar
```

For switch-case rules, the case label must not replace the target method label in the rule title.

Recommended implementation:

* Stop overloading `displayName` as both target label and switch-case label.
* Prefer explicit helpers in `AbstractBytemanStrategy`:

```java
protected static String targetLabel(RuleParams params) {
    return params.className() + "#" + params.methodName();
}

protected static String optionalLabel(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
}
```

* If `RuleParams` must carry a case label, introduce an explicit `eventLabel` field or a small dedicated value object instead of reusing `displayName` ambiguously.
* Keep the smallest safe change if record expansion would create excessive churn.

Regression tests:

* Add or update renderer tests for every strategy that emits a `RULE` header.
* Assert that no rendered rule header contains `#method#method`.
* Add a switch-case test proving the rule title remains `class#method`, while the `onCase(...)` payload receives the label.

Acceptance check after regeneration:

```bash
grep -E '^RULE .*#([^#[:space:]]+)#\1([[:space:]]|$)' build/forensics/forensics.btm
```

Expected result:

```text
no matches
```

---

### Defect 2 — Switch and switch-case rules still render at method entry

Observed generated output:

```text
SWITCH rules:      909 at AT ENTRY
SWITCH_CASE rules: 3,771 at AT ENTRY
```

Examples:

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

```btm
RULE 24db5c9fbbe73767814c73374dd32a60 : switch-case NAME#parseSocketBindingGroup
CLASS org.jboss.as.appclient.subsystem.parsing.AppClientXml_All
METHOD parseSocketBindingGroup(XMLExtendedStreamReader, Set, ModelNode, List)
HELPER de.burger.forensics.infrastructure.rt.RtTraceHelper
AT ENTRY
IF true
DO
    onCase(org.jboss.as.appclient.subsystem.parsing.AppClientXml_All.class, "parseSocketBindingGroup", "NAME");
ENDRULE
```

Source facts:

* `MethodEventExtractor` already collects switch and switch-case line numbers.
* `BytemanRuleRenderAdapter` already passes `location.line()` into `RuleParams`.
* `SwitchRuleStrategy` and `SwitchCaseRuleStrategy` still hardcode `AT ENTRY`.

Required behavior:

```btm
AT LINE <switch-selector-line>
```

for switch rules, and:

```btm
AT LINE <case-line>
```

for switch-case rules when line data is available.

Recommended implementation:

* Use `requireSourceLine(params, id())` in `SwitchRuleStrategy` and `SwitchCaseRuleStrategy`, same as IF rules.
* Render `AT LINE %d`.
* Keep `IF true` for switch/case hit events.
* Preserve selector metadata for `onSwitch(...)`.
* Preserve case label metadata for `onCase(...)`.

Regression tests:

* `SwitchAndJdbcRuleStrategyTest` must assert `AT LINE <n>` and must assert `doesNotContain("AT ENTRY")` for switch and switch-case rules with source line data.
* Add a negative test for missing source line if the chosen contract is fail-fast.
* Add an integration-style test through `GenerateRulesUseCase` or `GenerateBtmTask` using a source snippet with a switch.

Acceptance checks:

```bash
grep -A8 '^RULE .* : switch ' build/forensics/forensics.btm | grep 'AT ENTRY'
grep -A8 '^RULE .* : switch-case ' build/forensics/forensics.btm | grep 'AT ENTRY'
```

Expected result:

```text
no matches
```

---

### Defect 3 — Boolean return rules still use `IF $!` and lose false return values

Observed generated output:

```text
RETURN rules total: 12,874
RETURN rules with IF true: 11,555
RETURN rules with IF $!: 1,319
```

Problematic pattern:

```btm
RULE ... : return some.Type#isEnabled#isEnabled
CLASS some.Type
METHOD isEnabled()
HELPER de.burger.forensics.infrastructure.rt.RtTraceHelper
AT EXIT
IF $!
DO
    onExit(some.Type.class, "isEnabled", $! );
ENDRULE
```

This loses `false` return values because the rule only fires when `$!` evaluates to `true`.

Source cause to inspect:

```text
src/main/java/de/burger/forensics/domain/strategy/DefaultStrategyFactory.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/ReturnRuleStrategy.java
src/test/java/de/burger/forensics/domain/strategy/DefaultStrategyFactoryTest.java
```

Current test smell:

```java
void usesReturnPlaceholderForBooleanReturnExpressions()
```

This test currently encodes the bug by expecting `$!` as the IF guard.

Required behavior:

```btm
AT EXIT
IF true
DO
    onExit(SomeClass.class, "method", $! );
ENDRULE
```

Recommended implementation:

* `DefaultStrategyFactory.from(..., RuleTemplate.RETURN, ...)` must return `true` for all return rules.
* `ReturnRuleStrategy` should render `IF true` unconditionally for return value capture.
* `$!` belongs only in the `DO` action for non-void return value capture.
* Do not use `$!` as the rule guard.

Regression tests:

* Replace `usesReturnPlaceholderForBooleanReturnExpressions` with a test expecting `true`.
* Add `ReturnRuleStrategyTest` if missing.
* Assert boolean return rules render `IF true` and still pass `$!` to `onExit(...)`.

Acceptance check:

```bash
grep -c '^IF \$!$' build/forensics/forensics.btm
```

Expected result:

```text
0
```

---

### Defect 4 — Generic `METHOD_EXIT` and `RETURN` both emit `onExit(...)` for the same method

Observed generated output:

```text
Unique instrumented methods: 15,496
METHOD_EXIT rules:           15,496
RETURN rules:                12,874
onExit(...) calls total:     28,370
Methods with both RETURN and generic METHOD_EXIT: 12,874
```

Problem:

For methods with return values, the generated file contains two `AT EXIT` rules:

1. A generic exit rule:

```btm
DO
    onExit(SomeClass.class, "method", null);
ENDRULE
```

2. A return-value rule:

```btm
DO
    onExit(SomeClass.class, "method", $! );
ENDRULE
```

Both fire at `AT EXIT`. This creates duplicate method-exit telemetry, one event with the real result and one event with `null`.

Required behavior:

* A method must have at most one generated `onExit(...)` event at `AT EXIT`.
* If a method has a return-value rule, do not add a generic `METHOD_EXIT` rule for that same method.
* For void methods or methods without return-value capture, keep the generic `METHOD_EXIT` rule.

Recommended implementation:

Inspect:

```text
src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java
```

Change `addExitRuleIfMissing(...)` so it also checks whether a return rule exists for the method.

Suggested logic:

```java
boolean hasExit = methodEvents.stream().anyMatch(e -> e.kind() == RuleTemplate.METHOD_EXIT);
boolean hasReturn = methodEvents.stream().anyMatch(e -> e.kind() == RuleTemplate.RETURN);
if (!hasExit && !hasReturn) {
    add generic exit rule
}
```

Regression tests:

* `GenerateRulesUseCaseTest` must prove a method with a `RETURN` event does not also receive a generic `METHOD_EXIT` rule.
* A method without a return event still receives a generic exit rule when `includeEntryExit` is enabled.
* Existing deduplication for multiple return statements must remain intact.

Acceptance check after regeneration:

* `onExit(...)` count must be close to the number of instrumented methods, not `METHOD_EXIT + RETURN`.
* No method should have both a `return` rule and an `exit` rule for the same `CLASS` + `METHOD` pair.

---

### Defect 5 — `eval(...)` currently logs condition evaluation as `BRANCH_TAKEN`

Current runtime helper:

```java
public boolean eval(String ruleId, String expression, BooleanSupplier supplier) {
    try {
        String label = Objects.toString(ruleId, "") + ":" + Objects.toString(expression, "");
        boolean value = supplier.getAsBoolean();
        RtTrace.branch(label, value);
        return value;
    } catch (Exception t) {
        RtTrace.conditionError(ruleId, expression, t);
        return false;
    }
}
```

Problem:

Byteman must evaluate the `IF eval(...)` expression to decide whether the `DO` block runs. Therefore `eval(...)` also runs when the branch is not taken.

Because `RtTrace.branch(...)` emits `RtEvent.BRANCH_TAKEN`, the runtime trace can contain misleading branch-taken events for conditions that only evaluated to false.

Required behavior:

* `eval(...)` must return the boolean value safely.
* `eval(...)` may record condition errors.
* `eval(...)` must not emit `BRANCH_TAKEN` for successful evaluations.
* The actual branch hit must be recorded only by `onBranch(...)`, `onSwitch(...)`, or `onCase(...)` inside the `DO` block.

Recommended implementation:

Option A, preferred for now:

* Remove `RtTrace.branch(label, value)` from `RtTraceHelper.eval(...)`.
* Keep `conditionError(...)` for exceptions.

Option B, only if condition-evaluation telemetry is required:

* Add a new runtime event such as `CONDITION_EVALUATED`.
* Do not reuse `BRANCH_TAKEN` for evaluation events.

Regression tests:

* `RtTraceHelperTest` must prove `eval(...)` returns true/false.
* Add a capture test proving successful `eval(...)` does not emit `BRANCH_TAKEN`.
* Existing `CONDITION_ERROR` behavior must remain.

---

### Defect 6 — Unresolved type diagnostics flood the log and are too noisy

Observed from `log.log`:

```text
Suspicious unresolved type warnings: 2,053
Unique suspicious type names: 543
Console log contains many multiline source fragments.
```

Most frequent suspicious names:

```text
IIOPLogger: 201
MessagingLogger: 77
UndertowLogger: 76
Collections: 75
CommonAttributes: 57
ROOT_LOGGER: 42
Arrays: 38
Constants: 37
WildFlySecurityManager: 27
FileVisitResult: 24
```

BTM IF-expression risk analysis:

```text
IF eval(...) expressions: 28,858
IF eval expressions containing uppercase/simple type-like tokens: about 5,806
```

Examples:

```btm
IF eval("...", "!DeploymentTypeMarker.isType(DeploymentType.EAR, $deploymentUnit)", !DeploymentTypeMarker.isType(DeploymentType.EAR, $deploymentUnit))
```

```btm
IF eval("...", "CommandLineConstants.VERSION.equals($arg) || CommandLineConstants.SHORT_VERSION.equals($arg)", CommandLineConstants.VERSION.equals($arg) || CommandLineConstants.SHORT_VERSION.equals($arg))
```

Problem:

Byteman rule expressions must be resolvable at injection time. Unqualified imported types, enum constants, static fields, and static methods may fail when the generated rule is loaded.

There are two separate issues:

1. Real runtime risk: generated `IF eval(...)` expressions may contain unqualified type references.
2. Diagnostic noise: the current warnings are too verbose and print large multiline source expressions.

Required behavior:

* Do not print thousands of full expressions to the console by default.
* Do not validate non-runtime expressions as if they were Byteman `IF` guards.
* Focus diagnostics on expressions that actually appear in generated Byteman `IF` clauses.
* Summarize warning counts and write detailed diagnostics to a report file.
* Add import-aware qualification or a fail-fast validation mode for unresolved type references in IF expressions.

Recommended implementation slices:

#### 6A — Diagnostic hygiene first

* Introduce an internal diagnostic model, for example:

```java
record ExpressionDiagnostic(
        String typeName,
        String className,
        String methodName,
        int line,
        RuleTemplate template,
        String expressionPreview
) {}
```

* Limit `expressionPreview` to a safe one-line preview, for example 240 characters.
* Emit console summary only:

```text
Suspicious unresolved type references: 2053 occurrences, 543 unique names. Details: build/forensics/scan-profile.json
```

* Do not emit multiline expressions as Gradle/Maven warnings.

#### 6B — Reduce false positives

Only validate expressions that are used as executable Byteman IF conditions:

* Validate `IF_TRUE` and `IF_FALSE` conditions.
* Validate switch selector only if it becomes executable in a Byteman expression.
* Do not validate `RETURN` source expressions if the generated rule uses `IF true`.
* Do not validate `SWITCH_CASE` labels as unresolved type references.
* Do not validate rule titles.

#### 6C — Import-aware qualification

Add an import-aware source context to the JavaParser adapter.

Required support:

* Normal imports:

```java
import org.example.DeploymentTypeMarker;
```

should allow:

```java
DeploymentTypeMarker.isType(...)
```

to become:

```java
org.example.DeploymentTypeMarker.isType(...)
```

* Static imports:

```java
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
```

should allow:

```java
OUTCOME
```

to become:

```java
org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME
```

* Java standard library imports such as `java.util.Collections`, `java.util.Arrays`, `java.nio.file.FileVisitResult` must be resolvable from imports.
* `java.lang` types should not be reported as unresolved by default.

Inspect and extend:

```text
src/main/java/de/burger/forensics/adaptersupport/javaparser/DefaultConditionRenderingStrategy.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/StaticFieldQualifier.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/MethodScanContext.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/MethodEventExtractor.java
```

Regression tests:

* Condition with imported type gets fully qualified.
* Condition with imported enum type gets fully qualified.
* Condition with static import gets fully qualified.
* Local variables and parameters are not treated as unresolved types.
* `java.lang.String`, `Boolean`, `Integer`, etc. are not reported as unresolved.
* Multiline expressions are truncated in diagnostics.

Acceptance target:

* No multiline warning flood in Maven/Gradle output.
* Warning count is summarized.
* Real unresolved IF-expression risks are visible in a report.
* The number of unresolved type warnings should drop materially after import-aware qualification.

---

### Defect 7 — Tests currently protect some wrong behavior

Known test smells in the uploaded source snapshot:

```text
src/test/java/de/burger/forensics/domain/strategy/DefaultStrategyFactoryTest.java
```

contains a test expecting boolean return rules to use `$!` as the condition.

```text
src/test/java/de/burger/forensics/plugin/btmgen/render/impl/SwitchAndJdbcRuleStrategyTest.java
```

contains switch tests that do not assert line-aware rendering.

Required behavior:

* Change tests before or together with implementation.
* Do not keep tests that encode the observed bugs.
* Add regression tests that fail against the current source before the fix.

---

## Required BTM Audit Command

Before and after implementation, run a local audit of the generated BTM output.

Use this temporary command if no repository audit tool exists yet:

```bash
python3 - <<'PY'
from pathlib import Path
import re
from collections import Counter, defaultdict

path = Path('build/forensics/forensics.btm')
text = path.read_text(errors='replace')
blocks = [b for b in re.split(r'(?=^RULE )', text, flags=re.M) if b.startswith('RULE ')]

def kind(block):
    first = block.splitlines()[0]
    match = re.match(r'RULE\s+[^:]+:\s*([A-Za-z0-9_-]+)', first)
    return match.group(1).lower() if match else '<unknown>'

rules = re.findall(r'^RULE\s+(.+)$', text, flags=re.M)
endrules = re.findall(r'^ENDRULE\s*$', text, flags=re.M)
rule_kinds = Counter(kind(block) for block in blocks)
duplicated_titles = [line for line in rules if re.search(r'#([^#\s]+)#\1(?:\s|$)', line)]
return_if_dollar = [b for b in blocks if kind(b) == 'return' and re.search(r'^IF\s+\$!\s*$', b, re.M)]
switch_entry = [b for b in blocks if kind(b) == 'switch' and re.search(r'^AT ENTRY\s*$', b, re.M)]
case_entry = [b for b in blocks if kind(b) == 'switch-case' and re.search(r'^AT ENTRY\s*$', b, re.M)]

print('rules=', len(rules))
print('endrules=', len(endrules))
print('kinds=', dict(rule_kinds))
print('duplicated_titles=', len(duplicated_titles))
print('helper_dot=', text.count('helper().'))
print('enable_log=', text.count('ENABLE_LOG'))
print('return_if_$!=', len(return_if_dollar))
print('switch_at_entry=', len(switch_entry))
print('switch_case_at_entry=', len(case_entry))
PY
```

Final expected values:

```text
rules == endrules
duplicated_titles == 0
helper_dot == 0
enable_log == 0
return_if_$! == 0
switch_at_entry == 0
switch_case_at_entry == 0
```

## Slice Plan

### Slice 0 — Baseline and repository guard

Goal: Establish a safe baseline and detect version drift before modifying code.

Commands:

```bash
git status --short
git diff
git diff --cached
cat gradle/wrapper/gradle-wrapper.properties
cat QUALITY.md
find src/main/java -type f | sort
find src/test/java -type f | sort
```

Then try:

```bash
./gradlew --version --dependency-verification strict --console=plain --stacktrace
./gradlew test --dependency-verification strict --console=plain --stacktrace
```

If the wrapper cannot download Gradle because the environment has no network, report that exact blocker and continue with static/test planning only.

Do not modify production code in this slice.

Expected report:

```text
Slice 0 Result:
- Java baseline:
- Gradle wrapper version:
- Project-required Gradle version:
- Quality gate command from QUALITY.md:
- Current git status:
- Build executable: yes/no
- Blockers:
```

---

### Slice 1 — Add regression coverage for the observed BTM contracts

Goal: Capture the current bugs with tests before fixing behavior.

Files likely affected:

```text
src/test/java/de/burger/forensics/plugin/btmgen/render/impl/SwitchAndJdbcRuleStrategyTest.java
src/test/java/de/burger/forensics/plugin/btmgen/render/impl/ReturnRuleStrategyTest.java
src/test/java/de/burger/forensics/domain/strategy/DefaultStrategyFactoryTest.java
src/test/java/de/burger/forensics/application/service/GenerateRulesUseCaseTest.java
src/test/java/de/burger/forensics/plugin/btmgen/internal/BytemanRuleRenderAdapterTest.java
src/test/java/de/burger/forensics/infrastructure/rt/RtTraceHelperTest.java
```

Add tests proving:

* Rule headers do not duplicate method names.
* Switch renders at `AT LINE`.
* Switch-case renders at `AT LINE`.
* Boolean return rules render `IF true`.
* Return and generic method exit are not both emitted for the same method.
* `eval(...)` does not emit `BRANCH_TAKEN` for successful condition evaluation.

Expected result before fixes:

* The new tests should fail against the current source.

---

### Slice 2 — Normalize rule title rendering

Goal: Remove `class#method#method` and fix switch-case titles.

Files likely affected:

```text
src/main/java/de/burger/forensics/plugin/btmgen/render/spi/AbstractBytemanStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/*RuleStrategy.java
src/test/java/de/burger/forensics/plugin/btmgen/render/impl/*RuleStrategyTest.java
```

Implementation rules:

* One canonical target label: `className#methodName`.
* Event labels such as switch-case labels must be separate from the target label.
* Do not append `#methodName` to an already composed target label.

Target examples:

```btm
RULE <id> : enter org.example.Foo#bar
RULE <id> : if-true org.example.Foo#bar
RULE <id> : switch org.example.Foo#bar
RULE <id> : switch-case org.example.Foo#bar [case A]
```

The exact bracket formatting is less important than not losing the target method and not duplicating the method name.

Verification:

```bash
./gradlew test --tests "de.burger.forensics.plugin.btmgen.render.impl.*" --dependency-verification strict --console=plain --stacktrace
```

---

### Slice 3 — Render switch and switch-case at source lines

Goal: Make switch tracing semantically accurate.

Files likely affected:

```text
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/SwitchRuleStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/SwitchCaseRuleStrategy.java
src/test/java/de/burger/forensics/plugin/btmgen/render/impl/SwitchAndJdbcRuleStrategyTest.java
src/test/java/de/burger/forensics/plugin/btmgen/gradle/GenerateBtmTaskTest.java
```

Implementation rules:

* `SwitchRuleStrategy` must require a valid source line.
* `SwitchCaseRuleStrategy` must require a valid source line.
* Use `AT LINE <line>`.
* No fallback to `AT ENTRY` for these templates unless a dedicated documented compatibility mode is explicitly introduced.

Verification:

```bash
./gradlew test --tests "de.burger.forensics.plugin.btmgen.render.impl.SwitchAndJdbcRuleStrategyTest" --dependency-verification strict --console=plain --stacktrace
./gradlew test --tests "de.burger.forensics.plugin.btmgen.gradle.GenerateBtmTaskTest" --dependency-verification strict --console=plain --stacktrace
```

---

### Slice 4 — Fix return-value tracing and generic exit deduplication

Goal: Preserve false boolean return values and avoid duplicate method-exit events.

Files likely affected:

```text
src/main/java/de/burger/forensics/domain/strategy/DefaultStrategyFactory.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/ReturnRuleStrategy.java
src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java
src/test/java/de/burger/forensics/domain/strategy/DefaultStrategyFactoryTest.java
src/test/java/de/burger/forensics/plugin/btmgen/render/impl/ReturnRuleStrategyTest.java
src/test/java/de/burger/forensics/application/service/GenerateRulesUseCaseTest.java
```

Implementation rules:

* Return rules always use `IF true`.
* Return value capture still uses `$!` in the `DO` block.
* A method with a `RETURN` rule must not also get a generic `METHOD_EXIT` rule.
* Void methods still get generic `METHOD_EXIT` when `includeEntryExit` is enabled.

Verification:

```bash
./gradlew test --tests "de.burger.forensics.domain.strategy.DefaultStrategyFactoryTest" --dependency-verification strict --console=plain --stacktrace
./gradlew test --tests "de.burger.forensics.application.service.GenerateRulesUseCaseTest" --dependency-verification strict --console=plain --stacktrace
./gradlew test --tests "de.burger.forensics.plugin.btmgen.render.impl.ReturnRuleStrategyTest" --dependency-verification strict --console=plain --stacktrace
```

---

### Slice 5 — Fix safe eval telemetry semantics

Goal: Stop recording condition evaluation as branch-taken telemetry.

Files likely affected:

```text
src/main/java/de/burger/forensics/infrastructure/rt/RtTraceHelper.java
src/main/java/de/burger/forensics/infrastructure/rt/RtTrace.java
src/main/java/de/burger/forensics/infrastructure/rt/RtEvent.java
src/test/java/de/burger/forensics/infrastructure/rt/RtTraceHelperTest.java
src/test/java/de/burger/forensics/infrastructure/rt/RtTraceTest.java
```

Preferred implementation:

* Remove successful `RtTrace.branch(...)` emission from `RtTraceHelper.eval(...)`.
* Keep `CONDITION_ERROR` behavior unchanged.
* Leave actual branch-hit events in `onBranch(...)`, `onSwitch(...)`, and `onCase(...)`.

Only introduce a new `CONDITION_EVALUATED` event if the project explicitly needs condition-evaluation telemetry.

Verification:

```bash
./gradlew test --tests "de.burger.forensics.infrastructure.rt.RtTraceHelperTest" --dependency-verification strict --console=plain --stacktrace
./gradlew test --tests "de.burger.forensics.infrastructure.rt.RtTraceTest" --dependency-verification strict --console=plain --stacktrace
```

---

### Slice 6 — Fix unresolved type diagnostics and import-aware expression rendering

Goal: Make generated IF expressions safer and diagnostics usable for large projects.

Files likely affected:

```text
src/main/java/de/burger/forensics/adaptersupport/javaparser/DefaultConditionRenderingStrategy.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/MethodEventExtractor.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/MethodScanContext.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/StaticFieldQualifier.java
src/test/java/de/burger/forensics/adaptersupport/javaparser/DefaultConditionRenderingStrategyTest.java
src/test/java/de/burger/forensics/adaptersupport/javaparser/StaticFieldQualifierTest.java
```

Implementation sequence:

1. Create a source-level import context from `CompilationUnit`.
2. Pass it through `MethodScanContext` or another explicit value object.
3. Fully qualify imported type references in executable conditions.
4. Fully qualify supported static imports.
5. Suppress false positives for `java.lang` and local variable/parameter names.
6. Move detailed diagnostics to the scan profile or a dedicated report file.
7. Print only an aggregate warning summary to Gradle/Maven logs.

Do not attempt a huge symbol-solver rewrite in this slice unless the repository already has reliable symbol-solving support wired and tested.

Verification:

```bash
./gradlew test --tests "de.burger.forensics.adaptersupport.javaparser.*" --dependency-verification strict --console=plain --stacktrace
```

Manual acceptance after regenerating BTM:

* Warning output must not contain thousands of full multiline source expressions.
* Diagnostics must identify exact class, method, line, template, and short expression preview.
* The remaining unresolved references must be intentional and reviewable.

---

### Slice 7 — Maven/Gradle adapter regression guard

Goal: Ensure the shared runner remains the single implementation path.

Files likely affected:

```text
src/main/java/de/burger/forensics/plugin/btmgen/common/BtmGenerationRunner.java
src/main/java/de/burger/forensics/plugin/btmgen/gradle/GenerateBtmTask.java
src/main/java/de/burger/forensics/plugin/btmgen/maven/BtmGenMojo.java
src/main/java/de/burger/forensics/plugin/btmgen/maven/MavenBtmGenParameters.java
src/test/java/de/burger/forensics/plugin/btmgen/BtmGenerationAdapterValidationTest.java
src/test/java/de/burger/forensics/plugin/btmgen/maven/MavenBtmGenParametersTest.java
src/test/java/de/burger/forensics/plugin/btmgen/gradle/GenerateBtmTaskTest.java
```

Regression requirements:

* Maven Mojo must not scan or render directly.
* Gradle task must not duplicate Maven logic.
* Both must build a `BtmGenerationRequest` and call `BtmGenerationRunner`.
* Maven default must not include tests unless `forensics.includeTests=true`.
* Explicit `sourceRoot` still overrides Maven project roots.
* Generated output path remains configurable.

Verification:

```bash
./gradlew test --tests "de.burger.forensics.plugin.btmgen.BtmGenerationAdapterValidationTest" --dependency-verification strict --console=plain --stacktrace
./gradlew test --tests "de.burger.forensics.plugin.btmgen.maven.*" --dependency-verification strict --console=plain --stacktrace
./gradlew test --tests "de.burger.forensics.plugin.btmgen.gradle.GenerateBtmTaskTest" --dependency-verification strict --console=plain --stacktrace
```

---

### Slice 8 — Regenerate BTM and run output audit

Goal: Verify the generated output, not only unit tests.

Generate rules for the target project again.

For a Gradle consumer project:

```bash
./gradlew generateBtmRules --dependency-verification strict --console=plain --stacktrace
```

For the Maven/WildFly run:

```bash
mvn -DskipTests forensics:btmgen
```

Use the actual command configured in the consumer project if different.

Then run the BTM audit command from this workflow against the generated file.

Required final audit result:

```text
rules == endrules
duplicated_titles == 0
helper_dot == 0
enable_log == 0
return_if_$! == 0
switch_at_entry == 0
switch_case_at_entry == 0
```

Also check:

```bash
grep -c '^RULE ' build/forensics/forensics.btm
grep -c '^ENDRULE' build/forensics/forensics.btm
grep -c 'helper().' build/forensics/forensics.btm
grep -c 'ENABLE_LOG' build/forensics/forensics.btm
grep -c '^IF \$!$' build/forensics/forensics.btm
```

---

### Slice 9 — Full quality gate

Goal: Prove repository quality after all fixes.

Run the minimum command first:

```bash
./gradlew test --dependency-verification strict --console=plain --stacktrace
```

Then run the full local quality gate from `QUALITY.md`:

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --dependency-verification strict --console=plain --stacktrace
```

If Gradle plugin implementation, task inputs/outputs, or plugin metadata changed, also run:

```bash
./gradlew validatePlugins --dependency-verification strict --no-daemon --console=plain --stacktrace
```

Do not claim a command passed unless it was executed.

If dependency verification fails, do not disable it. Follow the repository dependency verification process and review metadata changes.

## Final Report Format

At the end, report exactly:

````md
# Final Workflow Result

## Overall Status

Status: PASS | PARTIAL | FAILED

## Fixed Defects

| Defect | Status | Files Changed | Tests |
|---|---|---|---|
| D1 Rule title duplication | PASS/FAILED | ... | ... |
| D2 Switch/case AT LINE | PASS/FAILED | ... | ... |
| D3 RETURN IF $! | PASS/FAILED | ... | ... |
| D4 Duplicate onExit | PASS/FAILED | ... | ... |
| D5 Safe eval telemetry | PASS/FAILED | ... | ... |
| D6 Unresolved diagnostics | PASS/FAILED | ... | ... |
| D7 Adapter regression guard | PASS/FAILED | ... | ... |

## Generated BTM Audit

- RULE count:
- ENDRULE count:
- Duplicated rule titles:
- helper(). occurrences:
- ENABLE_LOG occurrences:
- RETURN IF $! occurrences:
- SWITCH AT ENTRY occurrences:
- SWITCH_CASE AT ENTRY occurrences:
- Methods with duplicate exit telemetry:
- Unresolved type warning count:
- Unique unresolved type names:
- Diagnostic report file:

## Commands Executed

```bash
<command 1>
<command 2>
<command 3>
````

## Quality Gate

* Minimum test command: PASS | FAILED | NOT RUN
* Full local quality gate: PASS | FAILED | NOT RUN
* validatePlugins: PASS | FAILED | NOT RUN | NOT REQUIRED
* Dependency verification strict mode: PASS | FAILED | NOT RUN

## Git Result

* Files changed:
* Files staged:
* Commits created:
* Push performed: yes/no

## Remaining Blockers

* None

or:

* <exact blocker with command, file, and error>

```

## Definition of Done

This workflow is complete only when:

- The generator source, tests, and regenerated BTM output agree.
- Rule display names are no longer duplicated.
- Switch and switch-case rules no longer fire at method entry.
- Boolean `false` return values are captured.
- Generic method exit and return-value tracing no longer duplicate the same method exit.
- `eval(...)` no longer pollutes runtime traces with misleading `BRANCH_TAKEN` events.
- Unresolved type diagnostics are summarized and actionable.
- Maven and Gradle adapters still delegate to the shared runner.
- The documented quality gate passes or exact blockers are reported.

```
