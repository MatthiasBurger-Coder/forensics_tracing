# workflow.md — Build Tool Connector Feature Parity

## 1. Goal

Ensure that the Maven plugin connector and the Gradle plugin connector expose the same forensic analysis capabilities.

The build tool must not decide which forensic capabilities are available. Gradle and Maven are inbound adapters only. All forensic behavior must live in build-tool-neutral core/application services and must be invoked through the same request/result model.

Target rule:

```text
Gradle project analysis == Maven project analysis
Gradle multi-project analysis == Maven reactor analysis
Gradle Joern enrichment == Maven Joern enrichment
Gradle Analysis Store output == Maven Analysis Store output
```

Allowed differences:

```text
Gradle API mapping
Gradle task registration
Gradle extension/provider types
Gradle SourceSet discovery

Maven API mapping
Maven Mojo parameters
MavenSession / MavenProject access
Maven reactor project discovery
Maven lifecycle binding
```

Forbidden differences:

```text
BTM generation capabilities
Analysis Store capabilities
Joern semantic enrichment capabilities
Manifest/checksum generation
Build identity semantics
Source root aggregation semantics
Include/exclude semantics
Test source inclusion semantics
Profiling/cache behavior
Generated artifact structure
Quality gate requirements
```

## 2. Architectural decision

Introduce an explicit **Build Tool Connector Feature Parity** rule.

Decision:

```text
Every forensic capability that is exposed by one build-tool connector must be exposed by every supported build-tool connector, unless it is explicitly documented as build-tool-specific and approved as an exception.
```

Rationale:

```text
The project analyzes Java source code and generated forensic artifacts.
Gradle and Maven are only access paths into the same analysis engine.
A Maven project must not receive weaker forensic analysis than a Gradle project.
A Gradle project must not receive weaker forensic analysis than a Maven project.
```

Implication:

```text
New capabilities must be implemented in build-tool-neutral core/application code first.
Gradle and Maven adapters may only map their build-tool-specific configuration into the shared request model.
Parity tests are mandatory for every public connector capability.
```

## 3. Non-goals

Do not implement unrelated platform features in this workflow.

Not part of this workflow:

```text
gRPC upload
forensic_analytics server ingestion
runtime replay engine
LLM context generation
graph database export
vector store export
automatic bug fixing
automatic deployment
new public tracing API design
```

Do not rewrite the whole package structure unless the current source state already requires it.

Do not weaken existing quality rules, coverage thresholds, architecture tests, or dependency verification.

## 4. Technical baseline

Use the project-approved baseline.

Current project decision for this workflow:

```text
JDK: 17
Gradle: 9.1
JUnit: 5
ArchUnit: enabled
JaCoCo: enabled
Architecture: hexagonal
```

If repository files disagree about the baseline, stop and report before implementing functional changes.

Baseline alignment must be handled deliberately. Do not silently upgrade or downgrade Java, Gradle, Maven plugin APIs, JaCoCo, SonarQube plugins, or dependency versions while implementing connector parity.

## 5. Required connector capability matrix

Add and maintain a documented parity matrix.

Minimum capabilities:

```text
Capability                                  Gradle   Maven   Notes
BTM generation                              yes      yes     Same runner/use case
Single module/project scan                  yes      yes     Same source-root semantics
Multi-module/project aggregation            yes      yes     Gradle subprojects / Maven reactor
Explicit sourceRoots                        yes      yes     Deterministic de-duplication
Main source roots                           yes      yes     SourceSet / compileSourceRoots
Test source roots                           yes      yes     includeTests flag
Include package/class prefixes              yes      yes     Same matching semantics
Exclude package/class prefixes              yes      yes     Same matching semantics
Strict parsing                              yes      yes     Same failure behavior
Strict condition validation                 yes      yes     Same warning/fail behavior
Dependency-aware invalidation/cache          yes      yes     Same cache model
Profiling report                            yes      yes     Same profile format
includeEntryExit                            yes      yes     Same rule semantics
minBranchesPerMethod                        yes      yes     Same filter semantics
includeTimestampHeader                      yes      yes     Same deterministic/cache behavior
Analysis Store                              yes      yes     Same schema/artifacts
Analysis Store cleanup policy               yes      yes     Same policies
Manifest generation                         yes      yes     Same schema
Checksum generation                         yes      yes     Same checksum rules
BuildIdentity / AnalysisRunId               yes      yes     Same identity model
Joern enable/disable                        yes      yes     Default disabled
Joern CLI executable configuration          yes      yes     Same external adapter
Joern semantic artifact generation          yes      yes     Same artifacts
Joern import into Analysis Store            yes      yes     Same tables
Semantic anchor matching                    yes      yes     Same matching rules
Full analysis aggregate command             yes      yes     Gradle task / Maven goal
Clean generated analysis artifacts          yes      yes     Same cleanup behavior
```

