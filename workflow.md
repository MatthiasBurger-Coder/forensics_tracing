# Codex Workflow: Gradle- und Maven-Plugin-Adapter für Forensics BTMGen

## Ziel

Das bestehende Gradle-Plugin soll so umgebaut werden, dass Gradle- und Maven-Unterstützung sauber getrennt sind, ohne die Scanner-, Renderer- oder Rule-Generation-Logik zu duplizieren.

Zielstruktur im bestehenden Single-Modul:

```text
src/main/java/de/burger/forensics/plugin/btmgen
├── common
│   ├── BtmGenerationRunner.java
│   ├── BtmGenerationRequest.java
│   ├── BtmGenerationResult.java
│   ├── BtmGenerationDefaults.java
│   └── PluginLogPort.java
│
├── gradle
│   ├── BtmGenPlugin.java
│   ├── BtmGenExtension.java
│   ├── GenerateBtmTask.java
│   └── internal
│       └── PluginRuntimeLocator.java
│
├── maven
│   ├── BtmGenMojo.java
│   ├── MavenBtmGenParameters.java
│   └── MavenLogAdapter.java
│
├── internal
│   └── BytemanRuleRenderAdapter.java
│
├── render
│   └── ... existing renderer classes
│
└── writer
    └── BtmFileWriter.java
```

Langfristig kann daraus später ein Multi-Modul-Build entstehen:

```text
forensics-core
forensics-gradle-plugin
forensics-tracing
```

Dieser Workflow bleibt bewusst im bestehenden Projekt, damit der Umbau kontrolliert und reviewbar bleibt.

---

## Grundregel

Der Maven-Support darf keine zweite Parser- oder BTM-Generation-Implementierung werden.

Richtig:

```text
GenerateBtmTask ─┐
                 ├── BtmGenerationRunner ─── Scanner / UseCase / Renderer / Writer
BtmGenMojo   ────┘
```

Falsch:

```text
GenerateBtmTask ─── eigene Scanner-Orchestrierung
BtmGenMojo    ─── eigene Scanner-Orchestrierung
```

---

## Globale Constraints

* Java 17 only.
* Gradle 9.4.0 only.
* Source-Code-Kommentare ausschließlich in Englisch.
* Antworten, Dokumentation und Commit-Erklärungen dürfen Deutsch sein.
* Kein Spring Boot.
* Kein Maven-Build als primäres Buildsystem.
* Kein `quality_gate.py` erfinden.
* Keine Coverage-Thresholds senken.
* Keine bestehenden Tests entfernen.
* Keine Parserlogik in Gradle- oder Maven-Adapter duplizieren.
* Keine H2-, SQLite-, JDBC-, Gradle- oder Maven-Typen in Domain/Application leaken.
* Maven-Adapter darf Maven-Typen importieren.
* Gradle-Adapter darf Gradle-Typen importieren.
* `common` darf weder Gradle- noch Maven-Typen importieren.

---

# Workflow-Übersicht

Der Umbau erfolgt in Slices:

```text
Slice 0: Baseline prüfen
Slice 1: Common Request/Result/Log-Port einführen
Slice 2: BtmGenerationRunner aus GenerateBtmTask extrahieren
Slice 3: Gradle-Adapter verschlanken
Slice 4: Maven-Plugin-Abhängigkeiten und Descriptor-Strategie vorbereiten
Slice 5: Maven Mojo Adapter einführen
Slice 6: ArchUnit-Regeln härten
Slice 7: Funktionale Validierung mit Gradle und Maven
Slice 8: Dokumentation aktualisieren
Slice 9: Quality Gate, Diff-Review, Commit
```

Jeder Slice muss einzeln testbar und reviewbar sein.

---

# Slice 0 — Baseline prüfen

## Ziel

Vor dem Umbau muss klar sein, dass das Repository sauber ist und welche Tests/Quality-Gates aktuell gelten.

## Codex Prompt

```md
# Slice 0: Baseline inspection before plugin adapter split

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

The project currently contains a Gradle plugin under:

`src/main/java/de/burger/forensics/plugin/btmgen/gradle`

The goal of later slices is to split build-tool-specific adapters into:

- `plugin/btmgen/gradle`
- `plugin/btmgen/maven`
- `plugin/btmgen/common`

Do not implement this split in this slice.

## Task

Inspect the repository and establish the current baseline.

## Required actions

1. Inspect repository structure.
2. Inspect `build.gradle.kts`.
3. Inspect `settings.gradle.kts` if present.
4. Inspect `QUALITY.md`.
5. Inspect `AGENTS.md`.
6. Inspect current Gradle plugin classes:
   - `BtmGenPlugin.java`
   - `BtmGenExtension.java`
   - `GenerateBtmTask.java`
7. Inspect current scanner and use-case wiring.
8. Run the documented quality gate from `QUALITY.md`.
9. Do not modify files.

## Required output

Return:

1. Current plugin package layout.
2. Current Gradle task responsibilities.
3. Current quality gate command.
4. Current quality gate result.
5. Existing risks before refactoring.
6. Suggested first implementation slice.

## Restrictions

Do not modify code.
Do not add dependencies.
Do not create Maven plugin classes.
Do not change Java version.
Do not invent missing scripts.
```

