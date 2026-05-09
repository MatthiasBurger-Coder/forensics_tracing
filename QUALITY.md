# Code Quality & SOLID Checks

This repository contains automated checks for SOLID principles implemented as tests under `src/test/java/de/burger/forensics/quality/solid/`.

`QUALITY.md` is the project-specific quality contract for contributors and agents.

Use `.\gradlew.bat` on Windows PowerShell and `./gradlew` on Unix-like shells.

## Minimum Quality Command

Run the documented minimum verification command before broader validation:

```bash
./gradlew test --dependency-verification strict --console=plain --stacktrace
```

This runs the full test suite, including JUnit 5 tests, ArchUnit checks, and the SOLID-oriented quality tests.
Gradle Dependency Verification must remain enabled in strict mode.

## Full Local Quality Gate

The authoritative local quality gate for this repository is:

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --dependency-verification strict --console=plain --stacktrace
```

Each task is listed explicitly for a reason:

- `test` runs the full test suite, including the architecture and SOLID checks.
- `jacocoTestReport` generates the JaCoCo XML report required by downstream coverage checks.
- `jacocoTestCoverageVerification` verifies the configured JaCoCo coverage rules.
- `checkPackageCoverage` verifies per-package line and branch coverage against the repository thresholds.
- `--dependency-verification strict` verifies resolved Gradle artifacts against `gradle/verification-metadata.xml`.

## Gradle Dependency Verification

Gradle Dependency Verification is part of the repository quality gate.

Do not disable dependency verification to make a build pass. Do not use:

```bash
--dependency-verification off
```

If Gradle reports missing verification metadata for expected artifacts, update `gradle/verification-metadata.xml` with the existing checksum-based strategy and review the diff before committing it.

For checksum-only metadata updates, use the task that resolves the failing configuration. Example:

```bash
./gradlew --write-verification-metadata sha256 <task-that-resolves-the-failing-configuration>
```

After updating metadata, rerun the failing command and the full local quality gate with `--dependency-verification strict`.

Checksum mismatches or signature mismatches must be treated as security-relevant blockers and must not be fixed by blindly regenerating metadata.

## Partial Diagnostic Command

This command is useful for general build-health diagnostics:

```bash
./gradlew clean check --dependency-verification strict --console=plain --stacktrace
```

This command is intentionally incomplete for this repository.

`checkPackageCoverage` is not wired into the default Gradle `check` lifecycle, so contributors and agents must still run the full explicit quality gate shown above.

## Package Coverage Report

The `checkPackageCoverage` task parses the JaCoCo XML report, writes a per-package report, and fails when a package is below 80% line coverage or 80% branch coverage when branch data exists.

The report is written to:

```text
build/reports/coverage/package-coverage.txt
```

Example of a passing report:

```text
Package coverage report
Line threshold: 80.00%
Branch threshold: 80.00%
packageName	lineCoverage	branchCoverage	missedLines	missedBranches	totalLines	totalBranches
de.burger.forensics.domain	86.20%	84.10%	0	1	40	20
de.burger.forensics.adapters	91.40%	n/a	2	0	25	0
```

Example of a failing result. The gate would fail with values like these:

```text
Package coverage report
Line threshold: 80.00%
Branch threshold: 80.00%
packageName	lineCoverage	branchCoverage	missedLines	missedBranches	totalLines	totalBranches
de.burger.forensics.domain	72.50%	65.00%	11	7	40	20
```

## Plugin Validation

When Gradle plugin metadata, task inputs, task outputs, or plugin declarations change, also run:

```bash
./gradlew validatePlugins --dependency-verification strict --no-daemon --console=plain --stacktrace
```

## Build Tool Connector Parity Gate

Gradle and Maven connector behavior must remain equivalent for all forensic capabilities.

The quality gate must verify:

```text
- shared request model is build-tool-neutral
- Gradle adapter maps extension/task configuration into the shared request model
- Maven adapter maps Mojo/Reactor configuration into the shared request model
- Gradle and Maven produce equivalent BTM output for the same Java sources
- Gradle and Maven produce equivalent Analysis Store artifacts when enabled
- Gradle and Maven produce equivalent manifest/checksum metadata
- Gradle and Maven expose equivalent Joern configuration when Joern support is available
- Maven reactor aggregation matches Gradle multi-project aggregation semantics
```

Required local verification:

```bash
./gradlew test --tests '*BtmGenerationAdapterValidationTest' --dependency-verification strict --console=plain --stacktrace
./gradlew test --tests '*BuildToolConnectorParityTest' --dependency-verification strict --console=plain --stacktrace
./gradlew test --tests '*MavenReactorAggregationTest' --dependency-verification strict --console=plain --stacktrace
./gradlew test --tests '*HexagonRulesTest' --dependency-verification strict --console=plain --stacktrace
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --dependency-verification strict --console=plain --stacktrace
./gradlew validatePlugins --dependency-verification strict --no-daemon --console=plain --stacktrace
```

If Maven plugin descriptors or Maven integration behavior are changed, verification must also include a Maven fixture run from a generated test project or an integration-test fixture.

Agents must not claim connector parity unless the parity tests were executed or the reason for not executing them is explicitly reported.

## Optional SonarCloud Check

If `SONAR_TOKEN` or `sonar.token` is available, contributors may also run:

```bash
./gradlew sonar --dependency-verification strict --console=plain --stacktrace
```

If the token is not configured locally, skip this step and report that SonarCloud was not executed.

## Failure Policy

- The quality gate fails if any required task fails.
- A failing quality gate blocks commits and pushes.
- Coverage thresholds must not be lowered to make the gate pass.
- If a failure cannot be fixed within the current task scope, the blocker must be documented explicitly.
- Agents must stop and report when the gate fails. Do not proceed silently.

## Notes

- The tests rely on lightweight reflection-based heuristics because no additional dependencies were introduced.
- Dependency verification metadata is committed in `gradle/verification-metadata.xml` and must be updated when expected build dependencies or resolved source artifacts change.
- Gradle plugin modules avoid bundling an SLF4J provider to prevent multiple bindings.
- ArchUnit tests include build-tool adapter boundary checks for the shared BTM generation runner, Gradle adapter, and Maven adapter.
- To tighten the rules, adjust the assertions in the SOLID test suite or extend the support utilities.
