# AGENTS.md

## Scope

This repository is a single-module Java 17 project that generates Byteman rules from Java source code and ships Gradle plugin and runtime support.

Primary technologies:

- JDK 17
- Gradle 9.1
- JavaParser
- Byteman
- JUnit 5
- AssertJ
- Mockito
- ArchUnit
- JaCoCo

Use `.\gradlew.bat` on Windows PowerShell and `./gradlew` on Unix-like shells.

## Authoritative Guidance

This file and `QUALITY.md` are normative for repository changes.

When multiple rules apply:

1. Keep the change in the correct architectural layer.
2. Follow the stricter quality rule.
3. Do not lower quality bars unless explicitly requested.

Never disable quality gates, weaken tests, or relax architecture boundaries just to get a green build.

## Architecture Map

Respect the existing hexagonal structure and keep logic in the correct layer.

- `de.burger.forensics.domain`
  - Pure domain model, rule templates, strategies, invariants, value objects, ports.
  - Must stay free of adapter, Gradle, parser, and runtime tracing implementation details.
- `de.burger.forensics.application`
  - Use cases and orchestration.
  - Coordinates domain logic and ports.
  - Must not depend on adapters or infrastructure.
- `de.burger.forensics.domain.port..`
  - Explicit contracts between core logic and external implementations.
- `de.burger.forensics.adapters`
  - Concrete adapters such as JavaParser-based scanning.
- `de.burger.forensics.adaptersupport`
  - Shared adapter-side parsing and extraction support.
  - Keep it out of domain/application unless responsibility clearly belongs there.
- `de.burger.forensics.infrastructure`
  - Technical runtime and logging details.
- `de.burger.forensics.plugin`
  - Gradle plugin, rule rendering, writer, and plugin-specific integration code.
  - Treat `plugin.btmgen.gradle` as the Gradle-facing adapter layer.
  - Treat `plugin.btmgen.render` and `plugin.btmgen.writer` as plugin-side technical implementation, not domain logic.

## Non-Negotiable Rules

- Keep source code and source code comments in English.
- Preserve JDK 17 compatibility.
- Preserve Gradle 9.1 compatibility.
- Keep naming aligned with the current codebase.
- Prefer the smallest correct change.
- Do not refactor unrelated areas while fixing a focused issue.
- Do not move business logic into adapters, infrastructure, or Gradle/plugin code for convenience.
- Do not patch a domain or application bug in Gradle/plugin code if the responsibility belongs deeper in the hexagon.
- Do not weaken, delete, bypass, or rewrite valid existing tests to make the build pass.
- Do not lower coverage thresholds or disable architecture, SOLID, or coverage checks.
- Do not change dependency versions as a side effect of a focused bugfix unless explicitly requested.
- Do not add new dependencies just to avoid writing focused tests when the current test stack is sufficient.
- Prefer IF-less development: favor strategies, polymorphism, typed dispatch, and explicit objects over control-flow-heavy designs when behavior varies.
- Prefer declarative programming: express intent through composition, transformations, predicates, and explicit data flow instead of mutation-heavy step-by-step control flow where clarity improves.
- Gradle plugin modules must avoid bundling an SLF4J provider to prevent multiple bindings.

## IF-Less Development Rules

Prefer explicit object modeling over control-flow-heavy code. The repository follows an IF-less design preference whenever behavior varies by type, mode, state, or rule category.

### Default Expectation

- Prefer polymorphism, strategies, rule objects, dispatch maps, or typed selectors over growing `if / else` or `switch` chains.
- Model behavioral variants as separate classes with explicit names and focused responsibilities.
- Prefer composition to boolean flags that toggle behavior inside one class.
- Keep decision logic close to the boundary or composition root, then delegate to the selected behavior.
- Represent absence, fallback, and unsupported cases explicitly instead of encoding them through scattered conditionals.

### Typical Preferred Replacements

- Replace `if / else` trees on rule type, node type, or rendering mode with strategy selection.
- Replace boolean mode flags with distinct collaborators, policies, or commands.
- Replace `switch` on enum or string values with enum-backed behavior or a dedicated resolver.
- Replace null-driven flow control with explicit return types, dedicated objects, or clearly named fallback policies.

### Allowed Exceptions

A small conditional is acceptable when it is the simplest correct solution and does not hide varying business behavior, for example:

- guard clauses and fail-fast validation
- null checks at boundaries where external APIs force them
- adapter-side format translation or parser boundary handling
- tiny and local decisions where introducing indirection would reduce clarity
- performance-critical hot paths where a more abstract solution would be unjustified

### Review Rules

When introducing a new conditional, verify all the following:

1. The branch is not actually polymorphic behavior that belongs in a strategy or rule object.
2. The branch does not combine multiple responsibilities in one class.
3. The branch does not use booleans, strings, or enums as hidden mode switches when a typed design would be clearer.
4. The branch remains local, readable, and fully covered by tests.
5. The branch does not leak domain decisions into adapters, Gradle/plugin code, or infrastructure.

If a conditional grows, repeats, or spreads across classes, refactor toward a strategy-based or table-driven design.

