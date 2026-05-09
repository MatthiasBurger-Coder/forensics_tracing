# Forensics Tracing Build Plugins and Runtime Tracing Support

## Quality Gate Badge
[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=MatthiasBurger-Coder_forensics_tracing)](https://sonarcloud.io/summary/new_code?id=MatthiasBurger-Coder_forensics_tracing)

## What this project is

This repository provides:

- a Gradle plugin, `de.burger.forensics.btmgen`, that scans Java source code
- Maven plugin goals, including `forensics:btmgen` and `forensics:btmgen-aggregate`, backed by the same generation core
- generation of Byteman `.btm` rules
- runtime tracing helpers centered around `de.burger.forensics.infrastructure.rt.RtTrace`
- an application-facing tracing facade, `de.burger.forensics.application.tracing.Tracer`
- optional AspectJ-based method logging via `de.burger.forensics.infrastructure.logging.MethodLoggingAspect`

Internally the repository is split into pragmatic hexagonal layers (`domain`, `application`, `adapters`, `plugin`, `infrastructure`). The Gradle tasks and Maven Mojos sit in build-tool adapter packages and delegate to shared build-tool-neutral runners.

## Gradle quickstart

Prerequisites:

- Java 17
- Gradle 9.4.0
- a consumer Java project
- Byteman agent/tooling if you want the generated rules to run inside a JVM

Add the plugin through a composite build:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("../forensics_tracing")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "consumer-project"
```

Apply the plugin and configure the default output:

```kotlin
// build.gradle.kts
plugins {
    java
    id("de.burger.forensics.btmgen")
}

btmGen {
    sourceRoot.set(file("src/main/java"))
    outputFile.set(layout.buildDirectory.file("forensics/forensics.btm").get().asFile)
}
```

Generate rules and inspect the result:

```bash
./gradlew generateBtmRules
cat build/forensics/forensics.btm
```

The Gradle task also writes the static analysis package by default:

```text
build/forensics/forensics.btm
build/forensics/manifest.json
build/forensics/checksums.sha256
build/forensics/analysis-store/
```

Then:

1. Keep the generated file at `build/forensics/forensics.btm`.
2. Enable runtime tracing with `-Dforensics.rt.enabled=true`.
3. Optionally add `-Dforensics.rt.output=logs/trace.json` for file output.
4. Load the generated `.btm` file with your normal Byteman agent or tooling setup.

> Warning: `generateBtmRules` only generates build artifacts. It does not instrument a JVM.
>
> Warning: Byteman loading and runtime tracing are separate steps.

## Maven quickstart

Prerequisites:

- Java 17
- Maven 3.9.x or a compatible Maven runtime
- the plugin artifact installed or published to a Maven repository
- Byteman agent/tooling if you want the generated rules to run inside a JVM

The current project coordinates are:

```text
de.burger.forensics:forensics-tracing:0.0.3-SNAPSHOT
```

For local testing, publish the Gradle-built artifact to your local Maven repository first:

```bash
./gradlew publishToMavenLocal
```

Then invoke the Mojo with the full coordinate:

```bash
mvn de.burger.forensics:forensics-tracing:0.0.3-SNAPSHOT:btmgen
```

For a Maven reactor run from the root project:

```bash
mvn de.burger.forensics:forensics-tracing:0.0.3-SNAPSHOT:btmgen-aggregate
```

Minimal `pom.xml` configuration:

```xml
<plugin>
    <groupId>de.burger.forensics</groupId>
    <artifactId>forensics-tracing</artifactId>
    <version>0.0.3-SNAPSHOT</version>
    <configuration>
        <cacheEnabled>false</cacheEnabled>
        <strictParsing>false</strictParsing>
        <strictConditionValidation>false</strictConditionValidation>
        <outputFile>${project.build.directory}/forensics/generated.btm</outputFile>
    </configuration>
</plugin>
```

The short `mvn forensics:btmgen` form requires Maven plugin prefix resolution for the `de.burger.forensics` group. Use the full coordinate when that prefix metadata is not available in the configured repositories.
The Maven BTM goals write the same default package shape as Gradle under `target/forensics`: the `.btm` file, `manifest.json`, `checksums.sha256`, and `analysis-store/` when `analysisStoreEnabled=true`.

## Step-by-step usage

### Step 1: Add the plugin to a consumer build

Use a composite build so the consumer project resolves this plugin directly from the repository:

```kotlin
pluginManagement {
    includeBuild("../forensics_tracing")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "consumer-project"
```

Run the consumer build on Java 17 and Gradle 9.4.0. The repository intentionally does not target Java 21.

### Step 2: Apply the plugin

Use the verified plugin ID:

```kotlin
plugins {
    java
    id("de.burger.forensics.btmgen")
}
```

Notes:

- `btmGen` is the verified extension name.
- `generateBtmRules` is the verified task name.
- When a Java plugin is present, the plugin automatically attaches its runtime helper artifact to `runtimeOnly` and `testRuntimeOnly`.

### Step 3: Configure the plugin for a normal Java project

Start with the minimal scan-mode setup:

```kotlin
btmGen {
    sourceRoot.set(file("src/main/java"))
    outputFile.set(layout.buildDirectory.file("forensics/forensics.btm").get().asFile)
}
```

This is enough for a normal `src/main/java` project.

### Step 4: Generate rules and analysis metadata

Run:

```bash
./gradlew generateBtmRules
```

The plugin scans Java sources, writes one Byteman script, and prepares the static analysis store artifacts.

> Warning: `generateBtmRules` writes build artifacts only. It does not attach Byteman and does not start tracing on its own.

### Forensics Analysis Store

Gradle `generateBtmRules` and Maven `forensics:btmgen` create a persistent analysis package by default:

- `forensics.btm`: Byteman rules with a comment header containing the analysis identity
- `manifest.json`: machine-readable metadata for the generated package
- `checksums.sha256`: SHA-256 checksums for the BTM file, manifest, and H2 files
- `analysis-store/`: H2 database files containing analysis runs, source files, scan events, methods, BTM rules, and artifact checksums

The store is controlled through:

```kotlin
btmGen {
    analysisStoreEnabled.set(true)
    analysisStoreDirectory.set(file("build/forensics/analysis-store"))
    cleanupPolicy.set("KEEP_ON_SUCCESS")
    projectKey.set("legacy-demo-shop")
    manifestFile.set(file("build/forensics/manifest.json"))
    checksumsFile.set(file("build/forensics/checksums.sha256"))
}
```

The equivalent Maven configuration uses the same parameter names through the `forensics.*` user-property prefix:

```xml
<configuration>
    <analysisStoreEnabled>true</analysisStoreEnabled>
    <analysisStoreDirectory>${project.build.directory}/forensics/analysis-store</analysisStoreDirectory>
    <cleanupPolicy>KEEP_ON_SUCCESS</cleanupPolicy>
    <projectKey>legacy-demo-shop</projectKey>
    <manifestFile>${project.build.directory}/forensics/manifest.json</manifestFile>
    <checksumsFile>${project.build.directory}/forensics/checksums.sha256</checksumsFile>
</configuration>
```

Supported cleanup policies are `DELETE_ON_SUCCESS`, `KEEP_ON_SUCCESS`, `KEEP_ON_FAILURE`, and `KEEP_ALWAYS`.
Set `analysisStoreEnabled=false` to write only the `.btm` file.
Run `./gradlew cleanForensicsAnalysisStore` to delete the generated analysis store, manifest, checksum file, and Joern work directories without wiring that cleanup into the normal `clean` lifecycle.
Run `mvn forensics:clean-analysis` for the equivalent Maven cleanup goal.
gRPC publishing, server upload, replay, and LLM context generation are not implemented by this feature.

### Joern Semantic Enrichment

Joern enrichment is optional and disabled by default. It is not part of the normal `build` lifecycle.
Run the explicit aggregate task when a generated analysis package should be enriched with Joern artifacts and H2 semantic tables:

```bash
./gradlew forensicsAnalyze
```

The aggregate task runs `generateBtmRules`, `analyzeForensicsSemantics`, and `importForensicsSemantics`.
`analyzeForensicsSemantics` fails with a clear message unless `btmGen.joernEnabled=true`.
Joern is invoked as an external CLI; it is not added as a Java dependency.

```kotlin
btmGen {
    joernEnabled.set(true)
    joernExecutable.set(file("/opt/joern/joern"))
    joernParseExecutable.set(file("/opt/joern/joern-parse"))
    joernSliceExecutable.set(file("/opt/joern/joern-slice"))
    joernWorkspaceDirectory.set(file("build/forensics/joern/workspace"))
    joernOutputDirectory.set(file("build/forensics/joern"))
    joernTimeoutSeconds.set(300)
    joernFailOnError.set(true)
}
```

Maven exposes the same semantic enrichment flow:

```bash
mvn forensics:analyze
mvn forensics:analyze-aggregate
```

`forensics:analyze` runs module-local BTM generation plus Joern enrichment.
`forensics:analyze-aggregate` runs the same flow over Maven reactor source roots.
For an already generated package, use `forensics:analyze-semantics` followed by `forensics:import-semantics`.

```xml
<configuration>
    <joernEnabled>true</joernEnabled>
    <joernExecutable>/opt/joern/joern</joernExecutable>
    <joernParseExecutable>/opt/joern/joern-parse</joernParseExecutable>
    <joernSliceExecutable>/opt/joern/joern-slice</joernSliceExecutable>
    <joernWorkspaceDirectory>${project.build.directory}/forensics/joern/workspace</joernWorkspaceDirectory>
    <joernOutputDirectory>${project.build.directory}/forensics/joern</joernOutputDirectory>
    <joernTimeoutSeconds>300</joernTimeoutSeconds>
    <joernFailOnError>true</joernFailOnError>
</configuration>
```

When enabled, the package additionally contains Joern artifacts under `build/forensics/joern` for Gradle or `target/forensics/joern` for Maven, including `cpg.bin`, `callgraph.json`, `controlflow.json`, `dataflow.json`, and `slices.json`.
The H2 store receives `joern_import_run`, graph, relation, data-flow, and `semantic_anchor` rows, and the manifest/checksum files include the Joern artifacts.

### Step 5: Inspect the generated file

Inspect the output:

```bash
cat build/forensics/forensics.btm
```

The default output location is `build/forensics/forensics.btm`. By default the file starts with `# Generated Byteman rules`, includes analysis identity comments, omits a timestamp, and then contains rendered `RULE` blocks.

### Step 6: Enable runtime tracing

Enable runtime tracing separately:

```bash
-Dforensics.rt.enabled=true
```

Optional file output:

```bash
-Dforensics.rt.output=logs/trace.json
```

> Warning: Runtime tracing is opt-in. Generated rules can call the helper, but no runtime events are emitted until tracing is enabled.

### Step 7: Load the generated `.btm` file with Byteman

This repository does not provide one canonical startup command for every target JVM. Load the generated file through your normal Byteman mechanism, such as your existing agent startup flags, container startup settings, or test harness integration.

> Warning: Byteman must be attached or loaded separately. Rule generation alone is not instrumentation.

## Monorepo usage

Use the plugin on a root project when you want one aggregated `.btm` file for several Java subprojects:

```kotlin
plugins {
    id("de.burger.forensics.btmgen")
}

btmGen {
    scanSubprojects.set(true)
    outputFile.set(layout.buildDirectory.file("forensics/all-modules.btm").get().asFile)
}
```

Verified monorepo behavior:

- the root project does not need its own `src/main/java`
- Java subprojects are discovered through their `main` `SourceSet`
- non-Java subprojects are ignored
- empty Java subprojects do not fail the task
- custom `sourceSets.main.java.srcDirs(...)` directories are supported
- duplicate roots are de-duplicated before scanning
- explicit `sourceRoots` are combined with `scanSubprojects=true`
- included builds and composite builds are not auto-scanned

Add external or included-build roots explicitly:

```kotlin
btmGen {
    scanSubprojects.set(true)
    sourceRoots.from(
        file("../included-build/some-module/src/main/java"),
        file("legacy-module/src/main/java")
    )
}
```

> Warning: `scanSubprojects=true` scans Gradle subprojects of the current build only. It does not auto-scan included builds or composite builds.

## Maven-based repositories

Prefer the Maven Mojo for Maven projects:

```bash
mvn de.burger.forensics:forensics-tracing:0.0.3-SNAPSHOT:btmgen
```

Use the aggregate goal from a reactor root when you want one coherent analysis package for several Maven modules:

```bash
mvn de.burger.forensics:forensics-tracing:0.0.3-SNAPSHOT:btmgen-aggregate
```

Verified Maven reactor behavior:

- a root project with `pom` packaging is a valid aggregation context
- modules without existing source roots are ignored safely
- compile source roots are collected from all reactor modules
- `includeTests=true` adds Maven test compile source roots
- duplicate roots are de-duplicated deterministically
- `sourceRoot` or `sourceRoots` can be used to override automatic project or reactor root discovery

For Gradle sidecar builds that scan an external Maven-style source tree, keep `scanSubprojects=false` and pass discovered `src/main/java` directories explicitly through `btmGen.sourceRoots`. `scanSubprojects=true` only applies to Gradle subprojects in the current build.

## Minimal verification checklist

Linux, macOS, or Git Bash:

```bash
./gradlew tasks --group verification
./gradlew generateBtmRules
test -f build/forensics/forensics.btm
grep "RULE" build/forensics/forensics.btm
```

Windows PowerShell:

```powershell
.\gradlew.bat generateBtmRules
Test-Path .\build\forensics\forensics.btm
Select-String -Path .\build\forensics\forensics.btm -Pattern "RULE"
```

## Release 1.0.0 readiness

Release-relevant behavior for this repository:

- Gradle 9.4.0 is the baseline.
- Java 17 is the baseline.
- Default generated `.btm` output is deterministic.
- `includeTimestampHeader=true` disables Gradle task output caching intentionally.
- `analysisStoreEnabled=true` writes local H2 analysis state and disables Gradle task output caching for that task execution.
- the built-in strategy registry is configuration-cache safe
- custom `StrategyRegistry` instances are supported for normal builds, but they are not guaranteed to restore safely when Gradle reuses the configuration cache
- monorepo support covers Gradle subprojects in the current build
- composite-build or included-build source trees must be supplied explicitly through `sourceRoots`

> Warning: Timestamp output is intentionally non-cacheable because it makes the generated file time-dependent.

## Configuration reference

The verified configuration split is:

- Extension `btmGen`
  - `sourceRoot`
  - `sourceRoots`
  - `scanSubprojects`
  - `includeTimestampHeader`
  - `outputFile`
  - `helperFqn`
  - `includes`
  - `excludes`
  - `includeTests`
  - `minBranchesPerMethod`
  - `strictConditionValidation`
  - `analysisStoreEnabled`
  - `analysisStoreDirectory`
  - `cleanupPolicy`
  - `projectKey`
  - `manifestFile`
  - `checksumsFile`
- Task `generateBtmRules`
  - `includeEntryExit`
  - `minBranchesPerMethod`
  - `strictConditionValidation`
  - single-template inputs such as `templateId`, `className`, `methodName`, `methodDesc`

`minBranchesPerMethod` exists on both the extension and the task. The plugin copies the extension value into the task as a convention, and the task can override it explicitly.

Verified Kotlin DSL example:

```kotlin
btmGen {
    sourceRoot.set(file("src/main/java"))
    sourceRoots.from(file("shared-generated/java"))
    outputFile.set(file("build/forensics/forensics.btm"))
    analysisStoreEnabled.set(true)
    analysisStoreDirectory.set(file("build/forensics/analysis-store"))
    cleanupPolicy.set("KEEP_ON_SUCCESS")
    projectKey.set("consumer-project")
    manifestFile.set(file("build/forensics/manifest.json"))
    checksumsFile.set(file("build/forensics/checksums.sha256"))
    helperFqn.set("de.burger.forensics.infrastructure.rt.RtTraceHelper")
    includes.set("com.example,org.acme")
    excludes.set("com.example.generated")
    includeTests.set(false)
    minBranchesPerMethod.set(2)
    strictConditionValidation.set(false)
}

tasks.named<de.burger.forensics.plugin.btmgen.gradle.GenerateBtmTask>("generateBtmRules") {
    includeEntryExit.set(true)
    minBranchesPerMethod.set(2)
}
```

### Maven goal parameters

The Maven goals are:

```text
forensics:btmgen
forensics:btmgen-aggregate
forensics:analyze-semantics
forensics:import-semantics
forensics:analyze
forensics:analyze-aggregate
forensics:clean-analysis
```

Supported Maven parameters use the `forensics.*` user-property prefix:

- `sourceRoot`
- `sourceRoots`
- `outputFile`
- `cacheEnabled`
- `cacheBackend`
- `cacheDatabaseFile`
- `analysisStoreEnabled`
- `analysisStoreDirectory`
- `cleanupPolicy`
- `projectKey`
- `pluginVersion`
- `manifestFile`
- `checksumsFile`
- `profilingEnabled`
- `profileReportFile`
- `strictParsing`
- `strictConditionValidation`
- `dependencyAwareInvalidation`
- `includePackages`
- `excludePackages`
- `includeTests`
- `helperFqn`
- `includeEntryExit`
- `minBranchesPerMethod`
- `includeTimestampHeader`
- `joernEnabled`
- `joernExecutable`
- `joernParseExecutable`
- `joernSliceExecutable`
- `joernWorkspaceDirectory`
- `joernOutputDirectory`
- `joernTimeoutSeconds`
- `joernFailOnError`

Maven module-local goals use the current project's compile source roots by default.
Maven aggregate goals use `MavenSession.getProjects()` to collect reactor source roots.
A configured `sourceRoot` or non-empty `sourceRoots` list overrides automatic Maven project or reactor root discovery.
`includeTests=true` adds Maven test compile roots to the selected source roots.
The generated package can contain the `.btm` file, Analysis Store, manifest, checksums, and optional Joern artifacts, matching the Gradle artifact model.

The Maven adapter must stay thin. It must not call `GenerateBtmTask` and must not duplicate JavaParser scanning or Byteman rendering logic.

Property behavior:

- `sourceRoot`
  - default: `src/main/java`
  - legacy single-root alias kept for backward compatibility
  - missing directories are ignored instead of failing the task
- `sourceRoots`
  - additional explicit scan roots
  - combined with `sourceRoot` and auto-discovered Gradle `SourceSet` or Maven compile roots
  - duplicate roots are de-duplicated before scanning
  - use this for legacy folders, external folders, and included/composite build sources
- `scanSubprojects`
  - default: `false`
  - scans the `main` `SourceSet` of Java subprojects in the current Gradle build
  - non-Java subprojects are ignored
  - empty Java subprojects are ignored safely
  - the root project does not need its own `src/main/java`
  - included or composite builds are not auto-discovered through this flag
- `includeTimestampHeader`
  - default: `false`
  - when `false`, the generated header stays deterministic and the task can participate in the build cache
  - when `true`, the writer adds `# Timestamp: ...` and `GenerateBtmTask` explicitly opts out of task output caching
- `analysisStoreEnabled`
  - default for Gradle and Maven: `true`
  - when `true`, the build-tool connector writes `manifest.json`, `checksums.sha256`, and an H2 analysis store next to the `.btm` file
  - when `false`, output stays limited to the `.btm` file and can use caching when other non-cacheable options are disabled
- `cleanupPolicy`
  - default: `KEEP_ON_SUCCESS`
  - controls whether the H2 analysis store directory is retained after task execution
- `joernEnabled`
  - default: `false`
  - enables explicit Joern semantic enrichment tasks; Joern still does not run during normal `build`
- `joernExecutable`, `joernParseExecutable`, `joernSliceExecutable`
  - defaults: `joern`, `joern-parse`, `joern-slice`
  - external CLI executables used by `analyzeForensicsSemantics`
- `joernWorkspaceDirectory`
  - default: `build/forensics/joern/workspace`
- `joernOutputDirectory`
  - default: `build/forensics/joern`
- `joernTimeoutSeconds`
  - default: `300`
- `joernFailOnError`
  - default: `true`
  - when `false`, failed Joern commands are tolerated and available artifacts are parsed
- `outputFile`
  - default: `build/forensics/forensics.btm`
  - the task writes exactly one `.btm` file there by default
- `helperFqn`
  - default: `de.burger.forensics.infrastructure.rt.RtTraceHelper`
  - blank values normalize back to that default
- `includes`
  - Gradle extension and Maven `includePackages`
  - comma-separated fully qualified package or class prefixes
  - example: `com.example,org.acme`
- `excludes` / `excludePackages`
  - comma-separated fully qualified package or class prefixes to omit after include filtering
- `includeTests`
  - default: `false`
  - when `true`, Gradle adds test `SourceSet` roots and Maven adds test compile source roots
- `includeEntryExit`
  - task-only
  - default: `true`
  - when `false`, scan mode does not add `METHOD_ENTER` and `METHOD_EXIT` rules
- `minBranchesPerMethod`
  - default: `2`
  - methods with fewer than that many branch events (`IF_TRUE`, `IF_FALSE`, `SWITCH`, `SWITCH_CASE`) are filtered out from the final output

## Generate `.btm` rules

Run:

```bash
./gradlew generateBtmRules
```

Verified behavior:

- the default output file is `build/forensics/forensics.btm`
- Gradle also writes `build/forensics/manifest.json`, `build/forensics/checksums.sha256`, and `build/forensics/analysis-store/` by default
- Maven writes the equivalent defaults under `target/forensics/`
- the default file content is deterministic
- the file contains `# Generated Byteman rules`, analysis identity comments, no timestamp line by default, and the rendered rule blocks
- scan mode is the default mode
- the plugin also wires `generateBtmRules` into `build` when a `build` task exists
- `includeTimestampHeader=true` keeps the task functional but disables task output caching

> Warning: The generated `.btm` file is only a Byteman script artifact. It is not active until you load it into a JVM with Byteman tooling.

## Run with Byteman and runtime tracing

The generated output is a Byteman script. To execute the rules:

1. load the generated `.btm` file into a JVM with your normal Byteman mechanism
2. make sure the helper class referenced by the rules is on the runtime classpath
3. enable runtime tracing separately

The default helper is `de.burger.forensics.infrastructure.rt.RtTraceHelper`. That helper forwards events to `RtTrace`.

Enable runtime tracing with either:

- `-Dforensics.rt.enabled=true`
- `FORENSICS_RT_ENABLED=true`

Optional file output:

- `-Dforensics.rt.output=logs/trace.json`

Verified runtime output behavior:

- `RtTrace` emits one JSON line per event to a dedicated JUL console logger
- with the standard JDK `ConsoleHandler`, that is console output and is typically written to stderr rather than stdout
- when `forensics.rt.output` is set, the same JSON lines are also appended to the configured file
- the payload includes timestamp, event name, thread, optional correlation ID, optional span ID, a `details` object, and optional error fields

This repository does not provide one canonical JVM startup command for Byteman. Use your normal Byteman loading mechanism for the target application or test process.

### Use the tracing facade in application code

Use `de.burger.forensics.application.tracing.Tracer` when application code should stay independent from direct `RtTrace` calls.

The current interface exposes:

- `enter(...)`
- `exit(...)`
- `span(...)`
- `branch(...)`
- `setVariable(...)`
- `error(...)`
- `setCorrelationId(...)`
- `newCorrelationId()`

The repository already contains a concrete implementation:

- `de.burger.forensics.infrastructure.rt.RtTracer`

Typical usage:

```java
Tracer tracer = new de.burger.forensics.infrastructure.rt.RtTracer();

String correlationId = tracer.newCorrelationId();
tracer.setCorrelationId(correlationId);

try (AutoCloseable span = tracer.span("checkout")) {
    tracer.enter(OrderService.class, "placeOrder", orderId);
    tracer.branch("discountApplied", true);
    tracer.setVariable("orderTotal", total);
    tracer.exit(OrderService.class, "placeOrder", result);
} catch (Exception ex) {
    tracer.error(ex);
    throw ex;
}
```

The repository also contains `examples/RtTracerAdapter.java`, which shows the same adapter pattern. However, that example still uses the older `var(...)` method name. The current `Tracer` interface uses `setVariable(...)`, so align the example with the interface before copying it into production code.

## Supported generated rule types

The renderer supports these `RuleTemplate` values:

| Rule type | Produced from source scanning | Produced in single-template mode | Runtime callback |
| --- | --- | --- | --- |
| `METHOD_ENTER` | Yes. Also synthesized per method when `includeEntryExit=true` and no explicit enter event exists. | Yes | `onEnter(...)` |
| `METHOD_EXIT` | Yes. Also synthesized per method when `includeEntryExit=true` and no explicit exit event exists. | Yes | `onExit(..., null)` |
| `RETURN` | Yes | Yes | `onExit(..., $!)` |
| `THROW` | Yes | Yes | `onException($^)` |
| `IF_TRUE` | Yes | Yes | `onBranch(..., "IF_TRUE")` with `eval(...)` guard logic |
| `IF_FALSE` | Yes | Yes | `onBranch(..., "IF_FALSE")` with `eval(...)` guard logic |
| `SWITCH` | Yes | Yes | `onSwitch(...)` |
| `SWITCH_CASE` | Yes | Yes | `onCase(...)` |
| `THREAD_LIFECYCLE` | No. `GenerateRulesUseCase` explicitly skips it in scan mode. | Yes | `threadFork(...)` / `threadJoin(...)` |
| `JDBC_EXECUTE` | No. `GenerateRulesUseCase` explicitly skips it in scan mode. | Yes | `ioBegin(...)` / `ioEnd(...)` |

Notes:

- `THREAD_LIFECYCLE` renders fixed rules for `java.lang.Thread.start()` and `java.lang.Thread.join(..)`.
- `JDBC_EXECUTE` renders fixed rules for `java.sql.Statement` execute-style methods.
- The scanner-backed path currently generates `METHOD_ENTER`, `METHOD_EXIT`, `RETURN`, `THROW`, `IF_TRUE`, `IF_FALSE`, `SWITCH`, and `SWITCH_CASE`.

## Architecture and execution flow

The verified build-tool adapter split is:

```text
GenerateBtmTask ─┐
                 ├── BtmGenerationRunner ─── Scanner / UseCase / Renderer / Writer
BtmGenMojo(s) ───┘
```

The shared runner owns scanner, rule-generation, rendering, profiling, cache selection, and file writing orchestration. Build-tool adapters only map Gradle or Maven parameters into `BtmGenerationRequest`, invoke the runner, and translate logging or exceptions.

The verified scan execution flow is:

1. The Gradle task `generateBtmRules` (`GenerateBtmTask`) or Maven BTM goal starts in scan mode by default.
2. The build-tool adapter resolves one or more Java source roots and creates a build-tool-neutral `BtmGenerationRequest`.
3. `JavaParserScanner` scans those roots with JavaParser-based support classes and produces `ScanEvent` instances.
4. `GenerateRulesUseCase` filters scan events by language and optional include/exclude package prefixes, groups them by method, optionally adds synthetic `METHOD_ENTER` and `METHOD_EXIT` rules, and applies the `minBranchesPerMethod` filter.
5. The application layer turns scan events into domain `Rule` objects typed by `RuleTemplate`.
6. `BytemanRuleRenderAdapter` converts each domain rule into `RuleParams`, and `BytemanRuleRenderer` dispatches to the matching render strategy.
7. `BtmFileWriter` writes one `.btm` file containing a deterministic generated header plus all rendered rule blocks, unless an optional timestamp header is explicitly enabled.
8. That `.btm` file is not active by itself. You must load it into a JVM with Byteman tooling.
9. At runtime, generated rules call helper methods on the configured helper class. The default helper is `RtTraceHelper`, which delegates to `RtTrace`.
10. `RtTrace` emits structured runtime events and manages correlation IDs and span state.

`GenerateBtmTask` also has a manual single-template mode. In that mode it skips source scanning entirely and renders one explicit template from task inputs.

Architecture rules under `src/test/java/de/burger/forensics/quality` enforce that:

- `plugin.btmgen.common` does not depend on Gradle or Maven APIs
- `plugin.btmgen.gradle` does not depend on Maven APIs
- `plugin.btmgen.maven` does not depend on Gradle APIs
- `domain`, `application`, and JavaParser scanner packages do not depend on build-tool APIs

## Single-template mode

`GenerateBtmTask` switches from scan mode to manual single-template mode only when all of these task inputs are present:

- `templateId`
- `className`
- `methodName`

`methodDesc` is optional.

Minimal example:

```kotlin
tasks.named<de.burger.forensics.plugin.btmgen.gradle.GenerateBtmTask>("generateBtmRules") {
    templateId.set("METHOD_ENTER")
    className.set("com.example.OrderService")
    methodName.set("placeOrder")
    methodDesc.set("(Ljava/lang/String;)V")
}
```

Notes:

- this mode bypasses Java source scanning
- if `templateId` is blank but still present, the task normalizes it to `METHOD_ENTER`
- `THREAD_LIFECYCLE` and `JDBC_EXECUTE` are primarily useful in this mode because scan mode skips them
- the checked-in renderers for `THREAD_LIFECYCLE` and `JDBC_EXECUTE` emit fixed targets (`java.lang.Thread` and `java.sql.Statement` execute methods), even though the task still requires `className` and `methodName` to enter single-template mode

## Optional AspectJ method logging

`MethodLoggingAspect` is separate from Byteman rule generation and separate from runtime tracing.

Verified behavior:

- it logs method entry, successful return, and thrown exceptions
- it targets public methods matching:
  - `de.burger.forensics..*`
  - `org.example.trace..*`
- it skips methods annotated with `de.burger.forensics.infrastructure.logging.SuppressLogging`
- it writes through the target class logger and also mirrors log lines to a file

Verified file-logging properties:

- `forensics.btmgen.logToFile`
  - default in the aspect implementation: `true`
- `forensics.btmgen.logFile`
  - default in the aspect implementation: `logs/forensics-btmgen.log`

Verified weaving setup in this repository:

- `src/main/resources/META-INF/aop.xml` includes `de.burger.forensics..*`
- the same `aop.xml` excludes `de.burger.forensics.infrastructure.logging..*`
- repository tests run with the AspectJ weaver as a Java agent

About `forensics.aspect.enabled`:

- the repository build sets `forensics.aspect.enabled=true` in test configuration
- the checked-in `MethodLoggingAspect` class does not read that property directly
- treat that flag as part of the surrounding build or weaving setup, not as a property consumed by the aspect implementation itself

## Troubleshooting

- Wrong task name:
  Use `generateBtmRules`. Old names from earlier documentation are stale.

- Wrong extension name:
  Use `btmGen`, not a lowercase or differently spelled variant.

- No useful output because of the wrong source root:
  `GenerateBtmTask` filters source roots that do not exist. If you configured the wrong `sourceRoot` or `sourceRoots`, the task can still create the output file but it may contain only the generated header and no real rules.

- `scanSubprojects` misses code from another build:
  `scanSubprojects` only scans Gradle subprojects of the current build. For included builds, composite builds, or arbitrary external folders, add those directories explicitly with `sourceRoots`.

- `includeTimestampHeader=true` does not reuse the build cache:
  That is expected. Timestamped output is intentionally non-deterministic, so `GenerateBtmTask` disables task output caching for that mode.

- Configuration cache fails after configuring a custom `StrategyRegistry`:
  The built-in registry is configuration-cache safe. Custom registries are supported for normal builds, but they are not guaranteed to restore when the configuration cache is reused. Disable configuration cache for that build or use the built-in registry.

- No runtime trace appears:
  Generating `.btm` files is not enough. You must both load the script with Byteman and enable tracing with `-Dforensics.rt.enabled=true` or `FORENSICS_RT_ENABLED=true`.

- Confusing rule generation with instrumentation:
  `./gradlew generateBtmRules` only writes build artifacts. It does not attach Byteman, instrument a JVM, or start runtime tracing by itself.

- Confusing plugin-generated rules with AspectJ logging:
  `MethodLoggingAspect` is separate from Byteman. You can use one without the other.

- Package filtering matches nothing:
  `includes` is a comma-separated prefix filter over fully qualified class names. If no class name starts with one of those prefixes, scan mode produces no matching rules.

- Condition validation warnings:
  Generation reports suspicious simple type references such as `DeploymentType.EAR` because Byteman may not resolve source imports while loading a `.btm` file. The default is warning-only reporting. Set `strictConditionValidation=true` to fail generation on those findings. Known limitations: the validator cannot reconstruct every source import, debug local-variable table, or runtime classloader lookup, and it treats `$name` / `$1` Byteman variables as already resolved.

- Blank helper FQCN does not stay blank:
  Both `GenerationRequest` and `RuleParams` normalize blank helper values back to `de.burger.forensics.infrastructure.rt.RtTraceHelper`.

- `THREAD_LIFECYCLE` or `JDBC_EXECUTE` do not appear during scanning:
  That is expected. The scan path skips those templates. Use single-template mode for them.

- `includeEntryExit=false` still leaves some rules:
  That flag only suppresses generated `METHOD_ENTER` and `METHOD_EXIT` rules. It does not suppress `IF_*`, `SWITCH*`, `RETURN`, or `THROW`.

## Development and testing notes

Use `./gradlew` on Unix-like shells or `.\gradlew.bat` on Windows PowerShell.

Verified repository commands:

```bash
./gradlew build
./gradlew test
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage
./gradlew publishToMavenLocal
```

Notes:

- Java 17 is intentional for this repository.
- Gradle 9.4.0 is the repository baseline.
- Source code, source comments, test names, and repository documentation are maintained in English.
- the stricter local quality gate from `QUALITY.md` is `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage`
- the repository does not assume or require a Java 21 migration

The test suite in `src/test/java` verifies the behavior described above. In particular:

- `BtmGenPluginTest`
  - plugin ID application
  - extension and task registration
  - default conventions
  - runtime helper attachment to `runtimeClasspath` and `testRuntimeClasspath`
  - monorepo runtime helper attachment to Java subprojects
  - `build` wiring

- `BtmGenPluginFunctionalTest`
  - root-project monorepo scanning without a root `src/main/java`
  - custom `main` source-set directories in subprojects
  - `UP-TO-DATE` behavior and reruns when sources change
  - build-cache and configuration-cache coverage

- `GenerateBtmTaskTest`
  - scan mode
  - single-template mode
  - multi-root and subproject scanning
  - missing explicit roots and missing legacy `sourceRoot`
  - subproject `SourceSet` discovery and custom `sourceSets.main.java.srcDirs(...)`
  - helper FQCN normalization
  - duplicate root deduplication and duplicate `RULE` header deduplication
  - `THREAD_LIFECYCLE` and `JDBC_EXECUTE` manual rendering paths

- `BtmGenMojoTest`
  - Maven parameter mapping into the shared runner
  - explicit `sourceRoot` and `sourceRoots` handling
  - clear failure behavior for missing roots and unsupported cache backends

- `MavenReactorAggregationTest`
  - Maven reactor root collection
  - root `pom` aggregation behavior
  - deterministic handling of compile and test source roots

- `MavenAnalysisStoreParityTest`
  - Maven Analysis Store, manifest, and checksum artifact generation

- `MavenJoernConfigurationParityTest`
  - Maven Joern configuration mapping
  - disabled Joern failure behavior

- `BuildToolConnectorParityTest`
  - shared connector capability catalog coverage
  - mandatory Gradle and Maven capability parity

- `BtmGenerationAdapterValidationTest`
  - deterministic direct runner output
  - byte-identical output from the direct runner, Gradle task, and Maven Mojo for the same fixture

- `IfRuleStrategyTest`
  - `IF_TRUE` and `IF_FALSE` rendering at source lines
  - `eval(...)` expression generation
  - placeholder stripping and static field qualification

- `GenerationRequestTest`
  - blank helper normalization
  - null handling
  - immutable copies of optional collections

- `QUALITY.md`
  - explains the repository quality gate
  - documents `checkPackageCoverage`
  - points to the SOLID-oriented and architecture-oriented test suites
