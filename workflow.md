# Workflow: Migrate Forensics Core into `forensic_analytics` Engine

## Purpose

This workflow guides Codex through the controlled migration from a plugin-centric architecture into a dedicated Forensics Engine architecture.

The goal is not to move everything at once. The goal is to extract the reusable analysis core into `forensic_analytics`, while keeping `forensics_tracing` as a thin Gradle/Maven adapter.

## Repositories

Codex is expected to work with two local repositories:

```text
forensics_tracing      # Existing Gradle/Maven plugin project
forensic_analytics     # Target engine / analysis platform project
```

If one of these repositories is missing, Codex must stop and report the missing repository instead of guessing paths or creating an unrelated structure.

## Global Rules

* Use Java 25 for the Forensics core.
* Use JUnit 6 for all tests.
* Use Gradle 9.4.0 only.
* Do not perform a big-bang migration.
* Do not delete working functionality without a replacement plan.
* Do not move Gradle or Maven plugin adapter code into the Engine unless explicitly required by this workflow.
* Source code and source comments must be written in English.
* User-facing reports must be written in German.
* Preserve hexagonal architecture.
* Domain modules must not depend on frameworks, Gradle, Maven, Joern CLI, Docker, gRPC, Spring, persistence, or UI technologies.
* Application modules may depend on domain ports and use cases, but not on concrete infrastructure implementations.
* Infrastructure adapters must depend inward on application/domain contracts.
* If a class, method, responsibility, package, or architectural boundary is unclear, stop and report.
* Do not silently guess.
* Do not lower coverage thresholds.
* Do not disable failing tests.
* Do not remove tests to make the build pass.
* Every behavior-relevant migration must have regression tests.

## Target Architecture

The target architecture separates the system into:

```text
forensics_tracing
  -> Gradle plugin adapter
  -> Maven plugin adapter
  -> build-system integration
  -> optional BTM generation adapter
  -> later: gRPC client / Engine client

forensic_analytics
  -> Forensics Engine
  -> repository checkout / source acquisition
  -> JavaParser / AST analysis
  -> Joern / CPG analysis via container adapter
  -> graph / replay / report model
  -> persistence
  -> gRPC ingestion
  -> CLI
  -> server / API
  -> later: UI integration
```

The plugin must become a producer or client. The Engine must become the owner of analysis orchestration.

## High-Level Migration Strategy

The migration must be done in slices:

```text
1. Inspect both repositories
2. Classify existing responsibilities
3. Create a migration work plan
4. Prepare the Engine module structure
5. Move neutral domain contracts
6. Move application-level analysis orchestration
7. Add Joern Docker adapter to the Engine
8. Add repository checkout/source acquisition adapter
9. Add CLI entry point
10. Add gRPC ingestion contract/module
11. Reduce plugin logic to adapter/client role
12. Add external E2E testbed
13. Verify complete quality gates
```

Each slice must compile and be verifiable before continuing.

---

# Phase 0: Safety Preparation

## 0.1 Create or verify branches

Before any code changes, inspect branches in both repositories.

```bash
git status
git branch --show-current
```

If the current branch is not appropriate for a migration, create a dedicated branch.

Recommended branch names:

```text
forensic_analytics: feature/forensics-engine-foundation
forensics_tracing:  feature/extract-engine-boundary
```

## 0.2 Inspect working tree state

Run in both repositories:

```bash
git status --short
git diff --stat
git diff
```

If unrelated changes exist, stop and report them. Do not overwrite or mix them into this migration.

## 0.3 Verify Java and Gradle

Run in both repositories:

```bash
java -version
./gradlew --version
```

Expected:

* Java 25 runtime suitable for the project baseline.
* Gradle Wrapper available.
* Gradle 9.4.0 according to project constraints.
* JUnit 6 is used for tests.

If the wrapper is missing or the Gradle version differs, stop and report.

---

# Phase 1: Repository Inspection

## 1.1 Inspect `forensics_tracing`

