# Project Workflow

This repository contains thin Gradle and Maven plugins that communicate with the Forensics Analytics server over gRPC, plus retained legacy local analysis code used only for migration verification.

## Default Workflow

1. Verify the exact Gradle task, Gradle extension, Maven goal, Maven parameter, proto, or class before editing.
2. Keep the change limited to the Gradle adapter, Maven adapter, or gRPC client boundary.
3. Add or update focused tests for changed behavior.
4. Run the narrowest useful test first.
5. Run `validatePlugins` when Gradle plugin metadata, task inputs, task outputs, or declarations change.
6. Run `generateMavenPluginDescriptor` when Maven goal metadata or Mojo parameters change.
7. Run the full quality gate from `QUALITY.md`.
8. Report changed files, verification commands, failures, limitations, and blockers.

## Read-Only Review Roles

Read-only agents may review:

- repository structure
- Gradle and Maven plugin boundaries
- gRPC request and response mapping
- architecture tests
- documentation consistency
- dependency and security risk
- commit readiness

Read-only agents must not modify files.

## Write-Capable Work

Only one implementation worker should modify a working tree at a time. Each implementation slice must define:

- affected files
- exact change intent
- verification command
- stop condition
- risk summary

## Stop Conditions

Stop and report when:

- the expected proto contract cannot be verified
- a Gradle task, Gradle extension, Maven goal, or Maven parameter name cannot be found exactly
- a server API requirement is unclear
- implementing the task would require wiring local analysis functionality back into the active Gradle or Maven plugin boundary
- verification fails for a reason that cannot be fixed within the current task

## Verification Commands

Use Windows PowerShell commands in this workspace:

```powershell
.\gradlew.bat test --dependency-verification strict --console=plain --stacktrace
.\gradlew.bat validatePlugins --dependency-verification strict --console=plain --stacktrace
.\gradlew.bat clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --dependency-verification strict --console=plain --stacktrace
```
