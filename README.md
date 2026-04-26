# Forensics Tracing Gradle Plugin and Runtime Tracing Support

## Quality Gate Badge
[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=MatthiasBurger-Coder_forensics_tracing)](https://sonarcloud.io/summary/new_code?id=MatthiasBurger-Coder_forensics_tracing)

## What this project is

This repository provides:

- a Gradle plugin, `de.burger.forensics.btmgen`, that scans Java source code
- generation of Byteman `.btm` rules
- runtime tracing helpers centered around `de.burger.forensics.infrastructure.rt.RtTrace`
- an application-facing tracing facade, `de.burger.forensics.application.tracing.Tracer`
- optional AspectJ-based method logging via `de.burger.forensics.infrastructure.logging.MethodLoggingAspect`

Internally the repository is split into pragmatic hexagonal layers (`domain`, `application`, `adapters`, `plugin`, `infrastructure`). The Gradle plugin sits in the plugin adapter layer and drives the application service that generates rules.

## Architecture and execution flow

The verified execution flow is:

1. The Gradle task `generateBtmRules` (`GenerateBtmTask`) starts in scan mode by default.
2. `GenerateBtmTask` resolves one or more Java source roots from its own task inputs and from the `btmGen` extension.
3. `JavaParserScanner` scans those roots with JavaParser-based support classes and produces `ScanEvent` instances.
4. `GenerateRulesUseCase` filters scan events by language and optional package prefixes, groups them by method, optionally adds synthetic `METHOD_ENTER` / `METHOD_EXIT` rules, and applies the `minBranchesPerMethod` filter.
5. The application layer turns scan events into domain `Rule` objects typed by `RuleTemplate`.
6. `BytemanRuleRenderAdapter` converts each domain rule into `RuleParams`, and `BytemanRuleRenderer` dispatches to the matching render strategy.
7. `BtmFileWriter` writes one `.btm` file containing a generated header plus all rendered rule blocks.
8. That `.btm` file is not active by itself. You must load it into a JVM with Byteman tooling.
9. At runtime, generated rules call helper methods on the configured helper class. The default helper is `RtTraceHelper`, which delegates to `RtTrace`.
10. `RtTrace` emits structured runtime events and manages correlation IDs and span state.

`GenerateBtmTask` also has a manual single-template mode. In that mode it skips source scanning entirely and renders one explicit template from task inputs.

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

## Prerequisites

- Java 21
- Gradle 9.1
- a Java project to analyze
- Byteman agent/tooling if you want the generated rules to execute inside a JVM

Important baseline note:

- The plugin project in this repository is compiled and tested with a Java 21 toolchain in `build.gradle.kts`.
- The examples below assume a consumer build running on Java 21 and Gradle 9.1, because that is the target documentation baseline for this README.

## Build this project

Use `./gradlew` on Unix-like shells or `.\gradlew.bat` on Windows PowerShell.

Verified commands for this repository:

```bash
./gradlew build
```

- Compiles the repository
- runs the test suite
- produces the plugin and library artifacts

```bash
./gradlew test
```

- Runs the JUnit 5 test suite
- covers plugin wiring, task behavior, renderer behavior, runtime tracing, and AspectJ logging support

```bash
./gradlew clean test jacocoTestReport checkPackageCoverage
```

- Runs the stricter repository quality gate from `QUALITY.md`
- generates the JaCoCo XML report
- writes the per-package coverage report to `build/reports/coverage/package-coverage.txt`

## Use the plugin in a consumer project

The safest verified local-development path is a composite build. This repository is a Gradle plugin project, so another Gradle build can resolve it directly with `includeBuild(...)`.

Consumer `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../forensics_tracing")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Consumer `build.gradle.kts`:

```kotlin
plugins {
    java
    id("de.burger.forensics.btmgen")
}
```

Practical notes:

- `de.burger.forensics.btmgen` is the verified plugin ID.
- `btmGen` is the verified extension name.
- `generateBtmRules` is the verified task name.
- Applying a Java plugin (`java`, `java-library`, or another plugin based on `JavaPlugin`) is the practical default for projects whose own sources should be scanned.
- For a monorepo root task, the root project itself does not need a Java plugin as long as the scanned subprojects expose Java `main` source sets.
- When a Java plugin is present, `BtmGenPlugin` automatically attaches the plugin runtime artifact to `runtimeOnly` and `testRuntimeOnly`.
- When `scanSubprojects=true` is configured on the project where the plugin is applied, Java subprojects in the same Gradle build also receive that runtime helper on `runtimeOnly` and `testRuntimeOnly`.
- The plugin also wires `generateBtmRules` into `build` when a `build` task exists.

This repository also exposes Gradle publishing tasks, but this README documents the composite-build path first because it is the most direct repository-backed development flow.

## Configure the plugin

The verified configuration split is:

- Extension `btmGen`
  - `sourceRoot`
  - `sourceRoots`
  - `scanSubprojects`
  - `outputFile`
  - `helperFqn`
  - `includes`
  - `minBranchesPerMethod`
- Task `generateBtmRules`
  - `includeEntryExit`
  - `minBranchesPerMethod`
  - single-template inputs such as `templateId`, `className`, `methodName`, `methodDesc`

`minBranchesPerMethod` is important: it exists on both the extension and the task. The plugin copies the extension value into the task as a convention, and the task can override it explicitly.

Verified Kotlin DSL example:

```kotlin
btmGen {
    sourceRoot.set(file("src/main/java"))
    sourceRoots.from(file("shared-generated/java"))
    outputFile.set(file("build/forensics/forensics.btm"))
    helperFqn.set("de.burger.forensics.infrastructure.rt.RtTraceHelper")
    includes.set("com.example,org.acme")
    minBranchesPerMethod.set(2)
}

tasks.named<de.burger.forensics.plugin.btmgen.gradle.GenerateBtmTask>("generateBtmRules") {
    includeEntryExit.set(true)
    minBranchesPerMethod.set(2)
}
```

Property behavior:

- `sourceRoot`
  - Default: `src/main/java`
  - Legacy single-root alias kept for backward compatibility
  - Missing directories are ignored instead of failing the task
- `sourceRoots`
  - Additional explicit scan roots
  - Combined with `sourceRoot` and auto-discovered Gradle `SourceSet` roots
  - Use this for legacy folders, external folders, and included/composite build sources
- `scanSubprojects`
  - Default: `false`
  - Scans the `main` `SourceSet` of Java subprojects in the current Gradle build
  - Non-Java subprojects are ignored
  - The root project does not need its own `src/main/java`
  - Composite or included builds are not auto-discovered through this flag; add those directories with `sourceRoots`
- `outputFile`
  - Default: `build/forensics/forensics.btm`
  - The task writes exactly one `.btm` file there by default
- `helperFqn`
  - Default: `de.burger.forensics.infrastructure.rt.RtTraceHelper`
  - Blank values normalize back to that default
- `includes`
  - Extension-only
  - Comma-separated fully qualified package or class prefixes
  - Example: `com.example,org.acme`
- `includeEntryExit`
  - Task-only
  - Default: `true`
  - When `false`, scan mode does not add `METHOD_ENTER` / `METHOD_EXIT` rules
- `minBranchesPerMethod`
  - Default: `2`
  - Methods with fewer than that many branch events (`IF_TRUE`, `IF_FALSE`, `SWITCH`, `SWITCH_CASE`) are filtered out from the final output

Monorepo example:

```kotlin
plugins {
    id("de.burger.forensics.btmgen")
}

btmGen {
    scanSubprojects.set(true)
    outputFile.set(layout.buildDirectory.file("forensics/all-modules.btm").get().asFile)
}
```

Explicit external roots:

```kotlin
btmGen {
    sourceRoots.from(
        file("legacy-module/src/main/java"),
        file("../included-build/some-module/src/main/java")
    )
}
```

Notes:

- Normal `scanSubprojects` discovery scans Gradle subprojects of the current build by reading their `main` `SourceSet`.
- Custom directories configured with `sourceSets.main.java.srcDirs(...)` are picked up automatically.
- Included builds and composite builds are not introspected automatically. Supply their source directories explicitly through `sourceRoots`.

## Generate the `.btm` rules

Run:

```bash
./gradlew generateBtmRules
```

Verified behavior:

- The default output file is `build/forensics/forensics.btm`.
- The file is a single Byteman script containing a generated header and all rendered rule blocks.
- Scan mode is the default mode.
- The plugin also hooks `generateBtmRules` into `build`.

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

- This mode bypasses Java source scanning.
- If `templateId` is blank but still present, the task normalizes it to `METHOD_ENTER`.
- `THREAD_LIFECYCLE` and `JDBC_EXECUTE` are primarily useful in this mode because scan mode skips them.
- The checked-in renderers for `THREAD_LIFECYCLE` and `JDBC_EXECUTE` emit fixed targets (`java.lang.Thread` and `java.sql.Statement` execute methods), even though the task still requires `className` and `methodName` to enter single-template mode.

## Run with Byteman and enable runtime tracing

The generated output is a Byteman script. Generating `build/forensics/forensics.btm` does not instrument any JVM by itself.

To execute the rules:

- load the generated `.btm` file into a JVM with Byteman tooling or the Byteman agent
- make sure the helper class referenced by the rules is on the runtime classpath

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

This repository does not provide a single canonical JVM startup command for Byteman. Use your normal Byteman loading mechanism for the target application or test process.

## Use the tracing facade in application code

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

## Optional AspectJ method logging

`MethodLoggingAspect` is separate from Byteman rule generation and separate from runtime tracing.

Verified behavior:

- It logs method entry, successful return, and thrown exceptions.
- It targets public methods matching:
  - `de.burger.forensics..*`
  - `org.example.trace..*`
- It skips methods annotated with `de.burger.forensics.infrastructure.logging.SuppressLogging`.
- It writes through the target class logger and also mirrors log lines to a file.

Verified file-logging properties:

- `forensics.btmgen.logToFile`
  - Default in the aspect implementation: `true`
- `forensics.btmgen.logFile`
  - Default in the aspect implementation: `logs/forensics-btmgen.log`

Verified weaving setup in this repository:

- `src/main/resources/META-INF/aop.xml` includes `de.burger.forensics..*`
- the same `aop.xml` excludes `de.burger.forensics.infrastructure.logging..*`
- repository tests run with the AspectJ weaver as a Java agent

About `forensics.aspect.enabled`:

- The repository build sets `forensics.aspect.enabled=true` in test configuration.
- The checked-in `MethodLoggingAspect` class does not read that property directly.
- Treat that flag as part of the surrounding build or weaving setup, not as a property consumed by the aspect implementation itself.

## Troubleshooting

- Wrong task name:
  Use `generateBtmRules`. Old names from earlier documentation are stale.

- Wrong extension name:
  Use `btmGen`, not a lowercase or differently spelled variant.

- No useful output because of the wrong source root:
  `GenerateBtmTask` filters source roots that do not exist. If you configured the wrong `sourceRoot` or `sourceRoots`, the task can still create the output file but it may contain only the generated header and no real rules.

- `scanSubprojects` misses code from another build:
  `scanSubprojects` only scans Gradle subprojects of the current build. For included builds, composite builds, or arbitrary external folders, add those directories explicitly with `sourceRoots`.

- No runtime trace appears:
  Generating `.btm` files is not enough. You must both load the script with Byteman and enable tracing with `-Dforensics.rt.enabled=true` or `FORENSICS_RT_ENABLED=true`.

- Confusing rule generation with instrumentation:
  `./gradlew generateBtmRules` only writes a Byteman script. It does not attach Byteman, instrument a JVM, or start runtime tracing by itself.

- Confusing plugin-generated rules with AspectJ logging:
  `MethodLoggingAspect` is separate from Byteman. You can use one without the other.

- Package filtering matches nothing:
  `includes` is a comma-separated prefix filter over fully qualified class names. If no class name starts with one of those prefixes, scan mode produces no matching rules.

- Blank helper FQCN does not stay blank:
  Both `GenerationRequest` and `RuleParams` normalize blank helper values back to `de.burger.forensics.infrastructure.rt.RtTraceHelper`.

- `THREAD_LIFECYCLE` or `JDBC_EXECUTE` do not appear during scanning:
  That is expected. The scan path skips those templates. Use single-template mode for them.

- `includeEntryExit=false` still leaves some rules:
  That flag only suppresses generated `METHOD_ENTER` and `METHOD_EXIT` rules. It does not suppress `IF_*`, `SWITCH*`, `RETURN`, or `THROW`.

## Development and testing notes

The test suite in `src/test/java` verifies the behavior described above. In particular:

- `BtmGenPluginTest`
  - plugin ID application
  - extension/task registration
  - default conventions
  - runtime helper attachment to `runtimeClasspath` and `testRuntimeClasspath`
  - monorepo runtime helper attachment to Java subprojects
  - `build` wiring

- `BtmGenPluginFunctionalTest`
  - root-project monorepo scanning without a root `src/main/java`
  - custom `main` source-set directories in subprojects
  - `UP-TO-DATE` behavior and reruns when sources change

- `GenerateBtmTaskTest`
  - scan mode
  - single-template mode
  - multi-root and subproject scanning
  - missing explicit roots and missing legacy `sourceRoot`
  - subproject `SourceSet` discovery and custom `sourceSets.main.java.srcDirs(...)`
  - helper FQCN normalization
  - duplicate `RULE` header deduplication
  - `THREAD_LIFECYCLE` and `JDBC_EXECUTE` manual rendering paths

- `IfRuleStrategyTest`
  - `IF_TRUE` / `IF_FALSE` rendering at source lines
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