Inspect at least:

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
QUALITY.md
AGENTS.md
README.md
src/main/java
src/test/java
```

Also inspect any plugin-specific source sets or packages containing:

```text
Gradle plugin implementation
Maven Mojo implementation
BTM generation
JavaParser scanning
Joern integration
Analysis Store
Semantic analysis
Manifest/checksum logic
Report/export logic
Domain models
Application services
```

## 1.2 Inspect `forensic_analytics`

Inspect at least:

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
QUALITY.md
AGENTS.md
README.md
src/main/java
src/test/java
```

If it is already a multi-module project, inspect all modules.

Special attention:

```text
gRPC ingestion module
server/bootstrap module
existing domain/application packages
persistence setup
CLI/server entry points
Docker/testbed directories
```

## 1.3 Produce inspection report

Create or update this file in `forensic_analytics`:

```text
docs/migration/INSPECTION_REPORT.md
```

The report must be written in German and include:

```text
1. Current structure of forensics_tracing
2. Current structure of forensic_analytics
3. Existing reusable core logic
4. Existing plugin-only logic
5. Existing Engine/server logic
6. Open architectural questions
7. First risk assessment
```

Do not move code during Phase 1.

---

# Phase 2: Responsibility Classification

Create this file in `forensic_analytics`:

```text
docs/migration/RESPONSIBILITY_MAPPING.md
```

Classify every relevant responsibility from `forensics_tracing` into one of the following categories.

## Category A: Must stay in `forensics_tracing`

Examples:

```text
Gradle plugin id declaration
Gradle plugin implementation class
Gradle task classes
Gradle extension classes
Gradle TestKit tests
Maven Mojo classes
Maven plugin descriptor configuration
Maven plugin harness tests
Plugin publishing configuration
Build lifecycle integration
Consumer-project task wiring
Local plugin output configuration
```

## Category B: Must move to `forensic_analytics`

Examples:

```text
Analysis domain model
Analysis request/result model
Engine-neutral scan event model
Semantic analysis abstractions
Joern abstraction contracts
BTM rule domain model if not plugin-specific
Repository analysis orchestration
Analysis persistence abstraction
Graph/replay concepts
Report/export model
Finding model
Correlation/replay event model
Analysis profile model
Repository checkout model
```

## Category C: Temporary duplication allowed

Use only when necessary to avoid breaking the plugin while the Engine boundary is created.

Examples:

```text
DTOs during gRPC boundary migration
Simple value objects before shared contract exists
Compatibility models used by both repositories during transition
```

Temporary duplication must include a removal note.

## Category D: Later shared contract

Examples:

```text
gRPC request/response DTOs
Plugin-to-Engine request model
Analysis result upload model
Artifact reference model
Correlation event model
Repository metadata model
Analysis profile schema
```

## Category E: Delete later

Only classify something here if there is a verified replacement plan.

Examples:

```text
Obsolete plugin-internal orchestration after Engine extraction
Duplicate Joern command resolution after Docker adapter exists
Deprecated local output models replaced by Engine artifact references
```

## Category F: Unclear / requires decision

Any unclear item must be listed here with a concrete question.

Do not proceed to code migration while important items remain unclear.

---

# Phase 3: Migration Work Plan

Create this file in `forensic_analytics`:

```text
MIGRATION_WORKPLAN.md
```

The work plan must be written in German.

It must contain:

```text
1. Goal
2. Non-goals
3. Current architecture
4. Target architecture
5. Repository responsibility split
6. Proposed module structure
7. Slice plan
8. Verification strategy
9. Rollback strategy
10. Known risks
11. Decisions required from the user
12. First implementation slice
```

## Required target module proposal

The proposal should evaluate these modules:

```text
forensic-analytics-domain
forensic-analytics-application
forensic-analytics-engine
forensic-analytics-ingestion-grpc
forensic-analytics-adapter-git
forensic-analytics-adapter-build-gradle
forensic-analytics-adapter-build-maven
forensic-analytics-adapter-javaparser
forensic-analytics-adapter-joern-docker
forensic-analytics-adapter-byteman
forensic-analytics-persistence
forensic-analytics-cli
forensic-analytics-server
forensic-analytics-bootstrap
testbed
```

Do not create all modules automatically. First justify which modules are needed immediately and which should wait.

---

# Phase 4: Prepare Engine Foundation

This is the first implementation phase.

Only implement the minimum foundation needed for the Engine boundary.

## 4.1 Module creation

