# AGENTS.md

## Scope

This repository is a single-module Java 17 project that generates Byteman rules from Java source code and ships Gradle plugin and runtime support.

Primary technologies:

- JDK 17
- Gradle 9.x
- JavaParser
- Byteman
- JUnit 5
- AssertJ
- Mockito
- ArchUnit

Use `.\gradlew.bat` on Windows PowerShell and `./gradlew` on Unix-like shells.

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
- Keep naming aligned with the current codebase.
- Prefer the smallest correct change.
- Do not refactor unrelated areas while fixing a focused issue.
- Do not move business logic into adapters or infrastructure for convenience.
- Do not patch a domain or application bug in Gradle/plugin code if the responsibility belongs deeper in the hexagon.
- Do not weaken valid existing tests to make the build pass.

## Regression-First Workflow

For every bug, defect, incorrect behavior, or exception:

1. Identify the failing behavior and the likely root cause.
2. Determine the correct production layer.
3. Determine the correct test layer.
4. Add or update at least one automated regression test before or while fixing the issue.
5. Make the test fail for the right reason when possible.
6. Implement the smallest correct fix in the responsible layer.
7. Re-run the regression test.
8. Run the surrounding relevant test suite.
9. Check nearby branches, fallbacks, null handling, and alternative paths.
10. Add focused branch-protecting tests when the defect exposed a missed branch.

No bugfix is complete without regression coverage.

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

- Hexagonal architecture guardrails:
  - `src/test/java/de/burger/forensics/quality/HexagonRulesTest.java`
- SOLID-oriented quality tests:
  - `src/test/java/de/burger/forensics/quality/solid/...`
- Package coverage report:
  - `checkPackageCoverage`
  - fails below 80% line coverage per package
  - fails below 80% branch coverage per package when branch data exists

Bugfixes should preserve or improve local coverage, especially around conditionals and fallback paths.

## Common Commands

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

## Change Heuristics For This Repository

- If the bug is about extracted source information from Java code, inspect `adapters` and `adaptersupport` first.
- If the bug is about orchestration, filtering, rule generation decisions, or rule grouping, inspect `application.service`.
- If the bug is about rule text rendering, target `plugin.btmgen.render`.
- If the bug is about Gradle task behavior or extension wiring, target `plugin.btmgen.gradle`.
- If the bug is about runtime trace output, helper methods, or logging side effects, target `infrastructure.rt` or `infrastructure.logging`.

Do not change unrelated rule template behavior when fixing a rule-specific defect unless the bug truly affects shared behavior.

## Before Finishing Any Change

Verify all of the following:

- The fix is in the correct hexagonal layer.
- A regression test was added or updated in the correct test layer.
- Relevant branch and fallback paths were considered.
- The focused tests pass.
- The surrounding relevant tests pass.
- Architecture and coverage expectations remain intact.