Rules:

```text
- A capability may not be Gradle-only by default.
- A capability may not be Maven-only by default.
- If a capability is temporarily missing, it must be documented as a known gap and must fail a TODO/disabled parity test with a tracking reason.
- New public connector parameters require a parity check.
```

## 6. Required AGENTS.md extension

Update `AGENTS.md` with a dedicated section.

Suggested insertion location:

```text
Architecture Rules
  -> Build Tool Plugins
  -> Build Tool Connector Feature Parity
```

Suggested text:

````markdown
### Build Tool Connector Feature Parity

Gradle and Maven connectors are inbound adapters for the same forensic analysis capabilities.

The following rule is mandatory:

```text
If a forensic capability is exposed through the Gradle connector, the Maven connector must expose the same capability.
If a forensic capability is exposed through the Maven connector, the Gradle connector must expose the same capability.
````

Allowed differences are limited to build-tool-specific mapping and lifecycle integration:

```text
Gradle: Project, Task, Extension, Provider API, SourceSet, Gradle layout
Maven: MavenProject, MavenSession, Mojo parameters, ReactorProjects, Maven lifecycle
```

Forbidden differences:

```text
BTM generation behavior
Analysis Store behavior
Joern semantic enrichment behavior
Manifest/checksum structure
BuildIdentity semantics
Source root aggregation semantics
Include/exclude semantics
Validation behavior
Generated artifact structure
```

Agents must not implement new Gradle-only or Maven-only forensic capabilities unless the task explicitly defines a temporary exception.

Every new connector capability must be implemented in this order:

1. build-tool-neutral request/result model
2. application/core service or runner behavior
3. Gradle adapter mapping
4. Maven adapter mapping
5. parity test proving equivalent behavior
6. README/QUALITY documentation update

Gradle task classes must not call Maven Mojo classes.
Maven Mojo classes must not call Gradle task classes.
Both adapters must delegate to shared build-tool-neutral services.

Maven reactor analysis must provide the same repository-level analysis capability as Gradle multi-project analysis.
A Maven root project with `pom` packaging must be usable as an aggregation context and must not fail only because the root POM has no source roots.

If the agent finds that a capability exists only in one connector, the agent must either:

* implement the missing connector mapping in the same workflow, or
* stop and report the parity gap with the affected files, tests, and documented behavior.

````

Acceptance criteria:

```text
[ ] AGENTS.md explicitly requires Gradle/Maven connector feature parity.
[ ] AGENTS.md forbids hidden Gradle-only or Maven-only forensic capabilities.
[ ] AGENTS.md requires parity tests for new connector features.
[ ] AGENTS.md states that Maven reactor aggregation must match Gradle multi-project aggregation.
````

## 7. Required QUALITY.md extension

Update `QUALITY.md` with a dedicated quality gate section.

Suggested text:

````markdown
## Build Tool Connector Parity Gate

Gradle and Maven connector behavior must remain equivalent for all forensic capabilities.

The quality gate must verify:

