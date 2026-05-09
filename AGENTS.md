# AGENTS.md

## Purpose

This document defines the mandatory engineering rules for automated agents working on this repository.

The repository contains the Forensics Tracing Toolkit, Gradle and Maven build-tool adapters, and runtime support library for generating and consuming Byteman-based forensic tracing rules for Java applications.

Agents must follow this document before modifying code, tests, build logic, examples, or documentation.

## Project Baseline

The project baseline is:

- Java 17
- Gradle 9.4.0
- JUnit 5
- ArchUnit
- JaCoCo
- SonarQube / SonarCloud related quality checks
- Hexagonal architecture
- IF-Less development preference
- Declarative programming preference
- Regression-first workflow

All source code, comments, JavaDoc, test names, and repository documentation must be written in English.

Use `.\gradlew.bat` on Windows PowerShell and `./gradlew` on Unix-like shells.

## Core Principle

Prefer the smallest correct change.

Do not perform broad rewrites, speculative refactorings, or unrelated cleanups.

Every change must be traceable to the requested task, an observed defect, or a verified architectural rule.

## Mandatory Agent Safety Rules

These rules are mandatory for every change performed by an automated agent.

### STOP and Report Rule

If a required method, class, interface, task, package, file, or documented contract cannot be found exactly as expected, the agent must stop and report the mismatch.

The agent must not infer, guess, invent, or silently substitute missing names.

Examples:

- If a method was renamed in an interface, do not assume that a similarly named method exists in another class.
- If a delegation target cannot be verified, do not rename the call based on naming symmetry.
- If a Gradle task referenced by documentation does not exist, do not replace it with another task without verification.
- If a package does not match the documented architecture map, do not move files automatically unless the task explicitly requires it.
- If README, tests, and source code disagree, do not silently choose one interpretation.

Required behavior:

1. Inspect the relevant source files.
2. Verify the exact symbol, method, class, task, or contract.
3. If verification fails, stop the implementation.
4. Report:
  - what was expected
  - what was found instead
  - which files were inspected
  - why continuing would be unsafe

Do not continue with a speculative implementation.

### No Guessing Rule

Agents must not guess implementation details.

This applies especially to:

- method names
- static helper methods
- Gradle task names
- plugin IDs
- package names
- test class names
- generated file locations
- runtime helper contracts
- Byteman helper calls
- SonarQube or JaCoCo task names

If a value cannot be verified from source, build files, tests, or documentation, the agent must stop and report.

### No Side-Effect Renames

Renames must be limited to the explicitly requested symbol unless all affected symbols are verified from source.

Do not rename a method, class, field, package, Gradle task, or static call in one layer based only on the assumption that a corresponding symbol in another layer has already been renamed.

Unsafe example:

```java
// Interface method was renamed:
setVariable(String name, Object value)

// Do not automatically assume this static method exists:
RtTrace.setVariable(name, value);
```

Safe behavior:

1. Verify the actual target method in the target class.
2. Rename only the confirmed symbol.
3. If the target method does not exist, stop and report.
4. Do not create compatibility methods unless explicitly requested.

### No Hidden Compatibility Code

Do not introduce compatibility wrappers, aliases, overloads, deprecated bridge methods, or fallback paths unless explicitly requested.

Examples of forbidden hidden compatibility changes:

```java
public void var(String name, Object value) {
    setVariable(name, value);
}
```

```java
public static void setVariable(String name, Object value) {
    varSet(name, value);
}
```

Compatibility code may only be added when the task explicitly asks for backward compatibility and the affected API contract has been verified.

### No Unrequested Architecture Migration

Do not move packages, rename modules, introduce new layers, or restructure the project unless the task explicitly requires it.

Architecture cleanup must be handled as a dedicated task.

## Verify Before Touch Workflow

Every implementation task must start with a read-only verification phase.

### Phase 1: Read-Only Verification

Before modifying files, the agent must inspect:

1. the files explicitly mentioned in the task
2. the directly referenced interfaces
3. the directly referenced implementation classes
4. the directly referenced tests
5. the relevant Gradle tasks or quality documentation when build behavior is affected
6. README or usage documentation when public behavior is affected
7. `QUALITY.md` when quality-gate behavior is affected