## Akzeptanzkriterien

* Keine Dateien geändert.
* Quality Gate wurde ausgeführt oder klar begründet, warum es nicht ausgeführt werden konnte.
* Aktuelle Verantwortlichkeiten des Gradle-Tasks sind dokumentiert.

---

# Slice 1 — Common Request/Result/Log-Port einführen

## Ziel

Build-tool-neutrale Übergabeobjekte einführen, ohne das bestehende Verhalten zu ändern.

## Neue Klassen

```text
src/main/java/de/burger/forensics/plugin/btmgen/common/BtmGenerationRequest.java
src/main/java/de/burger/forensics/plugin/btmgen/common/BtmGenerationResult.java
src/main/java/de/burger/forensics/plugin/btmgen/common/BtmGenerationDefaults.java
src/main/java/de/burger/forensics/plugin/btmgen/common/PluginLogPort.java
src/main/java/de/burger/forensics/plugin/btmgen/common/NoOpPluginLogPort.java
```

## Inhaltliche Idee

`BtmGenerationRequest` enthält nur neutrale Java-Typen:

```java
Path sourceRoot;
Path outputFile;
Path cacheDatabaseFile;
Path profileReportFile;
boolean cacheEnabled;
boolean profilingEnabled;
boolean strictParsing;
List<String> includePackages;
List<String> excludePackages;
```

Keine Gradle-Typen:

```java
RegularFileProperty
DirectoryProperty
Property<T>
Project
Logger
```

Keine Maven-Typen:

```java
MavenProject
MojoExecutionException
Log
```

## Codex Prompt

```md
# Slice 1: Add build-tool-neutral BTM generation request/result model

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

The current Gradle task contains build-tool-specific configuration and scanner orchestration.

Before extracting the runner, introduce build-tool-neutral request/result types under:

`src/main/java/de/burger/forensics/plugin/btmgen/common`

## Goal

Add neutral common types that can later be used by both:

- Gradle task adapter
- Maven Mojo adapter

Do not change runtime behavior yet.

## Required classes

Create:

1. `BtmGenerationRequest`
2. `BtmGenerationResult`
3. `BtmGenerationDefaults`
4. `PluginLogPort`
5. `NoOpPluginLogPort`

## Requirements

### BtmGenerationRequest

Use only Java standard library and project-internal neutral types.

Allowed field types:

- `java.nio.file.Path`
- `java.util.List`
- `java.util.Optional` if needed
- primitive types
- project-internal neutral enums/value objects

Do not use Gradle types.
Do not use Maven types.
Do not use JDBC/H2/SQLite types.

Suggested fields:

- `Path sourceRoot`
- `Path outputFile`
- `Path cacheDatabaseFile`
- `Path profileReportFile`
- `boolean cacheEnabled`
- `boolean profilingEnabled`
- `boolean strictParsing`
- `List<String> includePackages`
- `List<String> excludePackages`

Use an immutable object or a small builder.

### BtmGenerationResult

Represent execution result without build-tool-specific types.

Suggested fields:

- `Path outputFile`
- `Path profileReportFile`
- `int generatedRuleCount`
- `int scannedFileCount`
- `int parsedFileCount`
- `int failedFileCount`
- `int cacheHitCount`
- `int cacheMissCount`

Adjust fields to existing project capabilities.

### BtmGenerationDefaults

Centralize default locations and settings.

Examples:

- default output file name
- default profile report name
- default cache database name
- default cache enabled flag
- default strict parsing flag

### PluginLogPort

Provide a tiny logging abstraction for common runner output.

Suggested methods:

- `info(String message)`
- `warn(String message)`
- `error(String message)`
- `debug(String message)`

Do not expose Gradle Logger or Maven Log.

### NoOpPluginLogPort

Implementation that ignores all messages.

## Tests

Add unit tests for:

1. Request builder creates immutable request.
2. Defaults produce expected relative paths/names.
3. NoOp logger does not throw.

## Restrictions

Do not modify Gradle task behavior yet.
Do not add Maven dependencies yet.
Do not create Maven Mojo yet.
Do not move scanner logic yet.
Do not change generated BTM output.

## Quality

Run relevant unit tests.
If cheap enough, run full quality gate from `QUALITY.md`.

## Final response

Return:

1. Changed files.
2. New common model summary.
3. Test result.
4. Remaining follow-up for Slice 2.
```

## Akzeptanzkriterien

* Neue Common-Klassen kompilieren.
* Keine Gradle-/Maven-Imports in `plugin/btmgen/common`.
* Existing Gradle Plugin läuft unverändert.

---

# Slice 2 — BtmGenerationRunner extrahieren