```text
- shared request model is build-tool-neutral
- Gradle adapter maps extension/task configuration into the shared request model
- Maven adapter maps Mojo/Reactor configuration into the shared request model
- Gradle and Maven produce equivalent BTM output for the same Java sources
- Gradle and Maven produce equivalent Analysis Store artifacts when enabled
- Gradle and Maven produce equivalent manifest/checksum metadata
- Gradle and Maven expose equivalent Joern configuration when Joern support is available
- Maven reactor aggregation matches Gradle multi-project aggregation semantics
````

Required local verification:

```bash
./gradlew test --tests '*BtmGenerationAdapterValidationTest' --console=plain --stacktrace
./gradlew test --tests '*BuildToolConnectorParityTest' --console=plain --stacktrace
./gradlew test --tests '*MavenReactorAggregationTest' --console=plain --stacktrace
./gradlew test --tests '*HexagonRulesTest' --console=plain --stacktrace
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --console=plain --stacktrace
./gradlew validatePlugins --console=plain --stacktrace
```

If Maven plugin descriptors or Maven integration behavior are changed, the verification must also include a Maven fixture run from a generated test project or an integration-test fixture.

Agents must not claim connector parity unless the parity tests were executed or the reason for not executing them is explicitly reported.

````

Acceptance criteria:

```text
[ ] QUALITY.md contains a connector parity quality gate.
[ ] QUALITY.md lists targeted parity tests.
[ ] QUALITY.md keeps the existing full local quality gate intact.
[ ] QUALITY.md does not replace the current quality gate with a weaker command.
````

## 8. Slices

### Slice 0 — Preflight and current-state verification

Goal:

Verify the actual repository state before changing files.

Commands:

```bash
git status --short
git diff --stat
rg -n "BtmGenerationRunner|BtmGenerationRequest|BtmGenMojo|GenerateBtmTask|AnalyzeForensicsSemanticsTask|forensicsAnalyze|joernEnabled|analysisStoreEnabled|MavenSession|reactor" src/main src/test README.md AGENTS.md QUALITY.md build.gradle.kts settings.gradle.kts gradle || true
./gradlew test --console=plain --stacktrace
```

Inspect:

```text
AGENTS.md
QUALITY.md
README.md
src/main/java/**/plugin/**/common/**
src/main/java/**/plugin/**/gradle/**
src/main/java/**/plugin/**/maven/**
src/test/java/**/BtmGenerationAdapterValidationTest.java
src/test/java/**/quality/**
```

Stop if:

```text
- AGENTS.md and README.md disagree about Java/Gradle baseline.
- BtmGenerationRunner does not exist or has incompatible semantics.
- Maven adapter currently bypasses the shared runner.
- Existing Analysis Store or Joern implementation differs from the assumed model.
- Maven support is located outside the expected plugin adapter package.
```

Acceptance criteria:

```text
[ ] Current connector capabilities are known.
[ ] Current Maven gaps are listed.
[ ] Current Gradle-only capabilities are listed.
[ ] No source changes were made in this slice.
```

### Slice 1 — Add mandatory parity rules to AGENTS.md and QUALITY.md

Goal:

Make connector parity a repository-level engineering rule before functional implementation begins.

Changes:

```text
AGENTS.md
QUALITY.md
```

Add:

```text
Build Tool Connector Feature Parity
Build Tool Connector Parity Gate
Maven reactor aggregation rule
Parity test requirement
Forbidden Gradle-only/Maven-only capability rule
```

Acceptance criteria:

```text
[ ] AGENTS.md says that Gradle and Maven connectors must expose the same forensic capabilities.
[ ] QUALITY.md requires parity tests.
[ ] Documentation uses English.
[ ] Existing STOP/no-guessing rules remain intact.
[ ] Existing quality gate remains at least as strict as before.
```

Verification:

```bash
./gradlew test --tests '*HexagonRulesTest' --console=plain --stacktrace
```

### Slice 2 — Create an explicit connector capability inventory

Goal:

Make all connector capabilities visible and testable.

Preferred implementation:

```text
src/main/java/de/burger/forensics/plugin/btmgen/common/ConnectorCapability.java
src/main/java/de/burger/forensics/plugin/btmgen/common/ConnectorCapabilityDescriptor.java
src/main/java/de/burger/forensics/plugin/btmgen/common/ConnectorCapabilityCatalog.java
```

The catalog should describe capabilities, not execute logic.

Example capabilities:

