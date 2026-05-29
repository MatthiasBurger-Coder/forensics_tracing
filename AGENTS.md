# AGENTS.md

## Purpose

This document defines the mandatory engineering rules for automated agents working on this repository.

The active Gradle and Maven plugin interfaces submit build context to the Forensics Analytics server over gRPC. The repository also retains legacy local analysis code as migration-audit inventory until its server-side migration can be proven.

All source code, comments, JavaDoc, test names, and repository documentation must be written in English.

Use `.\gradlew.bat` on Windows PowerShell and `./gradlew` on Unix-like shells.

## Project Baseline

- Java 17
- Gradle 9.4.0
- JUnit 5
- ArchUnit
- JaCoCo
- SonarQube / SonarCloud quality checks
- Gradle and Maven plugin interfaces
- gRPC client boundary only
- legacy local analysis inventory retained for migration verification

## Core Principle

Prefer the smallest correct change.

The plugins must remain thin adapters. They may configure Gradle tasks, expose Maven Mojos, collect build identity, create gRPC requests, send payloads, and handle server responses. Restored legacy analysis code may remain in the repository for migration verification, but Gradle and Maven entry points must not wire it back into active plugin behavior without an explicit task.

## Mandatory Safety Rules

### Verify Before Touching

Every implementation task must start with a read-only verification phase. Inspect the exact files, Gradle tasks, Gradle extension properties, Maven goals, Maven Mojo parameters, proto definitions, tests, and documentation relevant to the requested change before modifying files.

### Stop And Report

If a required method, class, interface, task, package, file, proto field, RPC method, or documented contract cannot be found exactly as expected, stop and report:

- what was expected
- what was found instead
- which files were inspected
- why continuing would be unsafe

Do not guess missing names, invent task names, or silently substitute similar symbols.

### No Hidden Compatibility Code

Do not add aliases, compatibility wrappers, deprecated bridge methods, fallback task names, or fallback RPC behavior unless explicitly requested and verified.

### No New Active Local Analysis Logic

Do not add new active plugin implementation for:

- source parsing or source analysis
- rule generation or rendering
- semantic enrichment
- persistent analysis stores
- generated analysis packages
- runtime tracing helpers
- server-side domain decisions

If a task requires active plugin behavior for any of these responsibilities, implement it in the server project or stop and report that this repository is the wrong boundary. If a task asks to remove legacy analysis code and its server-side migration cannot be proven, keep or restore the legacy code and report the migration uncertainty.

## Architecture Boundary

Active build-tool boundary packages:

```text
de.burger.forensics.plugin.btmgen.gradle
de.burger.forensics.plugin.btmgen.grpc
de.burger.forensics.plugin.btmgen.maven
```

The package name still contains `btmgen` for plugin ID continuity, but the implementation must behave as a gRPC submission plugin.

Legacy migration-audit packages may also exist under `domain`, `application`, `adapters`, `adaptersupport`, `infrastructure`, and non-build-tool `plugin.btmgen` packages. These packages are not the active Gradle or Maven execution path.

Gradle task classes may depend on Gradle APIs and the local gRPC client boundary.

Maven Mojo classes may depend on Maven APIs and the local gRPC client boundary.

gRPC client classes must not depend on Gradle or Maven APIs.

Generated protobuf and gRPC classes are build artifacts from `src/main/proto/forensic_ingestion.proto`.

## Gradle Plugin Rules

Gradle task classes must:

- declare inputs explicitly
- use Gradle `Property<T>` types where appropriate
- avoid execution work during configuration
- keep task actions small
- delegate network communication to the gRPC client boundary
- fail with useful messages when the server rejects a request

Gradle task classes must not:

- parse project sources for forensic meaning
- render local analysis artifacts
- write server-owned outputs
- store `Project` references in task fields
- use static mutable state for task behavior

## Maven Plugin Rules

Maven Mojo classes must:

- expose only thin goal and parameter mapping
- delegate network communication to the gRPC client boundary
- keep legacy goals as submission aliases only when explicitly required
- fail with useful `MojoExecutionException` messages when submission fails

Maven Mojo classes must not:

- parse project sources for forensic meaning
- render local analysis artifacts
- write server-owned outputs
- call Gradle task classes
- duplicate Gradle task orchestration
- use static mutable state for Mojo behavior

## gRPC Contract Rules

The checked-in contract is:

```text
src/main/proto/forensic_ingestion.proto
```

Before changing request mapping or response handling, verify the proto and relevant tests.

The standard submission flow is:

1. `StartAnalysisSession`
2. client-streaming `UploadAnalysisData`
3. `CompleteAnalysisSession`

If the server contract changes, update the proto, regenerate classes through Gradle, and adjust tests in the same task.

## Testing Rules

Use JUnit 5 and ArchUnit.

Place tests near the affected package:

```text
src/main/java/de/burger/forensics/plugin/btmgen/gradle/**
-> src/test/java/de/burger/forensics/plugin/btmgen/gradle/**

src/main/java/de/burger/forensics/plugin/btmgen/grpc/**
-> src/test/java/de/burger/forensics/plugin/btmgen/grpc/**

src/main/java/de/burger/forensics/plugin/btmgen/maven/**
-> src/test/java/de/burger/forensics/plugin/btmgen/maven/**
```

Architecture-sensitive rules belong under:

```text
src/test/java/de/burger/forensics/quality
```

Use in-process gRPC servers for client tests unless a real server integration test is explicitly requested.

## Quality Gate

The default verification command is:

```bash
./gradlew test --dependency-verification strict --console=plain --stacktrace
```

The full local quality gate is:

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --dependency-verification strict --console=plain --stacktrace
```

When Gradle plugin metadata, Gradle task inputs, Gradle task outputs, or Gradle plugin declarations change, also run:

```bash
./gradlew validatePlugins --dependency-verification strict --console=plain --stacktrace
```

When Maven plugin goal metadata or Mojo parameters change, verify the generated Maven plugin descriptor and targeted Maven Mojo tests:

```bash
./gradlew generateMavenPluginDescriptor --dependency-verification strict --console=plain --stacktrace
```

Do not claim a command passed unless it was executed.

## Documentation Rules

Documentation must stay aligned with source code.

When changing public plugin behavior, inspect and update relevant documentation:

- `README.md`
- `QUALITY.md`
- `AGENTS.md`
- `SECURITY.md`
- workflow or commit guidance files

Do not document local analysis behavior as active Gradle or Maven plugin behavior. When needed, document restored local analysis code as migration-audit inventory only.

## Dependency Rules

Do not add dependencies unless required for the Gradle or Maven gRPC client boundary or for compiling and verifying retained legacy migration-audit code.

Do not add concrete logging providers.

Do not upgrade Java, Gradle, plugins, dependencies, JaCoCo, SonarQube plugin, or publishing plugins unless explicitly requested.

## Final Report Requirements

At the end of every task, report:

1. files changed
2. main changes made
3. tests or verification commands executed
4. commands that failed, if any
5. quality gate result
6. known limitations
7. remaining blockers, if any

If no files were changed, say so explicitly.
