# Forensics Tracing Toolkit

`forensics-tracing` generates Byteman rules from Java source code and provides runtime helpers for low-overhead tracing.

## What is included

- Gradle plugin: `de.burger.forensics.btmgen`
- Rule generation application service: `GenerateRulesUseCase`
- JavaParser-based scanner with multi-file parallel scanning
- Byteman renderer and `.btm` writer
- Runtime tracing helper: `RtTrace` / `RtTraceHelper`

## Requirements

- Java 21+
- Gradle 9.1+
- Byteman runtime when you want to load generated rules into a JVM

## Apply the Gradle plugin

```kotlin
plugins {
    id("de.burger.forensics.btmgen") version "0.0.3-SNAPSHOT"
}
```

The plugin registers task `generateBtmRules` and wires it into `build`.

## Configure the plugin

```kotlin
btmGen {
    sourceRoot.set(file("src/main/java"))
    outputFile.set(file("build/forensics/forensics.btm"))
    helperFqn.set("de.burger.forensics.infrastructure.rt.RtTraceHelper")
    minBranchesPerMethod.set(2)
    includes.set("com.example,org.acme")
}
```

## Generate rules

```bash
./gradlew generateBtmRules
```

Generated output default:

- `build/forensics/forensics.btm`

## How scanning works

- Scans `.java` files below `sourceRoot`
- Skips non-Java files
- Ignores parse errors per file to keep generation resilient
- Parses files in parallel batches
- Uses isolated parser/symbol-solver instances per worker to stay thread-safe

## Supported rule templates

- `IF_TRUE`
- `IF_FALSE`
- `SWITCH`
- `SWITCH_CASE`
- `RETURN`
- `THROW`
- `METHOD_ENTER`
- `METHOD_EXIT`
- `THREAD_LIFECYCLE`
- `JDBC_EXECUTE`

## Application service usage

```java
GenerateRulesUseCase useCase = new GenerateRulesUseCase(
    scanner,
    renderer,
    clock,
    log,
    strategyFactory
);

GenerationRequest request = new GenerationRequest(
    rootPath,
    "de.burger.forensics.infrastructure.rt.RtTraceHelper",
    false,
    true,
    List.of("com.example"),
    2,
    List.of()
);

RuleGenerationResult result = useCase.generate(request);
List<String> rules = result.renderedRules();
```

## Runtime tracing

Enable runtime tracing:

- JVM property: `-Dforensics.rt.enabled=true`
- or env var: `FORENSICS_RT_ENABLED=true`

`RtTrace` emits one JSON line per event to stdout.

## Build and test

```bash
./gradlew test
```

## Local development

Build plugin and library artifacts:

```bash
./gradlew build
```

Publish locally:

```bash
./gradlew publishToMavenLocal
```
