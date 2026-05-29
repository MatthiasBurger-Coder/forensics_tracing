# Forensics Tracing Build Tool gRPC Plugins

## Quality Gate Badge

[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=MatthiasBurger-Coder_forensics_tracing)](https://sonarcloud.io/summary/new_code?id=MatthiasBurger-Coder_forensics_tracing)

## What This Project Is

The active Gradle and Maven plugin interfaces submit build context to the Forensics Analytics server over gRPC.

The repository also temporarily retains legacy local analysis code that has not been proven to exist in the server project yet: JavaParser scanning, Byteman rendering, H2 analysis storage, runtime tracing helpers, domain models, ports, and older application use cases. Treat that code as migration-audit inventory. The Gradle and Maven entry points must not wire it back into active plugin behavior unless a future task explicitly reverses the gRPC boundary.

The Gradle plugin ID remains:

```text
de.burger.forensics.btmgen
```

The old ID is kept only so existing Gradle builds can continue applying the plugin while the implementation boundary changes.

The Maven goal prefix is:

```text
forensics
```

## Current Boundary

The build-tool plugins own:

- Gradle extension configuration
- Gradle task registration
- Maven Mojo parameter mapping
- build-tool default value mapping
- gRPC channel creation
- ingestion session start, upload, completion, and failure handling
- a small build-context diagnostic payload

The server owns:

- repository or source analysis
- forensic domain decisions
- generated artifacts
- semantic enrichment
- storage and indexing
- runtime or replay behavior
- reporting and downstream analytics

The restored legacy packages are present only so migration completeness can be checked against the server repository. They are not the public Gradle or Maven execution path.

## Gradle Quickstart

Use Java 17 and Gradle 9.4.0.

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("../forensics_tracing")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("de.burger.forensics.btmgen")
}

forensicsTracing {
    serverHost.set("localhost")
    serverPort.set(6565)
    plaintext.set(true)

    projectId.set("checkout-service")
    repositoryUrl.set("https://example.invalid/acme/checkout-service.git")
    branchName.set("main")
    commitHash.set("0123456789abcdef")
    buildId.set("local-dev-1")
}
```

Submit the analysis request:

```powershell
.\gradlew.bat submitForensicsAnalysis
```

The aggregate task name `forensicsAnalyze` is also available and depends on `submitForensicsAnalysis`.

## Maven Quickstart

Use Java 17 and configure the Maven plugin in the consuming build:

```xml
<plugin>
    <groupId>de.burger.forensics</groupId>
    <artifactId>forensics-tracing</artifactId>
    <version>0.0.3-SNAPSHOT</version>
    <configuration>
        <serverHost>localhost</serverHost>
        <serverPort>6565</serverPort>
        <plaintext>true</plaintext>
        <projectId>checkout-service</projectId>
        <repositoryUrl>https://example.invalid/acme/checkout-service.git</repositoryUrl>
        <branchName>main</branchName>
        <commitHash>0123456789abcdef</commitHash>
        <buildId>local-dev-1</buildId>
    </configuration>
</plugin>
```

Submit the analysis request:

```powershell
mvn forensics:submit-analysis
```

The previous Maven goals are retained as thin gRPC submission aliases so existing builds can keep invoking the plugin while the analysis implementation lives on the server. These goal names do not generate BTM files, run semantic analysis, import semantic data, or perform local analysis work in this repository:

```text
forensics:btmgen
forensics:btmgen-aggregate
forensics:analyze
forensics:analyze-aggregate
forensics:analyze-semantics
forensics:import-semantics
```

`forensics:clean-analysis` is intentionally a local no-op because the active build-tool interface no longer owns local analysis artifacts or an analysis store.

## Configuration Reference

Extension name:

```text
forensicsTracing
```

Tasks:

```text
submitForensicsAnalysis
forensicsAnalyze
```

Maven goal prefix:

```text
forensics
```

Preferred Maven goal:

```text
submit-analysis
```

Supported Gradle extension properties and submitted task inputs:

| Property | Gradle default | Purpose |
| --- | --- | --- |
| `serverHost` | `localhost` | gRPC server host |
| `serverPort` | `6565` | gRPC server port |
| `plaintext` | `true` | Use plaintext transport instead of TLS |
| `deadlineSeconds` | `30` | Per-call gRPC deadline |
| `schemaVersion` | `1` | Ingestion schema version sent to the server |
| `projectId` | Gradle project name | Server-side project identifier |
| `repositoryUrl` | `UNKNOWN` | Repository URL passed to the server |
| `branchName` | `UNKNOWN` | Branch name passed to the server |
| `commitHash` | `UNKNOWN` | Commit hash passed to the server |
| `buildId` | Gradle project name and version | Build identifier passed to the server |
| `scanTimestamp` | `1970-01-01T00:00:00Z` | Stable timestamp value for deterministic local tests |
| `moduleName` | Gradle project name | Module name for the submitted build context |
| `modulePath` | Gradle project path | Module path for the submitted build context |
| `pluginVersion` | Gradle project version | Plugin version sent to the server task input |

Supported Maven configuration parameters:

| Parameter | Maven default | Purpose |
| --- | --- | --- |
| `serverHost` | `localhost` | gRPC server host |
| `serverPort` | `6565` | gRPC server port |
| `plaintext` | `true` | Use plaintext transport instead of TLS |
| `deadlineSeconds` | `30` | Per-call gRPC deadline |
| `schemaVersion` | `1` | Ingestion schema version sent to the server |
| `projectId` | Maven group ID and artifact ID | Server-side project identifier |
| `repositoryUrl` | `UNKNOWN` | Repository URL passed to the server |
| `branchName` | `UNKNOWN` | Branch name passed to the server |
| `commitHash` | `UNKNOWN` | Commit hash passed to the server |
| `buildId` | Maven artifact ID and version | Build identifier passed to the server |
| `scanTimestamp` | `1970-01-01T00:00:00Z` | Stable timestamp value for deterministic local tests |
| `moduleName` | Maven artifact ID | Module name for the submitted build context |
| `modulePath` | Maven project directory | Module path for the submitted build context |
| `pluginVersion` | Maven project version | Plugin version sent to the server |

`UNKNOWN` is a valid local default so the task can be inspected and tested without repository metadata. Production builds should configure real values so the server can identify the requested analysis.

## gRPC Contract

The client contract is checked in under:

```text
src/main/proto/forensic_ingestion.proto
```

The plugin calls the server service:

```text
ForensicIngestionService
```

Request flow:

1. `StartAnalysisSession`
2. client-streaming `UploadAnalysisData`
3. `CompleteAnalysisSession`

If the server returns a non-success status for any step, the Gradle task or Maven Mojo fails with a descriptive exception.

## Development

Use `.\gradlew.bat` on Windows PowerShell and `./gradlew` on Unix-like shells.

Common commands:

```powershell
.\gradlew.bat test --dependency-verification strict --console=plain --stacktrace
.\gradlew.bat validatePlugins --dependency-verification strict --console=plain --stacktrace
.\gradlew.bat clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --dependency-verification strict --console=plain --stacktrace
```

The test suite uses an in-process gRPC server for client verification. It does not require a running Forensics Analytics server.

## Legacy Local Analysis Inventory

The legacy local forensic analysis implementation is retained for migration verification only. Do not delete JavaParser scanning, Byteman rendering, H2 analysis storage, runtime tracing helpers, domain models, ports, or application use cases unless their server-side migration has been explicitly verified. Do not expose that legacy implementation through Gradle or Maven plugin work; add or change active analysis behavior in the server project, then expose it through the ingestion contract or another explicit server API.