## Ziel

Die eigentliche Orchestrierung aus `GenerateBtmTask` herauslösen.

Der Gradle-Task soll später nur noch:

1. Gradle-Properties lesen,
2. `BtmGenerationRequest` bauen,
3. `BtmGenerationRunner.generate(request)` aufrufen,
4. Gradle-Logging/Exceptions behandeln.

## Neue Klasse

```text
src/main/java/de/burger/forensics/plugin/btmgen/common/BtmGenerationRunner.java
```

## Codex Prompt

```md
# Slice 2: Extract build-tool-neutral BtmGenerationRunner from GenerateBtmTask

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

Slice 1 introduced common request/result/log types under:

`src/main/java/de/burger/forensics/plugin/btmgen/common`

The current Gradle task still owns too much orchestration.

## Goal

Extract scanner/use-case/renderer/writer orchestration from `GenerateBtmTask` into:

`BtmGenerationRunner`

The generated BTM output must remain identical for the same input.

## Required actions

1. Inspect `GenerateBtmTask`.
2. Identify all build-tool-neutral orchestration currently inside the task.
3. Move neutral orchestration into `BtmGenerationRunner`.
4. Keep Gradle-specific property access inside `GenerateBtmTask`.
5. Keep Gradle-specific task annotations inside `GenerateBtmTask`.
6. Keep Gradle-specific logging out of `BtmGenerationRunner`.
7. Use `PluginLogPort` for common logging if needed.
8. Return `BtmGenerationResult` from the runner.

## BtmGenerationRunner responsibilities

The runner may own:

- scanner construction
- cache adapter selection if already implemented
- profile sink selection if already implemented
- use-case construction
- Byteman rule rendering
- deterministic deduplication if already done in task
- BTM file writing
- result summary creation

The runner must not import:

- `org.gradle.*`
- `org.apache.maven.*`
- H2/JDBC types unless already hidden behind existing adapters

## GenerateBtmTask responsibilities after this slice

The task should only:

- expose Gradle properties
- map Gradle properties to `BtmGenerationRequest`
- create the runner during task execution
- invoke the runner
- translate failures into Gradle task failures

Do not store parser/scanner/runner services as task fields if that harms Gradle configuration cache.

## Tests

Add or update tests:

1. Runner can generate BTM output for a small fixture.
2. Gradle task still generates the same BTM output as before.
3. Existing plugin tests continue to pass.
4. If there is a golden-file test for BTM output, keep it stable.

## Restrictions

Do not add Maven dependencies yet.
Do not create Maven Mojo yet.
Do not change scanner semantics.
Do not change package filtering behavior unless a test proves the current behavior was unstable.
Do not lower coverage thresholds.

## Quality

Run:

- relevant unit tests
- existing Gradle plugin tests
- documented quality gate if feasible

## Final response

Return:

1. Changed files.
2. What moved from `GenerateBtmTask` to `BtmGenerationRunner`.
3. What intentionally stayed in `GenerateBtmTask`.
4. Test results.
5. BTM output compatibility statement.
```

## Akzeptanzkriterien

* `BtmGenerationRunner` enthält keine Gradle-Imports.
* `GenerateBtmTask` ist dünner.
* BTM-Ausgabe bleibt gleich.
* Existing Tests laufen.

---

# Slice 3 — Gradle-Adapter verschlanken

## Ziel

Den bestehenden Gradle-Adapter sauber als Adapter kennzeichnen und stabilisieren.

## Codex Prompt

```md
# Slice 3: Harden Gradle adapter after runner extraction

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

`BtmGenerationRunner` now owns build-tool-neutral orchestration.

`GenerateBtmTask` should be a thin Gradle adapter only.

## Goal

Clean up and harden the Gradle plugin adapter without changing behavior.

## Required actions

1. Inspect:
   - `BtmGenPlugin`
   - `BtmGenExtension`
   - `GenerateBtmTask`
2. Ensure Gradle-specific code stays only in `plugin/btmgen/gradle`.
3. Ensure task properties are lazy and configuration-cache-friendly.
4. Ensure no parser/scanner/database objects are stored as task fields.
5. Ensure all file access happens during task execution.
6. Ensure the task maps all properties into `BtmGenerationRequest`.
7. Add a small Gradle-specific log adapter if needed:
   - `GradlePluginLogAdapter`

## Required Gradle rules

The Gradle task may import:

- `org.gradle.api.*`
- `org.gradle.api.tasks.*`
- Gradle property types

The Gradle task must not contain duplicated scanner orchestration.

## Tests

Add or update tests for:

1. Gradle task maps extension values to runner request.
2. Gradle task creates output file.
3. Profiling flag maps correctly if present.
4. Cache flag maps correctly if present.
5. Missing source root fails with a clear Gradle error.

## Restrictions

Do not add Maven Mojo yet.
Do not add Maven dependencies yet unless needed by build metadata only.
Do not change BTM semantics.
Do not introduce eager file access during configuration.

## Quality

Run Gradle plugin tests and quality gate from `QUALITY.md`.

## Final response

Return:

1. Changed files.
2. Gradle adapter responsibilities after cleanup.
3. Configuration-cache-relevant changes.
4. Test results.
5. Remaining work before Maven adapter.
```