In `forensic_analytics`, create only the modules needed for the first vertical slice.

Recommended minimal start:

```text
forensic-analytics-domain
forensic-analytics-application
forensic-analytics-engine
forensic-analytics-cli
```

Optional if already planned and required:

```text
forensic-analytics-ingestion-grpc
```

Do not create empty vanity modules without tests or clear purpose.

## 4.2 Dependency direction

The dependency direction must be:

```text
cli -> engine -> application -> domain
```

Forbidden:

```text
domain -> application
domain -> engine
domain -> cli
domain -> infrastructure
domain -> Gradle
domain -> Maven
domain -> Joern
domain -> Docker
domain -> Spring
domain -> persistence
```

## 4.3 Minimal domain contracts

Create only minimal contracts if needed, for example:

```text
AnalysisId
RepositoryLocation
RepositoryRevision
AnalysisProfile
AnalysisRequest
AnalysisResult
AnalysisFinding
AnalysisArtifact
```

Rules:

* Keep them framework-free.
* Prefer immutable value objects or records where appropriate.
* Do not model infrastructure concerns in the domain.
* Comments must be in English.

## 4.4 Minimal application contracts

Create use-case ports only if needed, for example:

```text
RunRepositoryAnalysisUseCase
LoadRepositoryPort
StoreAnalysisResultPort
RunSemanticAnalysisPort
GenerateInstrumentationRulesPort
```

Do not implement Joern, Git, Docker, Maven, or Gradle behavior here.

## 4.5 Verification

Run in `forensic_analytics`:

```bash
./gradlew clean test
./gradlew check
```

If `QUALITY.md` defines a stricter gate, run the stricter command.

Create/update:

```text
docs/migration/SLICE_01_ENGINE_FOUNDATION_RESULT.md
```

The result must include:

```text
1. What was created
2. Why it was created
3. Files changed
4. Tests added
5. Commands executed
6. Result
7. Open risks
```

---

# Phase 5: Move Engine-Neutral Domain Model

Only begin this phase after Phase 4 passes.

## 5.1 Identify movable domain classes

From `forensics_tracing`, identify classes that are independent of:

```text
Gradle
Maven
Joern CLI
Docker
File system specifics
ProcessBuilder
JUnit/TestKit
Plugin lifecycle
```

Candidate areas:

```text
Analysis request/result model
Scan event model
Finding model
Artifact model
Rule model
Manifest/checksum model if engine-neutral
Correlation/replay model
```

## 5.2 Move with compatibility strategy

Preferred order:

```text
1. Copy domain class into forensic_analytics
2. Add tests in forensic_analytics
3. Verify compile/test
4. Mark original location in forensics_tracing as pending extraction
5. Only remove or replace original after adapter boundary exists
```

Do not immediately break the plugin project.

## 5.3 Tests

For every migrated domain model, add tests covering:

```text
valid construction
invalid construction
identity/equality if relevant
serialization expectations if relevant
edge cases found in existing tests
```

## 5.4 Verification

Run in `forensic_analytics`:

```bash
./gradlew clean test
./gradlew check
```

Run in `forensics_tracing` to ensure it still works:

```bash
./gradlew clean test
./gradlew check
```

If `QUALITY.md` in either repository defines stricter commands, use them.

Create/update:

```text
docs/migration/SLICE_02_DOMAIN_MIGRATION_RESULT.md
```

---

# Phase 6: Move Application-Level Orchestration

Only begin after domain model migration is stable.

## 6.1 Identify orchestration logic

Find orchestration logic in `forensics_tracing` that is not inherently Gradle/Maven-specific.

Candidate responsibilities:

```text
Run complete analysis
Select source roots
Collect scan events
Generate analysis result
Create artifacts
Coordinate semantic analysis
Coordinate BTM generation
Build analysis manifest
```

Do not move plugin lifecycle code.

## 6.2 Define application use case

In `forensic_analytics`, introduce a use case similar to:

```text
RunRepositoryAnalysisUseCase
```

The use case should coordinate ports, not concrete adapters.

Possible ports:

```text
RepositorySourcePort
JavaSourceScannerPort
SemanticAnalysisPort
InstrumentationRuleGeneratorPort
AnalysisResultStorePort
AnalysisArtifactWriterPort
```

