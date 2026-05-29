# Quality Gate

`QUALITY.md` is the repository quality contract for the thin Gradle and Maven gRPC plugin interfaces and the retained legacy migration-audit code.

Use `.\gradlew.bat` on Windows PowerShell and `./gradlew` on Unix-like shells.

## Minimum Verification

```bash
./gradlew test --dependency-verification strict --console=plain --stacktrace
```

This runs the JUnit 5 tests, Gradle and Maven adapter tests, gRPC client tests, and ArchUnit boundary checks.

## Full Local Quality Gate

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --dependency-verification strict --console=plain --stacktrace
```

The tasks are explicit:

- `test` runs the full test suite.
- `jacocoTestReport` generates the JaCoCo XML report.
- `jacocoTestCoverageVerification` verifies configured coverage rules.
- `checkPackageCoverage` verifies per-package line and branch coverage.
- `--dependency-verification strict` verifies resolved artifacts against `gradle/verification-metadata.xml`.

## Plugin Validation

When Gradle plugin metadata, Gradle task inputs, task outputs, or Gradle plugin declarations change, also run:

```bash
./gradlew validatePlugins --dependency-verification strict --console=plain --stacktrace
```

When Maven Mojo metadata or parameters change, verify the Maven descriptor and run the targeted Maven Mojo tests:

```bash
./gradlew generateMavenPluginDescriptor --dependency-verification strict --console=plain --stacktrace
```

## gRPC Contract Validation

The plugin contract is `src/main/proto/forensic_ingestion.proto`.

Contract-sensitive changes must include tests that verify:

- start-session request mapping
- upload envelope mapping
- completion request mapping
- response status handling
- Gradle extension values flowing into the submission model
- Maven Mojo parameters flowing into the submission model

The gRPC tests should use an in-process server unless a task explicitly requires a real server integration test.

## Boundary Rules

The active Gradle and Maven entry points must stay thin adapter layers. Quality checks must protect these rules:

- no local forensic analysis logic wired into active Gradle or Maven plugin execution
- no local persistence or generated analysis package ownership in active build-tool adapters
- no runtime tracing implementation exposed through active build-tool adapters
- Gradle and Maven may differ only in build-tool-specific parameter and lifecycle mapping
- no server-side domain decisions in Gradle task classes or Maven Mojo classes
- no Gradle API dependencies in Maven adapter classes
- no Maven API dependencies in Gradle adapter classes
- no build-tool API dependencies in gRPC client classes
- no concrete logging provider dependency

Retained JavaParser, Byteman, H2 analysis-store, runtime tracing, domain, port, and application classes are migration-audit inventory until their server-side migration is verified. They remain subject to compilation, tests, coverage, and dependency verification while they exist in this repository.

Generated gRPC and protobuf classes are build artifacts and are not treated as hand-written production logic for coverage purposes.

## Dependency Verification

Do not disable dependency verification to make a build pass. Do not use:

```bash
--dependency-verification off
```

If expected artifacts need metadata, update `gradle/verification-metadata.xml` with checksum metadata and review the diff before committing:

```bash
./gradlew --write-verification-metadata sha256 <task-that-resolves-the-failing-configuration>
```

Checksum mismatches must be treated as security-relevant blockers.

## Package Coverage Report

`checkPackageCoverage` reads the JaCoCo XML report and writes:

```text
build/reports/coverage/package-coverage.txt
```

The gate fails when a hand-written package is below 80% line coverage or below 80% branch coverage when branch data exists. Do not lower thresholds to make a task pass.

## Optional SonarCloud Check

If `SONAR_TOKEN` or `sonar.token` is available, contributors may run:

```bash
./gradlew sonar --dependency-verification strict --console=plain --stacktrace
```

If no token is configured locally, skip this step and report that SonarCloud was not executed.

## Failure Policy

- A failing quality gate blocks commits and pushes.
- Do not claim a command passed unless it was actually executed.
- If a command fails, report the command, failing task or test, likely cause, and remaining blocker.
- Fix coverage or architecture failures with targeted tests or implementation changes, not by weakening rules.