## Akzeptanzkriterien

* Gradle-Adapter ist dünn.
* Kein Common-Code importiert Gradle.
* Task funktioniert wie vorher.

---

# Slice 4 — Maven-Plugin-Abhängigkeiten und Descriptor-Strategie vorbereiten

## Ziel

Maven-Plugin technisch vorbereiten, ohne direkt die volle Mojo-Logik zu bauen.

Ein Maven-Plugin braucht eine Mojo-Klasse und einen Plugin-Descriptor in:

```text
META-INF/maven/plugin.xml
```

Da das Projekt selbst Gradle-basiert bleibt, muss entschieden werden, wie dieser Descriptor erzeugt wird.

## Mögliche Strategie

Mit Gradle kann dafür das Plugin `org.gradlex.maven-plugin-development` verwendet werden. Vor Einbau muss Codex prüfen, ob es mit der bestehenden Gradle-Version und Projektstruktur sauber funktioniert.

## Codex Prompt

```md
# Slice 4: Prepare Maven plugin build support and descriptor strategy

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

The project currently builds a Gradle plugin.

The next target is to add a Maven Mojo adapter under:

`src/main/java/de/burger/forensics/plugin/btmgen/maven`

However, Maven plugins require plugin metadata/descriptor generation.

Do not implement full Maven scanning logic in this slice.

## Goal

Prepare Maven plugin development support in the Gradle build safely.

## Required research within repository

Inspect:

- `build.gradle.kts`
- `settings.gradle.kts`
- version catalog if present
- existing publishing configuration
- existing plugin configuration
- existing dependency constraints
- `QUALITY.md`

## Required decision

Decide how the Maven plugin descriptor should be generated.

Preferred direction:

- Use a Gradle-compatible Maven plugin development helper if it fits cleanly.
- Keep the main build Gradle-based.
- Do not introduce a Maven build.

If the helper plugin is not suitable, document a fallback strategy and stop before forcing a broken setup.

## Dependencies likely needed

Maven plugin annotations and APIs may be needed later:

- `org.apache.maven:maven-plugin-api`
- `org.apache.maven.plugin-tools:maven-plugin-annotations`
- possibly `org.apache.maven:maven-core` only if `MavenProject` is required and not provided otherwise

Use compile-only/provided-like configuration where appropriate so the plugin does not package Maven runtime unnecessarily.

## Required actions

1. Add only the minimum build configuration required for Maven Mojo compilation and descriptor generation.
2. Keep Gradle plugin behavior unchanged.
3. Do not add the full Mojo implementation yet unless needed for descriptor validation.
4. If a minimal placeholder Mojo is required for descriptor generation, create it with no scanner logic.
5. Ensure Java 17 configuration remains correct.
6. Ensure Gradle 9.4.0 compatibility.

## Restrictions

Do not create a second Maven build.
Do not convert the project to Maven.
Do not change Gradle plugin ID.
Do not break existing Gradle plugin publishing.
Do not add scanner logic to Maven classes in this slice.
Do not duplicate `GenerateBtmTask` logic.

## Tests / validation

Run:

- `./gradlew compileJava`
- descriptor-related Gradle task if available
- existing relevant tests
- quality gate from `QUALITY.md` if feasible

## Final response

Return:

1. Changed build files.
2. Added dependencies/plugins.
3. Descriptor generation strategy.
4. Whether a placeholder Mojo was required.
5. Test/validation results.
6. Risks or follow-up work.
```

## Akzeptanzkriterien

* Projekt kompiliert weiter.
* Maven-Plugin-Metadatenstrategie ist klar.
* Gradle-Plugin ist nicht beschädigt.

---

# Slice 5 — Maven Mojo Adapter einführen

## Ziel

Maven bekommt einen dünnen Adapter, der dieselbe `BtmGenerationRunner`-Logik nutzt.

## Neue Klassen

```text
src/main/java/de/burger/forensics/plugin/btmgen/maven/BtmGenMojo.java
src/main/java/de/burger/forensics/plugin/btmgen/maven/MavenBtmGenParameters.java
src/main/java/de/burger/forensics/plugin/btmgen/maven/MavenLogAdapter.java
```

## Maven Goal

Für den Anfang nur:

```text
forensics:btmgen
```

## Codex Prompt

````md
# Slice 5: Add Maven Mojo adapter for BTM generation

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

Previous slices introduced:

- `BtmGenerationRequest`
- `BtmGenerationResult`
- `BtmGenerationRunner`
- build-tool-neutral common package
- descriptor strategy for Maven plugin support

Now add the Maven adapter.

## Goal