## 6.3 Adapter neutrality rule

The application layer must not call:

```text
ProcessBuilder
docker
joern
mvn
gradle
git command line directly
file-system-specific code unless behind a port
```

## 6.4 Tests

Add application tests using fake adapters.

Required test scenarios:

```text
analysis request is accepted
source roots are passed to scanner port
scanner results are included in result
semantic analysis can be disabled
semantic analysis can be enabled through port
artifact references are returned
failures are reported clearly
```

## 6.5 Verification

Run both repository gates again.

Create/update:

```text
docs/migration/SLICE_03_APPLICATION_ORCHESTRATION_RESULT.md
```

---

# Phase 7: Add Joern Docker Adapter to `forensic_analytics`

This phase introduces the Joern container approach.

## 7.1 Goal

Joern must not depend on the host JDK.

The Engine must support a Docker-based Joern adapter so that large projects such as WildFly can be analyzed without relying on local Windows, WSL, or host-JDK setup.

## 7.2 Module

Create only if not already present:

```text
forensic-analytics-adapter-joern-docker
```

## 7.3 Configuration model

Create an infrastructure configuration model, not a domain model, for:

```text
container image
container digest or tag
workspace mount
output mount
heap setting
timeout
additional Joern arguments
```

Example conceptual properties:

```properties
forensics.joern.mode=container
forensics.joern.container.image=ghcr.io/joernio/joern:<pinned-version>
forensics.joern.heap=16G
forensics.joern.workspace=/workspace
forensics.joern.output=/forensics-output
```

Do not use `latest` or `nightly` as final production default.

## 7.4 Docker command construction

The adapter must build commands in a testable way.

Do not hide command construction inside untestable string concatenation.

Preferred design:

```text
JoernDockerCommandBuilder
JoernDockerRunner
JoernDockerSemanticAnalysisAdapter
```

Tests must verify command construction without requiring Docker.

## 7.5 Initial behavior

Support at least:

```text
joern --version
joern-parse against mounted workspace
output artifact path handling
```

## 7.6 Tests

Required unit tests:

```text
builds docker command with workspace mount
builds docker command with output mount
adds heap setting
uses pinned image
rejects missing workspace path
rejects missing image
reports non-zero exit clearly
```

Optional integration test:

```text
run joern --version in container
```

This test must be disabled or tagged unless Docker availability is explicitly verified.

## 7.7 Verification

Run:

```bash
./gradlew clean test
./gradlew check
```

Create/update:

```text
docs/migration/SLICE_04_JOERN_DOCKER_ADAPTER_RESULT.md
```

---

# Phase 8: Add Repository Source Adapter

## 8.1 Goal

The Engine must be able to analyze an external repository without requiring the repository to execute the Forensics plugin itself.

## 8.2 Module

Create only if needed:

```text
forensic-analytics-adapter-git
```

## 8.3 Responsibilities

The adapter should support:

```text
local repository path
optional remote clone URL
branch selection
tag selection
commit selection
checkout into controlled workspace
clean workspace strategy
```

Initial implementation may support local path only.

## 8.4 Tests

Required tests:

```text
accepts existing local repository path
rejects missing repository path
normalizes repository path
returns repository metadata
```

Optional later tests:

```text
clones remote repository
checks out branch
checks out tag
checks out commit
cleans workspace
```

## 8.5 Verification

Create/update:

```text
docs/migration/SLICE_05_REPOSITORY_ADAPTER_RESULT.md
```

---

# Phase 9: Add CLI Entry Point

## 9.1 Goal

The first usable Engine entry point should be a CLI command.

Conceptual target:

```bash
./gradlew :forensic-analytics-cli:run --args="analyze --repo D:\Projects\wildfly --profile joern-btm"
```

or packaged form:

```bash
forensic-analytics analyze --repo D:\Projects\wildfly --profile joern-btm
```

## 9.2 Required CLI commands

Initial command:

```text
analyze
```

Required options:

```text
--repo
--profile
--output
--joern-mode
```

Optional options:

```text
--branch
--commit
--joern-image
--joern-heap
--timeout
```

## 9.3 CLI must not contain business logic

The CLI may:

```text
parse arguments
build application request
call use case
print result
return process exit code
```

The CLI must not:

```text
scan Java files directly
run Joern directly
write persistence directly
implement analysis rules
```

## 9.4 Tests

Required tests:

```text
parses valid analyze command
rejects missing repo
rejects missing profile
returns non-zero exit code on failed analysis
prints artifact location on success
```

## 9.5 Verification

Create/update:

```text
docs/migration/SLICE_06_CLI_RESULT.md
```

---

# Phase 10: Prepare gRPC Ingestion Boundary

This phase is important because the plugin will later send data to the Engine.

## 10.1 Module

Use or create:

```text
forensic-analytics-ingestion-grpc
```

## 10.2 Responsibilities

The gRPC ingestion module should receive analysis data from build adapters.

Possible messages:

```text
AnalysisSessionStarted
RepositoryMetadataSubmitted
SourceRootSubmitted
ScanEventSubmitted
RuleArtifactSubmitted
CoverageArtifactSubmitted
TraceEventSubmitted
AnalysisSessionCompleted
AnalysisSessionFailed
```

## 10.3 Boundary rule

The gRPC DTOs are transport contracts. They must not leak into the domain as mutable infrastructure objects.

Mapping required:

```text
gRPC DTO -> application command -> domain model
```

## 10.4 Tests

Required tests:

```text
maps ingestion request into application command
rejects invalid repository metadata
rejects missing analysis id
handles completed session
handles failed session
```

## 10.5 Verification

Create/update:

```text
docs/migration/SLICE_07_GRPC_INGESTION_RESULT.md
```

---

# Phase 11: Reduce `forensics_tracing` to Adapter Role

Only begin this phase after the Engine can run at least one local analysis path.

## 11.1 Goal

`forensics_tracing` should become a build adapter.

It may still generate local BTM files if required, but the core analysis logic should be owned by `forensic_analytics`.

## 11.2 Plugin responsibilities after migration

Allowed responsibilities:

```text
Gradle task registration
Maven Mojo registration
read plugin extension/configuration
collect project metadata
collect source roots
collect build output directories
invoke local Engine CLI or Engine client
optionally upload data via gRPC
write generated artifacts to build/target directories
report user-facing build errors
```

Forbidden responsibilities after migration:

```text
owning semantic analysis core
owning Joern runtime orchestration
owning repository checkout orchestration
owning graph/replay persistence
owning UI/server behavior
```

## 11.3 Compatibility mode

Do not break existing users immediately.

Provide one of:

```text
legacy local mode
engine mode disabled by default
engine mode opt-in
clear deprecation path
```

## 11.4 Tests

In `forensics_tracing`, update or add tests proving:

```text
Gradle task still registers
Maven goal still registers
legacy behavior still works if kept
engine request is built correctly
source roots are sent correctly
output paths are mapped correctly
clear error if Engine is unavailable
```

## 11.5 Verification

Run the full quality gate in `forensics_tracing`.

Create/update:

```text
docs/migration/SLICE_08_PLUGIN_ADAPTER_RESULT.md
```

---

# Phase 12: External E2E Testbed

## 12.1 Goal

Create a black-box testbed that proves the Engine works against external repositories.

This must be independent from normal unit tests.

## 12.2 Recommended structure

In `forensic_analytics`:

```text
testbed/
├── README.md
├── consumers/
│   ├── legacy-demo-shop/
│   └── wildfly-runner/
├── docker/
│   └── joern-worker/
├── python/
│   ├── requirements.txt
│   ├── pytest.ini
│   ├── tests/
│   │   ├── test_engine_against_legacy_demo_shop.py
│   │   ├── test_engine_against_wildfly_smoke.py
│   │   └── test_generated_artifacts.py
│   └── tools/
│       ├── command_runner.py
│       ├── workspace.py
│       └── artifact_assertions.py
```

## 12.3 Python test responsibilities

Python should orchestrate black-box checks:

```text
prepare temporary workspace
run Engine CLI
run Joern container if enabled
collect logs
verify generated artifacts
verify result metadata
verify non-empty reports
verify error diagnostics
```

## 12.4 Required first E2E scenario

```text
Input: local legacy-demo-shop repository
Mode: no Joern or Joern disabled
Expected: Engine runs AST/BTM analysis and creates artifacts
```