## Declarative Programming Preference

Prefer code that describes what should happen rather than how to mutate state step by step, especially for filtering, mapping, grouping, rule selection, and transformation logic.

### Default Expectation

- Prefer named predicates, mappers, selectors, collectors, and transformation steps over mutation-heavy loops that mix multiple concerns.
- Prefer immutable intermediate values or clearly scoped local values over shared mutable state that changes across many steps.
- Prefer table-driven, configuration-driven, or composition-based designs when behavior can be described declaratively.
- Keep side effects at the boundaries; domain and application code should favor pure transformations where practical.
- Prefer constructing new values from inputs over updating existing objects across several conditional branches when this improves clarity and testability.

### Typical Preferred Replacements

- Replace manual filter-map-collect loops with clear declarative pipelines when the intent becomes easier to read.
- Replace ad-hoc accumulation logic with dedicated collectors, aggregators, or domain-specific builder objects.
- Replace repeated inline checks with named predicates or specification-style objects.
- Replace scattered branching plus mutation with a transformation pipeline or resolver that returns the next explicit value.

### Allowed Exceptions

A local imperative solution is acceptable when it is the simplest and most readable correct solution, for example:

- tiny loops with straightforward local state
- performance-sensitive hot paths where extra allocations or abstraction would be unjustified
- sequential parser logic or stateful extraction logic where the execution order itself is the clearest representation
- APIs that require imperative interaction, checked exception handling, or callback-style control
- debugging-heavy sections where a declarative rewrite would reduce traceability

### Review Rules

When writing transformation or collection-processing logic, verify all the following:

1. The intent is visible from the structure of the code, not hidden in the mutable temporary state.
2. Filtering, mapping, grouping, fallback, and side effects are not mixed in one opaque block.
3. Named predicates, mappers, or helper types would not clarify the code.
4. A declarative form would not significantly improve readability, testability, or composability.
5. The chosen style keeps debugging, performance, and maintenance costs reasonable.

Do not force streams, fluent APIs, or other declarative constructs when they make the code harder to understand than a small explicit loop. Favor readability over style fashion.

## Hardened Regression-First Workflow

For every bug, defect, incorrect behavior, semantic mismatch, or exception:

1. Identify the failing behavior and the likely root cause.
2. Determine the correct production layer.
3. Determine the correct test layer.
4. Add or update at least one automated regression test before or while fixing the issue.
5. Make the regression fail for the correct reason whenever possible.
6. Implement the smallest correct fix in the responsible layer.
7. Re-run the regression test.
8. Run the surrounding relevant test suite.
9. Check nearby branches, fallbacks, null handling, negative paths, and alternative paths.
10. Add focused branch-protecting tests when the defect exposed a missed branch.
11. Run the mandatory verification pipeline.
12. Only finish when the relevant tests and quality gates are green.

No bugfix is complete without regression coverage.

## Required Test Strategy By Defect Type

Use the narrowest test that still protects the real responsibility.

### Domain Logic Defects

- Place tests under `src/test/java/de/burger/forensics/domain/...`
- Assert invariants, strategy behavior, value semantics, and edge cases.
- Do not use Gradle/plugin tests as a substitute for missing domain tests.

### Application / Use-Case Orchestration Defects

- Place tests under `src/test/java/de/burger/forensics/application/...`
- Verify orchestration, grouping, filtering, fallback selection, and port interaction.
- Keep adapter and Gradle concerns out of these tests unless the defect is truly cross-boundary.

### Adapter Parsing / Extraction Defects

- Place tests under:
  - `src/test/java/de/burger/forensics/adapters/...`
  - `src/test/java/de/burger/forensics/adaptersupport/...`
- Use focused fixture-based characterization tests.
- Prefer minimal Java source examples that reproduce the issue precisely.

### Plugin Rendering Defects

- Place tests under `src/test/java/de/burger/forensics/plugin/btmgen/render/...`
- Use JUnit 5 regression tests with exact output assertions.
- Assert the generated Byteman rule text exactly, or assert exact relevant fragments when full-text assertion would be too brittle.
- Protect both the failing rendering path and the opposite path where relevant.

### Gradle Task / Plugin Wiring Defects

- Place tests under `src/test/java/de/burger/forensics/plugin/btmgen/gradle/...`
- Use Gradle TestKit where task wiring, extension wiring, generated files, or task failure behavior is involved.
- Verify inputs, outputs, task registration, generated files, and failure conditions.

### Infrastructure / Runtime / Logging Defects

- Place tests under `src/test/java/de/burger/forensics/infrastructure/...`
- Use focused tests around the actual helper, adapter, or runtime behavior.
- Do not substitute a focused infrastructure test with a broad unrelated integration test.

### Architecture Guardrails

- Keep architecture rules under `src/test/java/de/burger/forensics/quality/...`
- Extend ArchUnit tests when a defect exposed architectural drift.

### SOLID Guardrails

- Keep the SOLID-oriented quality suite green under:
  - `src/test/java/de/burger/forensics/quality/solid/...`