Create a Maven Mojo that calls the existing `BtmGenerationRunner`.

Do not duplicate scanner logic.

## Required classes

Create or complete:

1. `BtmGenMojo`
2. `MavenBtmGenParameters`
3. `MavenLogAdapter`

## Package

Use:

`de.burger.forensics.plugin.btmgen.maven`

## Mojo behavior

The Mojo should:

1. Read Maven project source roots.
2. Read plugin parameters.
3. Build `BtmGenerationRequest`.
4. Create `BtmGenerationRunner` during execution.
5. Run generation.
6. Log result summary through Maven logging.
7. Translate failures to `MojoExecutionException`.

## Maven parameters

Support at least:

- `sourceRoot`
- `outputFile`
- `cacheEnabled`
- `cacheDatabaseFile`
- `profilingEnabled`
- `profileReportFile`
- `strictParsing`
- `includePackages`
- `excludePackages`
- `includeTests`

Suggested defaults:

- source root: Maven compile source roots
- output file: `${project.build.directory}/forensics/generated.btm`
- profile report: `${project.build.directory}/forensics/scan-profile.json`
- cache database: `${project.build.directory}/forensics/scan-cache.mv.db`
- cache enabled: current project default
- strict parsing: false
- include tests: false

## MavenProject handling

If `MavenProject` is used, keep it only in the Maven adapter package.

Do not expose Maven types to:

- common
- application
- domain
- JavaParser adapter
- Gradle adapter

## Multi-module behavior for first version

Keep behavior simple and explicit:

- The Mojo runs for the current Maven project/module.
- It uses that module's compile source roots.
- Reactor aggregation is not required in this slice.
- Aggregator behavior can be a later feature.

## Tests

Add tests for:

1. Maven parameters map to `BtmGenerationRequest`.
2. Maven log adapter does not throw.
3. Mojo fails clearly when no source root exists.
4. Mojo uses explicit `sourceRoot` when configured.
5. Mojo does not import Gradle types.

If direct Maven plugin integration testing is too heavy, add unit tests for the parameter mapper and document remaining integration validation.

## Restrictions

Do not duplicate scanner logic.
Do not make the Mojo call `GenerateBtmTask`.
Do not import Gradle classes.
Do not implement Maven reactor aggregation yet.
Do not implement multiple goals yet.
Do not change Gradle plugin behavior.
Do not lower test coverage thresholds.

## Validation

Run:

- compile
- tests
- descriptor generation
- quality gate from `QUALITY.md` if feasible

If possible, validate a minimal Maven project manually with:

```bash
mvn de.burger.forensics:forensics-tracing:<version>:btmgen
````

Adjust group/artifact/version to the actual project coordinates.

## Final response

Return:

1. Changed files.
2. Maven goal name.
3. Supported parameters.
4. How the Mojo maps to `BtmGenerationRunner`.
5. Test results.
6. Descriptor generation result.
7. Limitations.

````

## Akzeptanzkriterien

- Maven-Mojo kompiliert.
- Mojo verwendet `BtmGenerationRunner`.
- Keine Gradle-Imports im Maven-Package.
- Keine Maven-Imports im Common-Package.

---

# Slice 6 — ArchUnit-Regeln härten

## Ziel

Die neue Struktur darf später nicht verwässern.

## Regeln

```text
common darf nicht auf gradle zugreifen
common darf nicht auf maven zugreifen
gradle darf nicht auf maven zugreifen
maven darf nicht auf gradle zugreifen
domain/application dürfen nicht auf gradle/maven zugreifen
````

## Codex Prompt

```md
# Slice 6: Add architecture rules for Gradle/Maven adapter separation

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

The project now has build-tool-specific packages:

- `de.burger.forensics.plugin.btmgen.gradle`
- `de.burger.forensics.plugin.btmgen.maven`
- `de.burger.forensics.plugin.btmgen.common`

The separation must be protected by ArchUnit.

## Goal

Add or update ArchUnit tests to enforce build-tool adapter boundaries.

## Required rules

Add rules ensuring:

1. `plugin.btmgen.common` does not depend on Gradle.
2. `plugin.btmgen.common` does not depend on Maven.
3. `plugin.btmgen.gradle` does not depend on Maven.
4. `plugin.btmgen.maven` does not depend on Gradle.
5. `domain` does not depend on Gradle.
6. `domain` does not depend on Maven.
7. `application` does not depend on Gradle.
8. `application` does not depend on Maven.
9. `adapters.javaparser` does not depend on Gradle or Maven.

## Package/import checks

Disallow dependencies on packages matching:

- `org.gradle..`
- `org.apache.maven..`

except:

- Gradle imports are allowed only in `..plugin.btmgen.gradle..`
- Maven imports are allowed only in `..plugin.btmgen.maven..`

## Tests

Update the existing architecture test class if appropriate:

`src/test/java/de/burger/forensics/quality/HexagonRulesTest.java`

or create a dedicated test class:

`PluginAdapterArchitectureTest.java`

## Restrictions

Do not weaken existing architecture rules.
Do not remove existing tests.
Do not add broad ignore rules.
Do not move packages only to satisfy tests unless the move is architecturally correct.

## Validation

Run ArchUnit tests.
Run the full test suite if feasible.
Run quality gate from `QUALITY.md`.

## Final response

Return:

1. Changed test files.
2. New architecture rules.
3. Any violations found and fixed.
4. Test results.
```