```text
BTM_GENERATION
SOURCE_ROOTS
MAIN_SOURCE_ROOTS
TEST_SOURCE_ROOTS
MULTI_MODULE_AGGREGATION
INCLUDE_FILTERS
EXCLUDE_FILTERS
STRICT_PARSING
STRICT_CONDITION_VALIDATION
SCAN_CACHE
PROFILING
ANALYSIS_STORE
MANIFEST
CHECKSUMS
CLEANUP_POLICY
JOERN_CONFIGURATION
JOERN_SEMANTIC_ANALYSIS
JOERN_IMPORT
FULL_ANALYSIS_AGGREGATE
```

Alternative:

If adding catalog classes is too heavy, create a test-only parity matrix first. However, production capability descriptors are preferred because they can later drive documentation and validation.

Acceptance criteria:

```text
[ ] Capability list exists in one authoritative place.
[ ] The list is not Gradle-specific.
[ ] The list is not Maven-specific.
[ ] Tests fail when a connector is missing a mandatory capability.
```

Verification:

```bash
./gradlew test --tests '*BuildToolConnectorParityTest' --console=plain --stacktrace
```

### Slice 3 — Introduce or consolidate the shared request model

Goal:

Ensure that both build-tool adapters map into the same build-tool-neutral request model.

Current candidate:

```text
BtmGenerationRequest
```

If `BtmGenerationRequest` is now too narrow, introduce a higher-level request:

```text
ForensicsAnalysisRequest
```

Minimum fields:

```text
sourceRoots
testSourceRoots
outputFile
cacheEnabled
cacheBackend
cacheDatabaseFile
profilingEnabled
profileReportFile
strictParsing
strictConditionValidation
dependencyAwareInvalidation
includePackages
excludePackages
includeTests
helperFqn
includeEntryExit
minBranchesPerMethod
includeTimestampHeader
analysisStoreEnabled
analysisStoreDirectory
cleanupPolicy
projectKey
manifestFile
checksumsFile
joernEnabled
joernExecutable
joernParseExecutable
joernSliceExecutable
joernWorkspaceDirectory
joernOutputDirectory
joernTimeoutSeconds
joernFailOnError
buildTool
buildRoot
moduleName
reactorRoot
```

Rules:

```text
- No Gradle API types in the shared request.
- No Maven API types in the shared request.
- No FileCollection, Property, Provider, MavenProject, or MavenSession in the request.
- Use Path, List<Path>, String, boolean, int, Duration, and domain value objects.
```

Acceptance criteria:

```text
[ ] Shared request can represent all Gradle connector capabilities.
[ ] Shared request can represent all Maven connector capabilities.
[ ] Request validation is deterministic and fail-fast.
[ ] Blank helper FQCN behavior remains unchanged.
[ ] Existing BTM generation output does not change unexpectedly.
```

Verification:

```bash
./gradlew test --tests '*BtmGenerationRequestTest' --console=plain --stacktrace
./gradlew test --tests '*BuildToolConnectorParityTest' --console=plain --stacktrace
```

### Slice 4 — Extract Gradle mapping into a dedicated adapter mapper

Goal:

Keep `GenerateBtmTask` and other Gradle tasks thin.

Preferred new class:

```text
src/main/java/de/burger/forensics/plugin/btmgen/gradle/GradleForensicsAnalysisRequestFactory.java
```

Responsibilities:

```text
- read Gradle extension/task properties
- collect Gradle source roots
- handle scanSubprojects
- normalize and de-duplicate roots
- map all Gradle connector properties into the shared request
```

Forbidden:

```text
- scanner logic
- rendering logic
- Analysis Store schema logic
- Joern process execution
- Maven API imports
```

Acceptance criteria:

```text
[ ] GenerateBtmTask delegates request creation.
[ ] Gradle mapping is unit-testable without running a full build.
[ ] Gradle source root ordering is deterministic.
[ ] Gradle adapter still uses Provider/Property APIs correctly.
[ ] Configuration-cache behavior is not made worse.
```

Verification:

```bash
./gradlew test --tests '*GenerateBtmTaskTest' --console=plain --stacktrace
./gradlew test --tests '*Gradle*Request*Test' --console=plain --stacktrace
./gradlew validatePlugins --console=plain --stacktrace
```

