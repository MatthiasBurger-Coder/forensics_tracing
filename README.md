# Forensics Tracing Toolkit

**Forensics Tracing Toolkit** is a developer-friendly instrumentation stack that turns existing Java services into fully observable systems without invasive code changes. It scans your source code, generates Byteman rules on the fly, and streams structured runtime events so you can investigate production incidents, validate new rollouts, or collect audit trails with minimal overhead.

Whether you are debugging a stubborn race condition or capturing business KPIs for compliance, the toolkit gives you an end-to-end pipeline:

* **Generate** – A Gradle plugin (`de.burger.forensics.btmgen`) walks your sources and renders Byteman rules for method entry/exit, branches, returns, exceptions, thread lifecycle events, JDBC calls, and more. The bundled parser, registry, and file writer keep everything inside your build.
* **Orchestrate** – The application service (`GenerateRulesUseCase`) coordinates scanners, renderers, and deduplication so alternative build systems or custom pipelines can reuse the rule generation logic without Gradle.
* **Trace** – The runtime helper (`RtTrace` with `RtTraceHelper`) emits compact JSON lines enriched with correlation IDs and spans whenever a rule fires, giving you ready-to-ingest telemetry for SIEMs or observability platforms.
* **Audit** – An optional logging aspect (`MethodLoggingAspect`) mirrors method entry/exit details through SLF4J and into a dedicated log file, providing a lightweight forensic trail even when the runtime tracer is disabled.

All components share a clean, hexagonal architecture: scanners, renderers, clocks, and loggers are defined as ports so you can drop in your own adapters, swap implementations during tests, or extend the toolkit with domain-specific instrumentation strategies.

## Gradle plugin quick start

1. **Apply the plugin** in your `build.gradle(.kts)`:
   ```kotlin
   plugins {
       id("de.burger.forensics.btmgen") version "0.0.2-SNAPSHOT" // use the published version you need
   }
   ```

2. **Configure the extension** to define where sources live, which helper class should be invoked from rules, and (optionally) a custom strategy registry:
   ```kotlin
   forensicsBtmGen {
       sourceRoot.set(layout.projectDirectory.dir("src/main/java").asFile)
       outputFile.set(layout.buildDirectory.file("forensics/tracing.btm").get().asFile)
       helperFqn.set("de.burger.forensics.infrastructure.rt.RtTraceHelper")
       // registry.set(...) to provide your own StrategyRegistry implementation
   }
   ```
   The extension exposes strongly typed properties with sensible defaults (`src/main/java` and `build/forensics/forensics.btm`). 【F:src/main/java/de/burger/forensics/plugin/btmgen/gradle/BtmGenExtension.java†L13-L55】

3. **Register the generation task**. The bundled `GenerateBtmTask` scans `.java` files, optionally falls back to single-template rendering, and writes all rules to the configured `.btm` file.
   ```kotlin
   tasks.register("generateForensicsRules", de.burger.forensics.plugin.btmgen.gradle.GenerateBtmTask::class) {
       sourceRoot.set(layout.projectDirectory.dir("src/main/java"))
       outputFile.set(layout.buildDirectory.file("forensics/forensics.btm"))
       helperFqn.set("de.burger.forensics.infrastructure.rt.RtTraceHelper")
       includeEntryExit.convention(true)
       minBranchesPerMethod.convention(1)
   }
   ```
   The task ensures the output directory exists, walks the source tree, converts every detected decision point into a `RuleTemplate`, and writes a single aggregated Byteman script. 【F:src/main/java/de/burger/forensics/plugin/btmgen/gradle/GenerateBtmTask.java†L65-L134】

4. **Run the task**:
   ```bash
   ./gradlew generateForensicsRules
   ```
   The script will be available at the configured output path. Load it into a JVM running the Byteman agent with `bmsubmit` as usual.

### Safe mode, filtering, and deduplication

The application service exposes additional controls through `GenerationRequest`:

* `safeMode` wraps each condition so it is evaluated through a helper for defensive execution. 【F:src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java†L131-L150】【F:src/main/java/de/burger/forensics/domain/strategy/SafeModeDecorator.java†L1-L36】
* `includeEntryExit` decides whether synthetic method-enter/-exit rules should be injected when the scanner did not emit them. 【F:src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java†L78-L121】
* `minBranches` filters out methods that do not meet your desired branch coverage. 【F:src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java†L122-L159】
* `trackedVariables` allows downstream adapters to request additional instrumentation for variable writes. The record constructor enforces immutability and sane defaults (no null lists, helper fallback). 【F:src/main/java/de/burger/forensics/application/service/GenerationRequest.java†L11-L27】

If you integrate the use case directly you get a `RuleGenerationResult` containing the rendered script lines in declaration order. Duplicated rules are automatically removed while preserving stable ordering. 【F:src/main/java/de/burger/forensics/application/service/RuleGenerationResult.java†L1-L8】【F:src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java†L109-L121】

## Runtime tracing helper

`RtTrace` offers a dependency-free runtime that emits JSON lines to `stdout` when enabled via the system property `-Dforensics.rt.enabled=true` or the environment variable `FORENSICS_RT_ENABLED=true`. Each event includes a timestamp, thread name, optional correlation ID, and active span identifier. Convenience methods exist for method entry/exit, branch decisions, switch cases, variable writes, I/O, lock usage, and custom payloads. 【F:src/main/java/de/burger/forensics/infrastructure/rt/RtTrace.java†L1-L199】

Byteman rules should use `RtTraceHelper`, which delegates helper invocations back to the runtime singleton. 【F:src/main/java/de/burger/forensics/infrastructure/rt/RtTraceHelper.java†L1-L38】

### Adapting the tracing facade

At the application layer you can depend on the lightweight `Tracer` interface so your domain logic stays unaware of Byteman or runtime specifics. The repository contains a ready-to-copy adapter that bridges `Tracer` to the runtime helper: see [`examples/RtTracerAdapter.java`](examples/RtTracerAdapter.java). It delegates span, branch, variable, error, and correlation ID operations to `RtTrace`. 【F:examples/RtTracerAdapter.java†L1-L55】

## Example: instrumenting an existing service

The snippet below shows how the adapter can be dropped into an existing class to capture tracing data whenever the runtime is enabled.

```java
public final class PaymentService {
    private static final Tracer tracer = new RtTracerAdapter();

    public PaymentResult process(PaymentCommand cmd) {
        tracer.enter(getClass(), "process", cmd);
        tracer.setCorrelationId(tracer.newCorrelationId());
        try (var span = tracer.span("payment")) {
            boolean approved = authorize(cmd);
            tracer.branch("approved", approved);
            if (!approved) {
                tracer.var("state", "DECLINED");
                return PaymentResult.declined();
            }
            PaymentResult result = capture(cmd);
            tracer.var("state", result.status());
            return result;
        } catch (Exception ex) {
            tracer.error(ex);
            throw ex;
        } finally {
            tracer.exit(getClass(), "process", null);
        }
    }
}
```

When the JVM runs with `-Dforensics.rt.enabled=true` the trace is emitted as structured JSON lines (one per event). When disabled, all methods are cheap no-ops, so you can safely leave the instrumentation active in production builds. 【F:src/main/java/de/burger/forensics/application/tracing/Tracer.java†L1-L17】【F:src/main/java/de/burger/forensics/infrastructure/rt/RtTrace.java†L18-L104】

## Optional logging aspect

`MethodLoggingAspect` complements the runtime helper. When woven into your application (compile-time or load-time), it logs entry, exit, and failure events using the SLF4J logger of the advised class. The aspect also mirrors logs into a configurable file (`forensics.btmgen.logFile`, default `logs/forensics-btmgen.log`) so you always have a local audit trail. Apply `@SuppressLogging` to opt out per method. 【F:src/main/java/de/burger/forensics/infrastructure/logging/MethodLoggingAspect.java†L1-L120】

## Building and testing

The project targets Java 21 and ships with Gradle tasks configured for AspectJ weaving during tests. Run all checks with:

```bash
./gradlew test
```

This executes the unit tests, architecture rules, and Gradle TestKit scenarios that verify the plugin behaviour. 【F:build.gradle.kts†L1-L132】

For deeper Byteman usage details, consult the official documentation at [https://byteman.jboss.org/](https://byteman.jboss.org/).