## Akzeptanzkriterien

* ArchUnit schützt die Adaptergrenzen.
* Keine breiten Ausnahmen.
* Alte Regeln bleiben erhalten.

---

# Slice 7 — Funktionale Validierung Gradle + Maven

## Ziel

Nachweisen, dass beide Adapter denselben Core verwenden und vergleichbare Ausgabe erzeugen.

## Codex Prompt

````md
# Slice 7: Validate Gradle and Maven adapters against the same scanner core

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

Both adapters should now call:

`BtmGenerationRunner`

The goal is to prove they produce compatible BTM output for the same source input.

## Goal

Add validation that Gradle and Maven adapters use the same core behavior.

## Required validation

Use a small Java fixture project or existing test fixture.

Validate:

1. Core runner generates BTM output.
2. Gradle task generates BTM output.
3. Maven parameter mapping targets the same request structure.
4. Maven Mojo can generate BTM output if feasible in tests.
5. BTM output is deterministic.

## Suggested fixture

Create or reuse a small source tree containing:

- one class
- one method
- one local variable
- one method call
- one conditional branch

The output should be stable enough for golden-file comparison.

## If Maven integration testing is too heavy

At minimum:

1. Unit-test Maven parameter mapping.
2. Unit-test `BtmGenMojo` with a fake or temporary project structure if possible.
3. Document the manual Maven validation command.

Do not fake success.

## Manual validation command

Document the actual command needed after publishing to Maven local or generating plugin metadata.

Example shape:

```bash
./gradlew publishToMavenLocal

mvn de.burger.forensics:forensics-tracing:<version>:btmgen \
  -Dforensics.sourceRoot=/path/to/sample/src/main/java
````

Use actual coordinates from the project.

## Restrictions

Do not modify external benchmark repositories in this slice.
Do not introduce a full external large-project benchmark.
Do not weaken tests.
Do not skip failing tests silently.

## Validation

Run:

* unit tests
* ArchUnit tests
* Gradle plugin tests
* Maven descriptor validation if available
* full quality gate from `QUALITY.md` if feasible

## Final response

Return:

1. Validation setup.
2. Gradle validation result.
3. Maven validation result.
4. Whether outputs match.
5. Test results.
6. Any remaining limitations.

````

## Akzeptanzkriterien

- Gemeinsamer Runner ist funktional bewiesen.
- Maven-Adapter ist zumindest über Mapping/Unit-Test validiert.
- Gradle-Adapter bleibt funktionsfähig.

---

# Slice 8 — Dokumentation aktualisieren

## Ziel

README/QUALITY/AGENTS so aktualisieren, dass neue Adapterstruktur verständlich ist.

## Codex Prompt

```md
# Slice 8: Document Gradle and Maven plugin adapter architecture

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

The project now separates:

- common BTM generation runner
- Gradle plugin adapter
- Maven Mojo adapter

## Goal

Update documentation to describe the new build-tool adapter architecture and usage.

## Files to inspect

- `README.md`
- `QUALITY.md`
- `AGENTS.md`
- plugin-related docs if present

## Required documentation content

Add or update:

1. Architecture overview:

```text
Gradle task  ─┐
              ├── BtmGenerationRunner
Maven Mojo   ─┘
````

2. Explanation that scanner logic is build-tool-neutral.
3. Gradle usage example.
4. Maven usage example.
5. Maven limitations for first version.
6. Notes about source-code comments being English.
7. Quality gate command if changed.
8. Architecture rules for adapter boundaries.

## Maven usage example

Document the actual group/artifact/version from the project.

Example shape:

```xml
<plugin>
    <groupId>de.burger.forensics</groupId>
    <artifactId>forensics-tracing</artifactId>
    <version>...</version>
    <configuration>
        <cacheEnabled>true</cacheEnabled>
        <strictParsing>false</strictParsing>
        <outputFile>${project.build.directory}/forensics/generated.btm</outputFile>
    </configuration>
</plugin>
```

Command example:

```bash
mvn forensics:btmgen
```

Only document prefix usage if plugin prefix metadata actually supports it.

## Restrictions

Do not claim full support for a specific external large project unless validated.
Do not claim Maven Central availability unless implemented.
Do not claim Maven reactor aggregation unless implemented.
Do not change code in this documentation slice unless needed for broken docs tests.

## Validation

Run docs-related checks if any.
Run quality gate from `QUALITY.md` if feasible.

## Final response

Return:

1. Changed documentation files.
2. New documented architecture.
3. Usage examples added.
4. Limitations documented.
5. Validation result.

````

## Akzeptanzkriterien

- README erklärt Gradle und Maven.
- Keine falschen Versprechen.
- AGENTS schützt neue Architekturregeln.

---

# Slice 9 — Quality Gate, Diff-Review, Commit

## Ziel

Nach der Umsetzung sauber prüfen und committen.

## Codex Prompt

```md
# Slice 9: Final quality gate, diff review, and commit

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