## Branch And Semantic Protection Rules

When a defect touches any of the following, branch protection is mandatory:

- `if / else`
- `switch`
- boolean flags
- ternary expressions
- null handling
- fallback logic
- default branches
- filtering predicates
- rule-selection decisions
- rendering conditions

Then the fix must:

1. Protect the triggering branch.
2. Protect the opposite or neighboring branch.
3. Assert semantics, not only execution.
4. Verify fallback/default behavior where applicable.
5. Use exact output assertions for text rendering where the rendered rule text is the real contract.
6. Avoid happy-path-only tests.

A line coverage increase does not prove correctness if branch semantics remain unprotected.

## Test Placement Guide

Place tests where the responsibility belongs.

- Domain logic defects:
  - `src/test/java/de/burger/forensics/domain/...`
- Application/use-case orchestration defects:
  - `src/test/java/de/burger/forensics/application/...`
- Adapter parsing or extraction defects:
  - `src/test/java/de/burger/forensics/adapters/...`
  - `src/test/java/de/burger/forensics/adaptersupport/...`
- Plugin rendering defects:
  - `src/test/java/de/burger/forensics/plugin/btmgen/render/...`
- Gradle task or plugin wiring defects:
  - `src/test/java/de/burger/forensics/plugin/btmgen/gradle/...`
- Infrastructure/runtime/logging defects:
  - `src/test/java/de/burger/forensics/infrastructure/...`
- Architecture guardrails:
  - `src/test/java/de/burger/forensics/quality/...`

When a defect crosses a boundary, prefer the narrowest test that still protects the real integration point.

## Existing Quality Gates

The repository already enforces architectural and coverage constraints. Keep them green.

### Architecture

- Hexagonal architecture guardrails:
  - `src/test/java/de/burger/forensics/quality/HexagonRulesTest.java`

### SOLID

- SOLID-oriented quality tests:
  - `src/test/java/de/burger/forensics/quality/solid/...`

### Coverage

- `checkPackageCoverage`
  - parses the JaCoCo XML report
  - writes a per-package report
  - report path: `build/reports/coverage/package-coverage.txt`
  - fails below 80% line coverage per package
  - fails below 80% branch coverage per package when branch data exists

Bugfixes should preserve or improve local coverage, especially around conditionals, fallback paths, and negative paths.

## Mandatory Verification Commands

For any bugfix, behavioral change, or quality-affecting change, run:

```bash
./gradlew clean test jacocoTestReport checkPackageCoverage
```

Useful focused commands during iteration:

Run the full test suite:

```bash
./gradlew test
```

Run one focused test class:

```bash
./gradlew test --tests de.burger.forensics.plugin.btmgen.render.impl.IfRuleStrategyTest
```

Run coverage report and package coverage gate:

```bash
./gradlew jacocoTestReport checkPackageCoverage
```

Build the project:

```bash
./gradlew build
```

Publish to local Maven:

```bash
./gradlew publishToMavenLocal
```

Do not treat `./gradlew build` as a substitute for the explicit coverage gate unless it also executes the same checks in this repository configuration.

## Change Heuristics For This Repository

* If the bug is about extracted source information from Java code, inspect `adapters` and `adaptersupport` first.
* If the bug is about orchestration, filtering, rule generation decisions, or rule grouping, inspect `application.service`.
* If the bug is about rule text rendering, target `plugin.btmgen.render`.
* If the bug is about Gradle task behavior or extension wiring, target `plugin.btmgen.gradle`.
* If the bug is about runtime trace output, helper methods, or logging side effects, target `infrastructure.rt` or `infrastructure.logging`.

Do not change unrelated rule template behavior when fixing a rule-specific defect unless the bug truly affects shared behavior.

## Prohibited Shortcuts

* Do not change the expected test output to match a buggy implementation unless the specification is truly changed.
* Do not replace exact renderer assertions with loose assertions without a concrete reason.
* Do not move logic across layers to make a test easier.
* Do not hide missing branch protection behind a line coverage increase.
* Do not silence failing quality tests by excluding packages or classes from verification.
* Do not mark a fix as complete when only the directly failing test passes.
* Do not broaden visibility, APIs, or responsibilities without a concrete design reason.

## Before Finishing Any Change

Verify all the following:

* The fix is in the correct hexagonal layer.
* A regression test was added or updated in the correct test layer.
* Relevant primary and opposite branch paths were considered.
* The focused tests pass.
* The surrounding relevant tests pass.
* `./gradlew clean test jacocoTestReport checkPackageCoverage` passes.
* Architecture and SOLID expectations remain intact.
* Coverage expectations remain intact.
* No dependency or version drift was introduced unintentionally.
* New branching was avoided where a strategy-based or typed IF-less design was the better fit, or the conditional was explicitly justified.
* Declarative composition and transformation were preferred over mutation-heavy imperative flow where that improved clarity, or the imperative style was explicitly justified.
* No SLF4J provider was bundled into Gradle plugin modules.
* The completion summary includes the root cause, changed files, and exact tests/commands that were executed.