### Slice 5 — Extract Maven module mapping into a dedicated adapter mapper

Goal:

Keep `BtmGenMojo` thin and equivalent to the Gradle mapping layer.

Preferred new class:

```text
src/main/java/de/burger/forensics/plugin/btmgen/maven/MavenForensicsAnalysisRequestFactory.java
```

Responsibilities:

```text
- read Maven Mojo parameters
- read MavenProject compile source roots
- read MavenProject test compile source roots when includeTests=true
- normalize and de-duplicate roots
- map all Maven connector parameters into the shared request
```

Forbidden:

```text
- calling Gradle task classes
- duplicating scanner orchestration
- duplicating rendering orchestration
- silently ignoring supported shared request fields
```

Acceptance criteria:

```text
[ ] BtmGenMojo delegates request creation.
[ ] Maven module-local scan still works.
[ ] Missing source roots are handled like Gradle missing roots where applicable.
[ ] Maven parameter defaults match Gradle defaults unless explicitly documented.
[ ] Maven can set every shared capability that Gradle can set.
```

Verification:

```bash
./gradlew test --tests '*BtmGenMojoTest' --console=plain --stacktrace
./gradlew test --tests '*BtmGenerationAdapterValidationTest' --console=plain --stacktrace
./gradlew test --tests '*BuildToolConnectorParityTest' --console=plain --stacktrace
```

### Slice 6 — Implement Maven reactor source-root aggregation

Goal:

Give Maven the same repository-level analysis capability that Gradle has for multi-project builds.

Preferred design:

```text
src/main/java/de/burger/forensics/plugin/btmgen/maven/MavenReactorSourceRootCollector.java
src/main/java/de/burger/forensics/plugin/btmgen/maven/BtmGenAggregateMojo.java
```

Maven goal:

```text
forensics:btmgen-aggregate
```

Aggregation behavior:

```text
- use MavenSession.getProjects()
- treat the root POM as aggregation context
- do not fail only because root packaging is pom
- skip modules without existing source roots
- collect compile source roots from all reactor modules
- collect test compile source roots when includeTests=true
- de-duplicate roots deterministically
- preserve module identity for manifest/store metadata where possible
```

Root POM behavior:

```text
A root project with packaging=pom is valid for aggregation.
It is not required to have src/main/java.
It must not fail with "No existing Maven source roots" when reactor modules contain source roots.
```

Acceptance criteria:

```text
[ ] Maven reactor aggregation scans all module compile roots.
[ ] Maven reactor aggregation optionally scans test roots.
[ ] Aggregation handles root pom packaging safely.
[ ] Aggregation produces one coherent BTM output by default.
[ ] Aggregation produces the same kind of Analysis Store package as Gradle multi-project analysis.
```

Verification:

```bash
./gradlew test --tests '*MavenReactorSourceRootCollectorTest' --console=plain --stacktrace
./gradlew test --tests '*MavenReactorAggregationTest' --console=plain --stacktrace
```

### Slice 7 — Add Maven Analysis Store parity

Goal:

Maven must generate the same persistent analysis package as Gradle when Analysis Store is enabled.

Required Maven parameters:

```text
analysisStoreEnabled
analysisStoreDirectory
cleanupPolicy
projectKey
manifestFile
checksumsFile
```

Rules:

```text
- same default schema
- same BuildIdentity model
- same AnalysisRunId semantics
- same manifest schema
- same checksum rules
- same cleanup policies
```

Acceptance criteria:

```text
[ ] Maven module-local scan can write Analysis Store artifacts.
[ ] Maven aggregate scan can write Analysis Store artifacts.
[ ] Manifest/checksum output matches Gradle schema.
[ ] Cleanup policy behavior is equivalent.
[ ] analysisStoreEnabled=false restores BTM-only behavior where supported.
```

Verification:

```bash
./gradlew test --tests '*AnalysisStore*Test' --console=plain --stacktrace
./gradlew test --tests '*Maven*AnalysisStore*Test' --console=plain --stacktrace
./gradlew test --tests '*BuildToolConnectorParityTest' --console=plain --stacktrace
```

### Slice 8 — Add Maven Joern configuration parity