The previous slices introduced:

- build-tool-neutral BTM generation runner
- Gradle adapter cleanup
- Maven Mojo adapter
- architecture rules
- documentation

## Goal

Run final validation, inspect all changes, create a meaningful commit, and optionally push if configured by the user.

## Required execution order

### Phase 1 — Repository inspection

1. Run `git status`.
2. Inspect unstaged changes.
3. Inspect staged changes if any.
4. Inspect changed file list.
5. Inspect relevant diffs.

### Phase 2 — Quality gate

1. Read `QUALITY.md`.
2. Run the documented quality gate exactly as documented.
3. Do not invent `quality_gate.py`.
4. If the quality gate fails, fix only task-related issues.
5. Re-run the quality gate.
6. If remaining failures are unrelated or not realistically fixable, report them clearly.

### Phase 3 — Architecture validation

Verify:

1. `common` has no Gradle imports.
2. `common` has no Maven imports.
3. `gradle` has no Maven imports.
4. `maven` has no Gradle imports.
5. domain/application have no Gradle/Maven imports.
6. Maven Mojo calls `BtmGenerationRunner`.
7. Gradle task calls `BtmGenerationRunner`.
8. Scanner logic is not duplicated.

### Phase 4 — Commit preparation

Create a commit message that explains:

1. what changed
2. why it changed
3. how it changed
4. affected files/components
5. tests added/updated
6. behavior changes
7. limitations
8. breaking changes if any

### Phase 5 — Commit

Stage only relevant files.
Create the commit.
Do not push unless explicitly requested.

## Commit message format

Use a clear multi-line commit message:

```text
refactor: split BTM generation into shared runner and build-tool adapters

Extract build-tool-neutral BTM generation orchestration from the Gradle task
into a shared runner so Gradle and Maven integrations can use the same scanner,
renderer, writer, profiling, and cache wiring.

Changed:
- add plugin/btmgen/common request/result/runner types
- reduce GenerateBtmTask to a Gradle adapter
- add Maven Mojo adapter for btmgen goal
- add architecture rules for Gradle/Maven adapter boundaries
- document Gradle and Maven usage

Why:
- avoid duplicating scanner logic for Maven support
- allow Maven-based projects to use the plugin directly later
- keep build-tool-specific code isolated

Validation:
- <insert actual commands and results>

Limitations:
- Maven reactor aggregation is not implemented yet
- external large-project benchmark is not part of this commit
````

Adjust the message to actual changes.

## Restrictions

Do not stage unrelated files.
Do not commit generated build outputs.
Do not commit local cache DB files.
Do not commit external benchmark repository files.
Do not suppress failing tests.
Do not push unless asked.

## Final response

Return:

1. Commit hash.
2. Quality gate result.
3. Test commands run.
4. Summary of changed files.
5. Known limitations.
6. Whether anything was not committed and why.

````

## Akzeptanzkriterien

- Quality Gate ist gelaufen.
- Diff wurde geprüft.
- Commit ist sauber und nachvollziehbar.
- Keine generierten Artefakte committed.

---

# Empfohlene Commit-Schnittfolge

Nicht alles in einen riesigen Commit drücken.

Besser:

```text
Commit 1: add common BTM generation request/result model
Commit 2: extract BTM generation runner from Gradle task
Commit 3: harden Gradle adapter boundaries
Commit 4: add Maven plugin build support
Commit 5: add Maven btmgen Mojo adapter
Commit 6: add architecture tests for plugin adapters
Commit 7: document Gradle/Maven adapter usage
````

Wenn Codex gut durchläuft, kann man 1–3 zusammenfassen, aber Maven sollte eher separat bleiben.

---

# Master-Prompt für Codex-Orchestrierung

Falls du Codex einen Gesamtauftrag geben willst, aber trotzdem Slice-förmig arbeiten lassen möchtest:

````md
# Master Task: Split BTM generation into shared runner plus Gradle and Maven adapters

## Context

Repository: forensics_tracing
Runtime: Java 17 only
Build system: Gradle 9.4.0 only
Architecture: Hexagonal architecture
Testing: JUnit 5 and ArchUnit
Source-code comments: English only.

The project currently provides a Gradle plugin for generating Byteman BTM rules from Java source code.

The next productization step is to support Maven projects without duplicating scanner logic.

## Target architecture

Create this build-tool adapter structure:

```text
src/main/java/de/burger/forensics/plugin/btmgen
├── common
│   ├── BtmGenerationRunner.java
│   ├── BtmGenerationRequest.java
│   ├── BtmGenerationResult.java
│   ├── BtmGenerationDefaults.java
│   └── PluginLogPort.java
│
├── gradle
│   └── existing Gradle plugin adapter classes
│
└── maven
    └── Maven Mojo adapter classes
````

The core rule is:

```text
GenerateBtmTask ─┐
                 ├── BtmGenerationRunner ─── Scanner / UseCase / Renderer / Writer
BtmGenMojo   ────┘
```

The Maven Mojo must not duplicate Gradle task scanner logic.

## Execution style

Work slice by slice.

For each slice:

1. Inspect relevant code.
2. Make the smallest coherent change.
3. Add/update tests.
4. Run relevant tests.
5. Inspect diff.
6. Stop and report before moving to the next slice if there is an architectural uncertainty.

## Required slices

### Slice 0 — Baseline

Inspect current repository, plugin task, quality gate, and current architecture.
Do not modify code.

### Slice 1 — Common model

Add build-tool-neutral request/result/default/log types.
No behavior change.

### Slice 2 — Runner extraction

Extract scanner/use-case/render/write orchestration from `GenerateBtmTask` into `BtmGenerationRunner`.
BTM output must remain identical.

### Slice 3 — Gradle adapter cleanup

Reduce `GenerateBtmTask` to a thin Gradle adapter.
No duplicated scanner orchestration.

### Slice 4 — Maven build support

Add minimum Maven plugin API/annotation support and descriptor generation strategy while keeping Gradle as the build system.
Do not convert the project to Maven.

### Slice 5 — Maven Mojo

Add `BtmGenMojo` under `plugin/btmgen/maven`.
It must call `BtmGenerationRunner`.
Support one goal: `btmgen`.

### Slice 6 — Architecture tests

Add ArchUnit rules:

* common must not depend on Gradle
* common must not depend on Maven
* Gradle adapter must not depend on Maven
* Maven adapter must not depend on Gradle
* domain/application must not depend on Gradle or Maven

### Slice 7 — Validation

Validate Gradle and Maven paths against the same fixture where feasible.
Document manual Maven validation if full integration is too heavy.

### Slice 8 — Documentation

Update README/AGENTS/QUALITY if needed.
Document Gradle usage, Maven usage, adapter boundaries, and limitations.

### Slice 9 — Final quality gate and commit

Run documented quality gate from `QUALITY.md`.
Inspect diff.
Commit only relevant changes.
Do not push unless explicitly requested.

## Hard restrictions

Do not add a second scanner implementation.
Do not call Gradle task from Maven Mojo.
Do not call Maven Mojo from Gradle task.
Do not import Gradle classes outside `plugin.btmgen.gradle`.
Do not import Maven classes outside `plugin.btmgen.maven`.
Do not leak Gradle/Maven types into domain/application/common.
Do not use Spring Boot.
Do not introduce Maven as project build system.
Do not lower coverage thresholds.
Do not remove tests.
Do not invent missing scripts.
Do not commit generated cache DBs, build outputs, or external repositories.

## Required final output

Return:

1. Summary of implemented slices.
2. Changed files.
3. Test commands and results.
4. Quality gate result.
5. Maven descriptor generation result.
6. Gradle plugin compatibility result.
7. Known limitations.
8. Commit hash if committed.

```

---

# Grenzen und Risiken

## Risiko 1: Single-Modul-JAR enthält Gradle- und Maven-Abhängigkeiten zusammen

Kurzfristig ist das akzeptabel, wenn Abhängigkeiten sauber scoped sind. Langfristig ist ein Multi-Modul-Build besser.

## Risiko 2: Maven-Plugin-Descriptor wird nicht erzeugt

Ein Maven-Mojo allein reicht nicht für komfortable Maven-Nutzung. Maven erwartet einen Plugin-Descriptor im Jar. Deshalb muss Descriptor-Generierung entweder sauber eingebaut oder als expliziter Folgeschritt dokumentiert werden.

## Risiko 3: Gradle-Task verliert Configuration-Cache-Fähigkeit

Task-Felder dürfen keine nicht serialisierbaren Parser-, Scanner-, Renderer- oder Maven-Objekte halten. Diese Objekte müssen während der Task-Ausführung erzeugt werden.

## Risiko 4: BTM-Ausgabe ändert sich

Jede Änderung am Runner muss gegen bestehende BTM-Tests abgesichert werden. Wenn die Reihenfolge instabil war, darf sie deterministisch gemacht werden, aber nur mit Testanpassung und klarer Begründung.

## Grenzen dieses Workflows

Dieser Workflow baut die Adapterstruktur. Er löst noch nicht:

- H2-/SQLite-Cache
- Dependency-aware invalidation
- external large-project benchmark
- CLI-Unterstützung
- Multi-Modul-Publishing
- Maven-Central-Release

Diese Punkte bleiben Folgearbeiten.

```