During this phase, the agent must not modify files.

The goal is to confirm that the requested change matches the actual repository state.

### Phase 2: Implementation

Only after successful verification may the agent modify files.

The implementation must follow the smallest correct change principle.

A valid implementation must:

- change only files required by the task
- preserve existing behavior unless the task explicitly changes it
- avoid speculative improvements
- avoid unrelated formatting-only changes
- keep code comments in English
- keep repository documentation in English

### Phase 3: Local Verification

After implementation, the agent must run the narrowest meaningful verification first.

Preferred order:

1. targeted unit test
2. affected package or module test
3. ArchUnit rule when architecture is affected
4. Gradle plugin validation task
5. full quality gate as defined by `QUALITY.md`

The current quality gate source is `QUALITY.md`.

Do not invent or reference a `quality_gate.py` file.

If the quality gate cannot be executed, the agent must report the reason and provide the commands that were attempted.

## Quality Gate

The default quality command is:

```bash
./gradlew test
```

The authoritative full local quality gate is:

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --console=plain --stacktrace
```

When plugin metadata, Gradle task inputs, task outputs, or plugin implementation classes are changed, also run:

```bash
./gradlew validatePlugins
```

When general build health is relevant, the following command is useful as a partial diagnostic run:

```bash
./gradlew clean check --console=plain --stacktrace
```

`checkPackageCoverage` is not part of the default Gradle `check` lifecycle in this repository, so `./gradlew clean check` is not the complete local gate on its own.

Agents must not claim that a command passed unless it was actually executed.

If a command fails, the agent must report:

- command executed
- failure summary
- relevant failing test or task
- whether the failure was caused by the current change
- remaining blocker

## Architecture Rules

The project follows hexagonal architecture.

The domain and application code must stay independent from technical frameworks and infrastructure details.

### Architecture Intent

The project is structured around:

- domain model
- application services
- ports
- adapters
- Gradle plugin integration
- runtime tracing helpers
- rendering and writing infrastructure
- quality tests

The intended dependency direction is:

```text
plugin / infrastructure / adapters
        -> application
        -> domain
```

The reverse direction is forbidden.

### Package Responsibility Map

The following package responsibilities apply to the current repository structure.

#### Domain

```text
de.burger.forensics.domain
```

Contains:

- domain models
- value objects
- domain strategies
- domain ports
- domain-level rules

Allowed dependencies:

- Java standard library
- domain-internal packages

Forbidden dependencies:

- Gradle API
- JavaParser
- SLF4J
- AspectJ
- Byteman
- filesystem adapters
- runtime tracing implementation
- infrastructure classes
- plugin classes

#### Application

```text
de.burger.forensics.application
```

Contains:

- use cases
- orchestration services
- application-level request and result objects
- application-facing tracing facade

Allowed dependencies:

- domain
- Java standard library

Forbidden dependencies:

- Gradle API
- JavaParser
- concrete infrastructure adapters
- concrete runtime helper implementation
- filesystem-specific implementation details
- plugin classes

#### Adapters

```text
de.burger.forensics.adapters
de.burger.forensics.adaptersupport
```

Contains:

- JavaParser scanner adapter
- scanner support classes
- parser-specific extraction logic
- condition rendering support related to parsed Java source

Allowed dependencies:

- application
- domain
- JavaParser
- Java standard library

Adapters may depend inward on application and domain.

Adapters must not become orchestration use cases.

#### Infrastructure

```text
de.burger.forensics.infrastructure
```

Contains:

- runtime tracing helper implementation
- logging aspect
- infrastructure-specific runtime support

Allowed dependencies:

- application-facing contracts where needed
- domain only if explicitly justified
- external runtime libraries required by the feature

Infrastructure must not contain domain decisions.

#### Build Tool Plugins

```text
de.burger.forensics.plugin
```

Contains:

- Gradle plugin entry points
- Gradle tasks
- Gradle extension objects
- Maven Mojo entry points
- Maven parameter mapping
- Gradle log adapters
- Maven log adapters
- task wiring
- plugin-specific rendering and file writing adapters

Allowed dependencies:

- application
- domain ports
- infrastructure adapters where required
- Gradle API
- Maven plugin APIs inside Maven adapter packages only
- Java standard library

Forbidden behavior:

- business logic hidden inside Gradle task classes
- business logic hidden inside Maven Mojo classes
- scanner rules hardcoded in task classes
- rendering policies hardcoded in task classes when strategy classes exist
- direct dependency from domain/application back to plugin code

Build-tool adapter boundaries:

- `de.burger.forensics.plugin.btmgen.common` must not depend on Gradle or Maven APIs.
- `de.burger.forensics.plugin.btmgen.gradle` may depend on Gradle APIs and must not depend on Maven APIs.
- `de.burger.forensics.plugin.btmgen.maven` may depend on Maven APIs and must not depend on Gradle APIs.
- Maven Mojo classes must delegate to `BtmGenerationRunner`; they must not call Gradle task classes or duplicate scanner orchestration.

### Build Tool Connector Feature Parity

Gradle and Maven connectors are inbound adapters for the same forensic analysis capabilities.

The following rule is mandatory:

```text
If a forensic capability is exposed through the Gradle connector, the Maven connector must expose the same capability.
If a forensic capability is exposed through the Maven connector, the Gradle connector must expose the same capability.
```

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

- implement the missing connector mapping in the same workflow, or
- stop and report the parity gap with the affected files, tests, and documented behavior.

### Preferred Target Package Structure

When new code is added or existing code is intentionally migrated, prefer the following target structure:

```text
de.burger.forensics.domain
de.burger.forensics.application