Goal:

Maven must expose the same optional Joern semantic enrichment configuration as Gradle.

Required Maven parameters:

```text
joernEnabled
joernExecutable
joernParseExecutable
joernSliceExecutable
joernWorkspaceDirectory
joernOutputDirectory
joernTimeoutSeconds
joernFailOnError
```

Maven goals:

```text
forensics:analyze-semantics
forensics:import-semantics
forensics:analyze
forensics:analyze-aggregate
```

If separate semantic import is not useful for Maven, document the reason and keep the externally visible full analysis goal equivalent to Gradle `forensicsAnalyze`.

Rules:

```text
- Joern remains disabled by default.
- Joern remains an external CLI/process adapter.
- No Joern Java library dependency is added to the plugin core.
- Standard tests must not require a local Joern installation.
- Fake adapter or fixture mode must cover deterministic tests.
```

Acceptance criteria:

```text
[ ] Maven can configure Joern executables.
[ ] Maven can run full BTM + Analysis Store + Joern enrichment.
[ ] Maven semantic analysis writes the same Joern artifacts and H2 tables.
[ ] Maven and Gradle use the same SemanticAnalysisPort / AnalyzeSemanticsUseCase.
[ ] Joern disabled behavior is consistent across connectors.
```

Verification:

```bash
./gradlew test --tests '*AnalyzeSemanticsUseCaseTest' --console=plain --stacktrace
./gradlew test --tests '*Maven*Joern*Test' --console=plain --stacktrace
./gradlew test --tests '*BuildToolConnectorParityTest' --console=plain --stacktrace
```

### Slice 9 — Unify full analysis orchestration

Goal:

A full analysis run must have the same internal sequence for Gradle and Maven.

Shared orchestration target:

```text
ForensicsAnalysisRunner
```

or, if the existing runner can be safely extended:

```text
BtmGenerationRunner
```

Standard full analysis flow:

```text
1. resolve connector configuration
2. collect source roots
3. create shared request
4. create BuildIdentity / AnalysisRunId
5. run JavaParser scan
6. generate domain rules
7. render BTM rules
8. write BTM file
9. write Analysis Store rows
10. write manifest
11. write checksums
12. if joernEnabled=true, run semantic analysis
13. import Joern artifacts into Analysis Store
14. update manifest/checksums
15. apply cleanup policy
```

Acceptance criteria:

```text
[ ] Gradle full analysis delegates to shared orchestration.
[ ] Maven full analysis delegates to shared orchestration.
[ ] No duplicated Analysis Store orchestration in Maven Mojo.
[ ] No duplicated Joern orchestration in Gradle task.
[ ] Error handling is equivalent.
```

Verification:

```bash
./gradlew test --tests '*ForensicsAnalysisRunnerTest' --console=plain --stacktrace
./gradlew test --tests '*BtmGenerationAdapterValidationTest' --console=plain --stacktrace
```

### Slice 10 — Add connector parity tests

Goal:

Make parity executable and hard to regress.

Required tests:

```text
BuildToolConnectorParityTest
GradleRequestMappingTest
MavenRequestMappingTest
BtmGenerationAdapterValidationTest
MavenReactorAggregationTest
MavenAnalysisStoreParityTest
MavenJoernConfigurationParityTest
MavenFullAnalysisParityTest
```

Test scenarios:

```text
same simple project -> same BTM output
same include/exclude filters -> same selected rules
same minBranchesPerMethod -> same filtered rules
same includeEntryExit -> same rule set
same timestamp setting -> same deterministic behavior
same Analysis Store enabled setting -> same artifact structure
same Joern disabled setting -> same no-Joern behavior
same fake Joern result -> same semantic artifacts/import rows
Gradle multi-project fixture -> equivalent to Maven reactor fixture
```

Recommended fixture layout:

```text
src/test/resources/fixtures/parity/simple-java-project/
src/test/resources/fixtures/parity/gradle-multiproject/
src/test/resources/fixtures/parity/maven-reactor/
src/test/resources/fixtures/parity/joern-fake-output/
```

Acceptance criteria:

```text
[ ] Tests prove request mapping parity.
[ ] Tests prove output parity for BTM generation.
[ ] Tests prove artifact parity for Analysis Store.
[ ] Tests prove Maven reactor aggregation.
[ ] Tests do not require real Joern by default.
```

Verification:

```bash
./gradlew test --tests '*Parity*' --console=plain --stacktrace
```

### Slice 11 — Extend ArchUnit rules for connector boundaries

Goal:

Prevent accidental cross-dependencies and adapter leakage.

Rules to enforce:

```text
plugin.btmgen.common must not depend on Gradle APIs
plugin.btmgen.common must not depend on Maven APIs
plugin.btmgen.gradle must not depend on Maven APIs
plugin.btmgen.maven must not depend on Gradle APIs
domain must not depend on Gradle APIs
domain must not depend on Maven APIs
application must not depend on Gradle APIs
application must not depend on Maven APIs
application must not depend on concrete Joern CLI adapter classes
application must not depend on H2 implementation classes
```

Additional parity rule:

```text
Maven Mojo classes and Gradle Task classes must not call each other.
Both must call common/application services.
```

Acceptance criteria:

```text
[ ] ArchUnit catches Gradle API leakage into Maven/common/application/domain.
[ ] ArchUnit catches Maven API leakage into Gradle/common/application/domain.
[ ] ArchUnit catches direct task-to-mojo coupling.
[ ] Existing architecture rules stay green.
```

Verification:

```bash
./gradlew test --tests '*HexagonRulesTest' --console=plain --stacktrace
```

### Slice 12 — Update README with equal Gradle/Maven usage

Goal:

Document Gradle and Maven as equal connectors, not as feature-tiered variants.

README updates:

```text
Build Tool Connector Parity
Gradle quickstart
Maven quickstart
Gradle multi-project analysis
Maven reactor analysis
Gradle full analysis with Joern
Maven full analysis with Joern
Generated artifacts for both connectors
Known limitations, if any
```

Remove or update outdated statements such as:

```text
Maven reactor aggregation is not implemented yet
Maven generated .btm remains the only generated build artifact
Maven has no Joern parameters
```

Only remove those statements after the corresponding implementation and tests are complete.

Acceptance criteria:

```text
[ ] README contains equivalent Gradle and Maven examples.
[ ] README does not claim parity before tests prove it.
[ ] README documents Maven reactor usage.
[ ] README documents Maven Joern usage.
[ ] README documents generated Maven artifacts.
```

Verification:

```bash
./gradlew test --console=plain --stacktrace
```

### Slice 13 — Add Maven reactor smoke fixture based on WildFly-like structure

Goal:

Prove that the root POM aggregation case works before trying a real WildFly scan.

Fixture requirements:

```text
root pom.xml with packaging=pom
module-a with src/main/java
module-b with src/main/java
module-empty with no source roots
module-test with src/test/java
nested module path
```

Assertions:

```text
- root packaging=pom does not fail
- empty module is ignored safely
- module-a and module-b are scanned
- includeTests=false excludes test roots
- includeTests=true includes test roots
- output is deterministic
- Analysis Store contains module/source metadata
```

Acceptance criteria:

```text
[ ] Fixture behaves like a Maven reactor.
[ ] Test proves root aggregation works.
[ ] Test proves empty modules do not fail.
[ ] Test proves source roots are deterministic and de-duplicated.
```

Verification:

```bash
./gradlew test --tests '*MavenReactorAggregationTest' --console=plain --stacktrace
```

### Slice 14 — Optional real-project validation with WildFly checkout

Goal:

Validate behavior against a large Maven reactor without making the normal test suite depend on WildFly.

This must remain opt-in.

Suggested command shape:

```bash
./gradlew test -PwithLargeMavenFixture=true -PwildflyCheckout=/path/to/wildfly --tests '*LargeMavenReactorSmokeTest' --console=plain --stacktrace
```

Rules:

```text
- Do not download WildFly during normal tests.
- Do not require Joern installation for normal tests.
- Do not commit WildFly sources.
- Do not make performance-sensitive smoke tests part of the default unit test loop unless explicitly approved.
```

Acceptance criteria:

```text
[ ] Large Maven reactor scan can be started from root.
[ ] Root pom packaging does not fail.
[ ] Source-root count is reported.
[ ] Memory/performance warnings are documented if observed.
[ ] Joern missing from PATH produces a clear configuration error, not a misleading Maven failure.
```

### Slice 15 — Final quality gate and commit

Goal:

Complete the workflow with full verification and a traceable commit.

Commands:

```bash
git status --short
git diff --stat
./gradlew test --tests '*BuildToolConnectorParityTest' --console=plain --stacktrace
./gradlew test --tests '*BtmGenerationAdapterValidationTest' --console=plain --stacktrace
./gradlew test --tests '*MavenReactorAggregationTest' --console=plain --stacktrace
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --console=plain --stacktrace
./gradlew validatePlugins --console=plain --stacktrace
```

Before commit:

```bash
git status --short
git diff
git diff --cached
```

Commit message must explain:

```text
what changed
why connector parity is required
how shared request/orchestration was used
which Gradle capabilities were mirrored in Maven
which Maven reactor behavior was added
which tests were added
which quality commands were executed
known limitations
```

Acceptance criteria:

```text
[ ] All targeted parity tests pass.
[ ] Full local quality gate passes or failure is documented with root cause.
[ ] validatePlugins passes when Gradle plugin metadata/tasks changed.
[ ] README, QUALITY.md, and AGENTS.md are consistent.
[ ] Commit contains only related files.
```

## 9. Definition of Done

This workflow is done when:

```text
[ ] AGENTS.md defines mandatory Gradle/Maven connector feature parity.
[ ] QUALITY.md defines a connector parity quality gate.
[ ] A capability matrix exists and is maintained.
[ ] Shared request/result model covers all connector capabilities.
[ ] Gradle adapter maps into the shared model.
[ ] Maven adapter maps into the shared model.
[ ] Maven module-local scan remains functional.
[ ] Maven reactor aggregation is implemented.
[ ] Maven root pom packaging is valid as aggregation context.
[ ] Maven Analysis Store support matches Gradle.
[ ] Maven Joern configuration matches Gradle.
[ ] Maven full analysis can generate BTM + Analysis Store + Joern enrichment.
[ ] Gradle and Maven produce equivalent BTM output for equivalent sources.
[ ] Gradle and Maven produce equivalent artifact metadata for equivalent sources.
[ ] ArchUnit prevents build-tool API leakage.
[ ] Standard tests do not require a real Joern installation.
[ ] README documents equal Gradle and Maven usage.
[ ] Full quality gate has been executed and reported.
```

## 10. STOP conditions

Stop and report if any of these conditions occur:

```text
1. The current repository baseline conflicts with the project-approved baseline.
2. The shared runner/request model cannot represent a Gradle-only feature without a breaking rewrite.
3. Maven plugin APIs would require a new dependency/version change not approved by the task.
4. Maven reactor aggregation would require calling Gradle classes.
5. Gradle full analysis and Maven full analysis cannot share the same core orchestration.
6. Existing output would change semantically without explicit approval.
7. Existing quality gates can only pass by lowering thresholds or disabling tests.
8. Joern tests would require a real local Joern installation in the default test suite.
9. Dependency verification blocks new dependencies and cannot be updated cleanly.
10. Public README behavior cannot be verified from source/tests.
```

No speculative implementation beyond these stop conditions.

## 11. Expected end state

After this workflow, the project has two equal build-tool connectors:

```text
Gradle connector
  -> shared request
  -> shared analysis runner/use cases
  -> BTM rules
  -> Analysis Store
  -> Manifest/checksums
  -> optional Joern enrichment

Maven connector
  -> shared request
  -> shared analysis runner/use cases
  -> BTM rules
  -> Analysis Store
  -> Manifest/checksums
  -> optional Joern enrichment
```

The repository-level rule becomes:

```text
A Java project analyzed through Maven must receive the same forensic analysis capability as a Java project analyzed through Gradle.
```

This prepares the later `forensic_analytics` server workflow because both Gradle and Maven builds will produce the same kind of static analysis package for gRPC upload, replay, and LLM-supported diagnostics.