## 12.5 Required second E2E scenario

```text
Input: local legacy-demo-shop repository
Mode: Joern container enabled
Expected: Joern version check succeeds and semantic analysis step is invoked
```

## 12.6 WildFly scenario

WildFly must start as smoke/performance scenario, not as mandatory unit gate.

Expected first WildFly scenario:

```text
Input: D:\Projects\wildfly or /mnt/d/Projects/wildfly
Mode: Joern container enabled
Expected: Engine starts, validates repository, starts Joern container, reports progress or clear resource limitation
```

Do not require full WildFly semantic analysis in the normal quality gate until runtime and memory requirements are known.

---

# Phase 13: Documentation Update

Update documentation in both repositories.

## 13.1 `forensic_analytics` documentation

Must explain:

```text
What the Engine is
How to run local analysis
How to configure Joern container mode
How to analyze an external repository
Where artifacts are written
How to run E2E testbed
Architecture overview
Module overview
```

## 13.2 `forensics_tracing` documentation

Must explain:

```text
Plugin is now a build adapter
Standalone Engine lives in forensic_analytics
How to run legacy/local plugin mode
How to enable Engine mode if available
How gRPC upload will work later
```

## 13.3 Migration documentation

Update:

```text
docs/migration/MIGRATION_STATUS.md
```

Include:

```text
completed slices
open slices
known risks
compatibility status
next recommended step
```

---

# Phase 14: Final Verification

Run the full quality gate in both repositories.

## 14.1 `forensic_analytics`

```bash
./gradlew clean test
./gradlew check
```

The test suite must run on JUnit 6.

If `QUALITY.md` defines a stricter gate, run that stricter gate.

## 14.2 `forensics_tracing`

```bash
./gradlew clean test
./gradlew check
```

The test suite must run on JUnit 6.

If `QUALITY.md` defines a stricter gate, run that stricter gate.

## 14.3 Optional plugin verification

If the plugin still publishes locally:

```bash
./gradlew publishToMavenLocal
```

Then run a consumer smoke test if available.

## 14.4 Optional E2E testbed

From the Python testbed:

```bash
python -m pytest
```

If Docker is required and unavailable, report it as an environment blocker.

---

# Required Stop Conditions

Codex must stop and report if any of the following occurs:

```text
Repository not found
Unexpected Gradle version
Missing Gradle Wrapper
Unknown module structure
Unclear ownership of a class or package
Domain dependency would point outward
Migration would require deleting functionality without replacement
Tests fail for unknown reason
Coverage gate fails and root cause is unclear
Docker is required but unavailable
Joern image cannot be resolved
WildFly analysis requires resources beyond available environment
```

The report must include:

```text
what was attempted
where it failed
why continuing would be risky
what decision is needed
recommended next action
```

---

# Commit Strategy

Use small commits per slice.

Recommended commit style:

```text
feat(engine): add forensic analytics engine foundation
feat(domain): migrate analysis request model
feat(application): add repository analysis use case
feat(joern): add docker command builder
feat(cli): add analyze command
feat(ingestion): add grpc analysis session contract
refactor(plugin): prepare engine adapter boundary
test(e2e): add external repository analysis testbed
docs(migration): document engine migration status
```

Before each commit:

```bash
git status
git diff --stat
git diff
```

Run the relevant test gate for the touched repository.

---

# Final Report Template

At the end of each Codex run, write a German report:

```markdown
# Migration Report

## Ziel

## Bearbeiteter Slice

## Geänderte Dateien

## Architekturentscheidungen

## Tests

## Ausgeführte Commands

## Ergebnis

## Offene Risiken

## Blocker

## Nächster empfohlener Slice
```

---

# First Codex Execution Task

For the first execution, Codex must not migrate code yet.

Start with:

```text
Phase 0
Phase 1
Phase 2
Phase 3
```

Expected first output:

```text
forensic_analytics/docs/migration/INSPECTION_REPORT.md
forensic_analytics/docs/migration/RESPONSIBILITY_MAPPING.md
forensic_analytics/MIGRATION_WORKPLAN.md
```

Only after these files are reviewed should implementation begin with Phase 4.