de.burger.forensics.adapter.in.gradle
de.burger.forensics.adapter.out.scan.javaparser
de.burger.forensics.adapter.out.render.byteman
de.burger.forensics.adapter.out.write.file
de.burger.forensics.adapter.out.log.gradle
de.burger.forensics.adapter.out.time.system

de.burger.forensics.runtime
```

Do not migrate existing packages to this target structure unless the task explicitly requires it.

## Gradle Plugin Rules

The Gradle plugin must behave like a clean Gradle 9.4.0 plugin.

### Task Design

Gradle task classes must:

- declare inputs and outputs explicitly
- use Gradle `Property<T>`, `RegularFileProperty`, and `DirectoryProperty` where appropriate
- avoid resolving files eagerly during configuration
- avoid doing execution work during configuration
- keep task actions small
- delegate business logic to application services

Gradle task classes must not:

- perform broad source parsing logic directly
- contain domain rules
- contain rendering strategy decisions that belong in strategy classes
- introduce hidden filesystem assumptions
- use static mutable state for task behavior

### Configuration Cache Awareness

When modifying Gradle task or plugin code, avoid:

- accessing project state during task execution unless Gradle supports it
- storing `Project` references in task fields
- resolving providers too early
- using non-cacheable mutable state
- reading environment or system properties outside declared inputs when they affect outputs

If configuration cache support cannot be guaranteed, report the limitation.

### Build Cache Awareness

When modifying generated output behavior, ensure task outputs are deterministic for the same inputs.

Avoid:

- nondeterministic ordering
- timestamps in generated rules unless explicitly required
- absolute paths in generated content unless explicitly required
- environment-dependent output without declared inputs

### Plugin Validation

When Gradle plugin metadata, task inputs, task outputs, or plugin declarations are changed, run:

```bash
./gradlew validatePlugins
```

If this command is not available or fails for reasons unrelated to the current change, report that clearly.

## IF-Less Development Rules

The project prefers IF-Less development.

This does not mean that every `if` is forbidden.

It means that repeated, branching-heavy, behavior-selecting logic should be replaced by explicit structures.

Prefer:

- strategy pattern
- polymorphism
- enum-to-strategy maps
- lookup tables
- immutable configuration objects
- declarative rule registration
- stream pipelines when they improve clarity
- command objects
- value-object behavior

Avoid:

- long `if / else if / else` chains
- duplicated branch conditions
- type-code branching
- mode flags controlling large behavior blocks
- switch statements that duplicate strategy dispatch
- boolean parameters that change method meaning

Acceptable uses of `if`:

- guard clauses
- null validation
- boundary checks
- error handling
- simple fail-fast preconditions
- direct translation of a domain rule when clearer than abstraction

When replacing conditionals, do not over-engineer.

The smallest understandable design wins.

## Declarative Programming Preference

Prefer declarative configuration and explicit wiring over implicit control flow.

Good examples:

- strategy registries
- explicit mappings
- named rule renderers
- immutable request objects
- clear Gradle extension properties
- ArchUnit rules that document boundaries
- test fixtures that describe scenarios

Avoid:

- hidden conventions
- implicit stringly-typed behavior
- magic method names
- reflection-based wiring unless explicitly justified
- undocumented fallback behavior
- side effects hidden in constructors

Declarative code must remain readable.

Do not replace simple code with abstract DSL-like structures unless it improves maintainability.

## Testing Rules

The project uses JUnit 5 and ArchUnit.

### Regression-First Workflow

When fixing a bug:

1. Write or update a failing test that reproduces the bug.
2. Verify that the test fails for the expected reason when practical.
3. Implement the smallest fix.
4. Run the targeted test.
5. Run the quality gate.

If writing a regression test is not practical, explain why in the final report.

### Test Placement Guide

Place tests according to the affected production code.

Examples:

```text
src/main/java/de/burger/forensics/domain/**
-> src/test/java/de/burger/forensics/domain/**

src/main/java/de/burger/forensics/application/**
-> src/test/java/de/burger/forensics/application/**

src/main/java/de/burger/forensics/adapters/**
-> src/test/java/de/burger/forensics/adapters/**

src/main/java/de/burger/forensics/adaptersupport/**
-> src/test/java/de/burger/forensics/adaptersupport/**

src/main/java/de/burger/forensics/infrastructure/**
-> src/test/java/de/burger/forensics/infrastructure/**

src/main/java/de/burger/forensics/plugin/**
-> src/test/java/de/burger/forensics/plugin/**
```

Architecture rules belong under:

```text
src/test/java/de/burger/forensics/quality
```

SOLID-related quality tests belong under:

```text
src/test/java/de/burger/forensics/quality/solid
```

### Unit Tests

Unit tests must:

- test behavior, not implementation details
- use descriptive names
- avoid brittle assertions
- avoid dependence on test execution order
- use temporary directories for filesystem output
- avoid writing to repository files
- avoid requiring external services

### Gradle TestKit Tests

Gradle plugin behavior should be tested with Gradle TestKit when:

- plugin application behavior changes
- task registration changes
- extension defaults change
- task inputs or outputs change
- generated output behavior changes
- plugin metadata changes

### ArchUnit Tests

Architecture-sensitive changes should be protected with ArchUnit tests.

Use ArchUnit to enforce:

- domain independence
- application independence from infrastructure
- forbidden dependencies
- package boundaries
- adapter dependency direction
- plugin isolation where appropriate

Do not weaken ArchUnit rules to make a change pass.

If a rule is wrong, explain why and update the rule with a dedicated justification.

### Coverage

JaCoCo is part of the repository quality setup.

Do not lower coverage thresholds to make a task pass.

Do not exclude production code from coverage unless the task explicitly requires it and the exclusion is justified.

## Examples Directory Rules

The `examples/` directory contains production-near reference code.

Example files are not part of the main production source set, but they must still remain consistent with the current public API and documented usage patterns.

### Scope

Files under `examples/` are treated as executable documentation.

They must:

- compile conceptually against the current public API
- use the current method and class names
- avoid stale API names
- avoid outdated package structures
- demonstrate the preferred integration style
- remain minimal and focused

They must not:

- introduce alternative architecture rules
- bypass the public API
- depend on internal implementation details unless explicitly documented
- contain obsolete migration leftovers without a comment explaining why

### Definition of Done for Examples

A change to an example is complete only when:

1. The example uses the current public interface names.
2. All referenced methods and classes have been verified in source.
3. The example still demonstrates the intended use case.
4. Any delegation from the example to runtime or infrastructure code is verified against the actual target class.
5. The README and example do not contradict each other.
6. No speculative compatibility code was introduced.

If an example references an API that no longer exists, the agent must verify the replacement from source before changing it.

If no replacement can be verified, the agent must stop and report.

### Known Example Consistency Requirement

`examples/RtTracerAdapter.java` must stay aligned with the current `Tracer` interface.

The method names used by `RtTracerAdapter` must be verified against:

```text
src/main/java/de/burger/forensics/application/tracing/Tracer.java
```

The runtime delegation methods must be verified independently against:

```text
src/main/java/de/burger/forensics/infrastructure/rt/RtTrace.java
```

Do not assume that a runtime delegation method has the same name as the interface method.

In particular, do not replace runtime calls by naming symmetry.

Always inspect the actual runtime class first.

Unsafe assumption:

```java
// Do not assume this target exists only because the interface has this method name.
RtTrace.setVariable(name, value);
```

Safe behavior:

```java
// Verify the actual runtime target method from RtTrace before delegating.
RtTrace.varSet(name, value);
```

If `Tracer` uses `setVariable(String name, Object value)`, the adapter override must use that exact interface method name.

If `RtTrace` still exposes `varSet(String name, Object value)`, the delegation must remain `RtTrace.varSet(name, value)` unless a verified runtime API change exists.

If either side cannot be verified, stop and report.

## Runtime Tracing Rules

Runtime tracing helpers must remain lightweight.

Runtime tracing code should:

- avoid unnecessary allocation
- avoid heavy dependencies
- remain safe when tracing is disabled
- avoid throwing exceptions from tracing paths
- preserve thread-local correlation and span semantics
- emit stable structured data
- keep runtime helper methods explicit and easy to call from Byteman rules

Runtime tracing code must not:

- depend on Gradle API
- depend on JavaParser
- depend on plugin implementation classes
- introduce domain logic
- introduce blocking I/O unless explicitly required
- make tracing mandatory for normal application behavior

## Byteman Rule Rendering Rules

Byteman rendering must remain deterministic and testable.

Rendering code should:

- keep each rule strategy focused
- use explicit render strategy classes
- avoid string duplication where a shared abstraction improves clarity
- keep generated rule names stable
- preserve ordering when deduplicating
- protect branch, switch, return, throw, method enter, method exit, JDBC, and thread lifecycle behavior with tests

Rendering code must not:

- silently drop scan events
- generate syntactically invalid Byteman rules
- depend on filesystem state unless explicitly passed in
- mix JavaParser scanning concerns into rendering strategies

## Scanner Rules

Java source scanning must remain separated from rule rendering.

Scanner code should:

- detect source events
- create scan events or domain models
- normalize source locations where needed
- avoid deciding how Byteman rules are rendered
- keep parser-specific behavior inside adapter packages

Scanner code must not:

- contain Gradle task behavior
- contain plugin metadata behavior
- contain filesystem writing behavior
- contain runtime tracing implementation details

## Documentation Rules

Documentation must stay aligned with source code.

When changing public API, plugin configuration, generated outputs, runtime helper names, or examples, inspect and update relevant documentation.

Relevant documentation may include:

- `README.md`
- `QUALITY.md`
- `AGENTS.md`
- example files
- Gradle plugin usage snippets
- release or publishing notes
- commit prompt documents

Do not update documentation based on assumptions.

Verify documented method names, task names, plugin IDs, paths, and commands from source.

## Documentation Ownership

`AGENTS.md` is the primary source of truth for agent behavior, architecture boundaries, coding rules, test-placement rules, and safety rules.

Other documents, including commit prompts and task prompts, may summarize these rules, but they must not redefine them independently.

If a conflict exists between `AGENTS.md` and another prompt document, `AGENTS.md` wins unless the task explicitly states otherwise.

Commit prompts should reference `AGENTS.md` instead of duplicating architecture definitions.

This avoids rule drift between automation instructions and repository-level engineering rules.

## Commit Rules

A commit must clearly document:

1. what was changed
2. why it was changed
3. how it was changed
4. which files or components were affected
5. whether bugs were fixed
6. whether new features were introduced
7. whether refactoring, cleanup, structural, or architectural changes were made
8. whether tests were added or adjusted
9. whether any breaking or behavior-relevant changes exist
10. which verification commands were executed

Do not create vague commit messages.

Do not commit generated build output unless explicitly required.

Do not commit unrelated local files.

## Required Git Inspection Before Commit

Before creating a commit, inspect:

```bash
git status
git diff
git diff --cached
```

If staged and unstaged changes both exist, inspect them separately.

Do not commit files that are unrelated to the task.

If unexpected changes exist, stop and report.

## Dependency and Security Rules

Dependencies must be treated carefully.

Do not add dependencies unless explicitly required.

Before adding a dependency, verify:

- why it is needed
- whether the JDK or existing dependencies already provide the required capability
- whether it affects plugin consumers
- whether it affects runtime footprint
- whether it introduces logging providers
- whether it conflicts with the existing dependency strategy

The project intentionally avoids bundling an SLF4J provider.

Do not introduce logging bindings such as:

- `logback-classic`
- `slf4j-log4j12`
- `slf4j-reload4j`

unless the task explicitly requires it and the impact is documented.

## Version Rules

Use the configured project baseline.

Do not upgrade Java, Gradle, plugins, dependencies, JaCoCo, SonarQube plugin, or publishing plugins unless the task explicitly asks for it.

Do not change dependency versions as part of unrelated fixes.

## Source Code Style

Java source code must:

- use English comments
- use English JavaDoc
- use clear method names
- prefer immutable data where practical
- avoid unnecessary setters
- avoid static mutable state
- avoid hidden side effects
- keep classes focused
- keep public APIs stable unless explicitly changed

Avoid:

- unrelated formatting
- large methods
- mixed abstraction levels
- broad catch blocks without purpose
- swallowing exceptions
- null-heavy APIs where value objects would be clearer
- stringly-typed rule dispatch when typed models are available

## Error Handling Rules

Errors must be explicit and useful.

Prefer:

- fail-fast validation
- descriptive exception messages
- preserving original causes
- narrow exception handling
- explicit fallback behavior only when documented

Avoid:

- silent fallback
- catching `Exception` without clear purpose
- returning null to signal failures
- hiding parser or rendering failures
- ignoring file write failures

## Public API Rules

Public API changes require extra care.

Before changing public API, inspect:

- interface declarations
- implementations
- examples
- README snippets
- tests
- Gradle plugin usage
- runtime helper usage
- Byteman helper usage

If a public API method is renamed, update all verified callers and examples.

Do not assume matching method names across layers.

## Monorepo and Multi-Module Readiness

The plugin should be designed so it can work in real Gradle builds, including multi-module repositories.

When modifying plugin or task behavior, avoid assumptions such as:

- only one source root exists
- only one module exists
- the root project is always the analyzed project
- all Java sources are under one fixed path
- generated output always belongs to the root build directory

If monorepo behavior is changed, add or update tests that prove the expected behavior.

## File Writing Rules

File-writing adapters and tasks must:

- create parent directories where needed
- write deterministic output
- avoid partially written files where practical
- use declared Gradle outputs when invoked from Gradle tasks
- avoid writing outside configured output locations

Do not write to source directories unless explicitly requested.

## Logging Rules

Logging must be useful but not noisy.

Logging code must:

- avoid introducing concrete logging providers
- avoid leaking secrets
- avoid excessive logs in normal test output
- preserve useful diagnostics for failures
- keep plugin logging behind Gradle logging adapters where appropriate

Runtime tracing output must remain separate from build logging concerns.

## Final Report Requirements

At the end of every task, report:

1. files changed
2. main changes made
3. tests or verification commands executed
4. commands that failed, if any
5. quality gate result
6. known limitations
7. remaining blockers, if any

Do not claim success for unexecuted verification.

If no files were changed, say so explicitly.

## Definition of Done

A task is done only when:

- the requested change was implemented
- the change follows this `AGENTS.md`
- no speculative changes were introduced
- relevant tests were added or updated when needed
- the narrowest meaningful verification was executed
- the quality gate from `QUALITY.md` was executed or a clear reason was reported
- documentation was updated when public behavior changed
- examples were kept consistent with public API
- the final report is accurate

## Forbidden Actions

Agents must not:

- guess missing names
- invent Gradle tasks
- reference `quality_gate.py`
- silently rename side-effect symbols
- add compatibility wrappers without request
- weaken tests to make a build pass
- lower coverage thresholds without request
- remove architecture rules without justification
- introduce framework dependencies into domain code
- introduce Gradle dependencies into application or domain code
- perform broad package migrations without request
- change Java or Gradle baseline without request
- commit unrelated files
- claim verification passed without executing it
