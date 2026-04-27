# Codex Commit Prompt — Forensics Tracing

## Task

Inspect the repository, read the project quality definition from `QUALITY.md`, run the documented and Gradle-based quality checks first, fix all quality-gate-related issues that are realistically fixable from the current codebase, re-run the quality checks until they pass or until remaining blockers are clearly identified, and only then perform the Git inspection, commit creation, and push.

## Important

This repository does **not** use `quality_gate.py`.

Do not search for or invent a Python quality gate.

The authoritative quality guidance is documented in `QUALITY.md`.

Use `QUALITY.md`, `build.gradle.kts`, `settings.gradle.kts`, Gradle tasks, and existing tests to determine the correct quality gate.

## Document Precedence and CI Alignment

When `AGENTS.md`, `QUALITY.md`, or other project-level instruction files are present in the repository root:

1. **`AGENTS.md` takes precedence** over any instructions inferred from code, build scripts, or general heuristics.
2. **`QUALITY.md` is the binding quality contract** — do not override, weaken, or deviate from it based on assumptions.
3. If there is a conflict between the instructions in this prompt and the instructions in `AGENTS.md` or `QUALITY.md`, the **repository-level document wins**.
4. Before executing any phase, inspect both `AGENTS.md` and `QUALITY.md` if they exist and record any deviations or additional rules they define.
5. **Align the commit and push with the CI pipeline** defined in `.github/workflows`:
   - Identify the CI trigger conditions (e.g. push to `main`, pull request).
   - Identify which Gradle tasks the CI pipeline runs.
   - Do not commit or push changes that would predictably break the CI pipeline based on your local quality gate results.
   - If a local quality check fails that is also part of the CI pipeline, treat it as a blocking issue.
   - If a CI step uses a Sonar token or external service that is unavailable locally, report the skip explicitly — do not treat it as a failure.
6. **Do not guess CI behavior** — read the actual workflow YAML files in `.github/workflows` before making any claim about what CI does or does not verify.
7. If `AGENTS.md` defines commit message conventions, branch naming rules, or push restrictions, apply them exactly.

## Project Context

This repository is the **Forensics Tracing Gradle plugin** project.

## Technology Constraints

- Use Java 17 only.
- Use Gradle 9.1 only.
- Use the project Gradle wrapper if available.
- Do not upgrade Gradle, Java, plugins, or dependencies unless the current task explicitly requires it.
- Source code and source code comments must be written in English.
- Preserve the existing Gradle plugin nature of the project.
- Do not introduce Spring Boot.
- Do not introduce unrelated frameworks.

## Architecture Context

The project follows a hexagonal architecture style.

Respect these responsibilities:

### `de.burger.forensics.domain`

- Domain model
- Domain strategies
- Domain ports
- No Gradle, filesystem, JavaParser, Byteman, logging, or infrastructure coupling unless already intentionally present

### `de.burger.forensics.application`

- Use cases and orchestration
- Coordinates ports and domain logic

### `de.burger.forensics.adapters` and `de.burger.forensics.adaptersupport`

- JavaParser scanning
- Scanner support code

### `de.burger.forensics.plugin`

- Gradle plugin adapter
- Gradle task wiring
- Plugin extension
- Byteman rendering and file writing integration

### `de.burger.forensics.infrastructure`

- Runtime tracing helper
- AspectJ logging support
- Runtime/infrastructure concerns

## Important Project Components

### Gradle Plugin

- `BtmGenPlugin`
- `BtmGenExtension`
- `GenerateBtmTask`
- `PluginRuntimeLocator`

### Application Service

- `GenerateRulesUseCase`
- `GenerationRequest`
- `RuleGenerationResult`

### Java Source Scanning

- `JavaParserScanner`
- `MethodEventExtractor`
- Condition rendering and normalization helpers

### Byteman Rule Rendering

- `BytemanRuleRenderAdapter`
- `BytemanRuleRenderer`
- Rule strategies such as:
  - `IfTrueRuleStrategy`
  - `IfFalseRuleStrategy`
  - `SwitchRuleStrategy`
  - `SwitchCaseRuleStrategy`
  - `MethodEnterRuleStrategy`
  - `MethodExitRuleStrategy`
  - `ReturnRuleStrategy`
  - `ThrowRuleStrategy`
  - `JdbcExecuteRuleStrategy`
  - `ThreadLifecycleRuleStrategy`

### Runtime Tracing

- `RtTrace`
- `RtTraceHelper`
- `RtTracer`
- `RtEvent`
- `RtSpanToken`

### Logging

- `MethodLoggingAspect`
- `SuppressLogging`

### File Output

- `BtmFileWriter`

### Quality Tests

- JUnit 5 tests
- ArchUnit tests
- SOLID heuristic tests
- Gradle TestKit tests
- JaCoCo coverage verification

## Commit Documentation Goal

Your task is not to generate a vague commit message.

Your task is to produce a commit that clearly documents:

1. What was changed
2. Why it was changed
3. How it was changed
4. Which files, packages, modules, components, ports, adapters, use cases, Gradle tasks, rule strategies, or tests were affected
5. Whether bugs were fixed
6. Whether new features were introduced
7. Whether refactoring, cleanup, structural, architectural, or test-hardening changes were made
8. Whether tests were added or adjusted
9. Whether any breaking or behavior-relevant changes exist

---

# Strict Execution Order

## Phase 1 — Repository Inspection

1. Inspect the repository root.
2. Inspect whether this is a single-module or multi-module Gradle build.
3. Inspect `settings.gradle`, `settings.gradle.kts`, `build.gradle`, and `build.gradle.kts`.
4. Inspect `gradle/libs.versions.toml`.
5. Inspect `QUALITY.md`.
6. Inspect `AGENTS.md` if present — record all rules and conventions it defines.
7. Inspect `.github/workflows` — read every workflow YAML and identify which Gradle tasks CI runs.
8. Inspect available Gradle tasks.
9. Inspect `git status`.
10. Inspect staged and unstaged changes separately.
11. Inspect `git diff`.
12. Inspect `git diff --cached`.
13. Inspect changed Java, Gradle, test, documentation, CI, and configuration files.
14. Identify whether changes affect:
    - Gradle plugin wiring
    - Source scanning
    - Rule generation
    - Byteman rendering
    - Runtime tracing
    - Logging aspect
    - Ports/adapters
    - Application services
    - Domain strategies
    - Tests
    - Architecture rules
    - Coverage configuration
    - SonarCloud configuration
    - Dependency verification or build security

## Phase 2 — Quality Definition from `QUALITY.md`

1. Read `QUALITY.md` completely.
2. Treat `QUALITY.md` as the project-specific quality contract.
3. Do not look for `quality_gate.py` as the primary quality mechanism.
4. If `quality_gate.py` is absent, report that this is expected for this project.
5. Extract the documented quality command from `QUALITY.md`.
6. Verify the documented command against the Gradle build setup.
7. Run the documented quality command first.

The documented minimum quality command is expected to be:

```bash
./gradlew test
```

After the documented quality command succeeds or after its failures have been handled, run the full local Gradle quality gate:

```bash
./gradlew clean check jacocoTestReport --console=plain --stacktrace
```

Use the project root as working directory.

Before running the quality gate:

1. Verify the Java version.
2. Verify that Java 17 is used.
3. Verify the Gradle wrapper version if possible.
4. Use Gradle 9.1 only.
5. Do not switch to another Java version.
6. Do not use Maven.
7. Do not use Python as the quality gate.

## Quality Gate Rules

- Do not commit before the `QUALITY.md`-based quality checks have been handled.
- Do not skip tests.
- Do not skip ArchUnit tests.
- Do not skip SOLID quality tests.
- Do not skip Gradle TestKit tests if they are part of `check`.
- Do not disable failing tests to make the build pass.
- Do not lower JaCoCo thresholds to make the build pass unless the task explicitly requires changing the policy.
- Do not silence compiler warnings by hiding them without fixing the cause.
- Do not remove meaningful assertions.
- Do not weaken architecture rules unless the current architectural decision explicitly requires it.
- Do not move domain logic into plugin, infrastructure, or adapter packages.
- Do not introduce infrastructure dependencies into the domain layer.
- Do not introduce Gradle API dependencies into domain or application code.
- Do not introduce Byteman rendering concerns into domain model classes unless already intentionally modeled.
- Do not add generated files, build outputs, `.gradle`, `build`, IDE metadata, or temporary artifacts to the commit unless they are explicitly intended project files.
- Remove unused imports.
- Remove dead code only when it is clearly unused and not part of a public API contract.
- Keep refactoring targeted and explainable.
- Preserve public behavior unless the change intentionally fixes faulty behavior.

## Handling Quality Gate Failures

When quality gate failures occur:

1. Collect the full relevant output.
2. Identify concrete failures.
3. Classify each failure as:
   - Introduced by the current change set
   - Pre-existing repository issue
   - Environment/tooling issue
   - External service issue
4. Fix all failures caused by the current codebase that are realistically fixable in scope.
5. Re-run the failing quality command.
6. Re-run the full local Gradle quality gate.
7. Repeat until:
   - The quality gate passes, or
   - Only clearly explainable blockers remain

If the quality gate reveals lint, compiler, type, import, architecture, test, Gradle, JaCoCo, runtime, Byteman-rendering, JavaParser-scanning, or plugin-functional issues, fix them in the appropriate layer.

If tests must be updated because of a real implementation change, update them and explain why.

If rule generation behavior changes, explicitly verify and mention affected rule types, for example:

- Method enter
- Method exit
- Return
- Throw
- If true
- If false
- Switch
- Switch case
- JDBC execute
- Thread lifecycle

If the change affects monorepo or multi-module behavior, explicitly verify and mention:

- Root project detection
- Included Gradle projects
- Source root handling
- Per-module scanning
- Output file handling
- Task registration
- Task configuration avoidance
- Gradle configuration cache compatibility if relevant
- Gradle TestKit coverage

## Optional External Quality Gate

If the environment provides the required Sonar token, run:

```bash
./gradlew sonar --console=plain --stacktrace
```

If the Sonar token is missing, unavailable, or intentionally not configured locally:

1. Report that SonarCloud was not executed locally.
2. Do not treat the missing token as a code failure.
3. Do not fake Sonar success.

## Phase 3 — Final Repository Inspection After Quality Gate Handling

Only after the `QUALITY.md`-based quality checks and the full Gradle quality gate have been handled:

1. Inspect the final repository state again with:

```bash
git status --short
```

2. Inspect unstaged changes:

```bash
git diff
```

3. Inspect staged changes if any:

```bash
git diff --cached
```

4. Inspect changed file names:

```bash
git diff --name-status
git diff --cached --name-status
```

5. Determine whether existing staged changes were present before your work.
6. Handle staged and unstaged changes deliberately.
7. Do not accidentally commit unrelated files.
8. Do not commit generated build artifacts.
9. Do not commit `.gradle`, `build`, IDE workspace files, local logs, or temporary files unless they are intentionally part of the requested change.

## Phase 4 — Commit Message Creation

Write the commit message based only on the actual final diff.

Do not invent facts that are not visible in the code changes.

Do not write generic messages like:

- `update code`
- `fix issues`
- `small improvements`
- `quality fixes`
- `cleanup`

Preferred commit structure:

```text
<type>: <short precise summary>

What:
- ...
- ...

Why:
- ...
- ...

Changes:
- ...
- ...

Impact:
- ...
- ...

Testing:
- ...
- ...
```

Allowed commit types:

- `feat`
- `fix`
- `refactor`
- `chore`
- `test`
- `docs`
- `perf`

Choose the most accurate type based on the actual change set.

## Commit Message Rules

- Read the real diff, not only file names.
- Mention affected packages, classes, Gradle tasks, rule strategies, use cases, adapters, ports, tests, or CI files where relevant.
- If bugs were fixed, state the faulty behavior that was corrected.
- If a feature was introduced, state what it does and why it was added.
- If refactoring was done, explain the structural improvement and its purpose.
- If architectural work was done, name the affected layer:
  - Domain
  - Application
  - Adapter
  - Plugin adapter
  - Infrastructure
  - Test support
  - CI/build tooling
- If tests were added or changed, mention what was covered, adapted, or repaired.
- If ArchUnit or SOLID quality tests were affected, mention that explicitly.
- If JaCoCo or Sonar configuration changed, mention that explicitly.
- If Gradle plugin behavior changed, mention the affected task, extension, or plugin class.
- If JavaParser scanning changed, mention what source constructs are now handled differently.
- If Byteman rendering changed, mention which rule strategy or emitted rule format changed.
- If runtime tracing changed, mention affected event fields, correlation/span behavior, helper calls, or enablement behavior.
- If multiple concerns are mixed, group them honestly.
- If unrelated changes are present, call that out clearly instead of pretending the change set is perfectly clean.
- If quality gate related fixes were made, mention that explicitly in the commit body.
- If the quality gate required no fixes, say so truthfully in the final execution summary, not necessarily in the commit body.
- If `QUALITY.md` itself was added, fixed, integrated, or changed, mention that explicitly if visible in the diff.
- If `AGENTS.md` itself was added, fixed, or changed, mention that explicitly if visible in the diff.

## Phase 5 — Stage, Commit, and Push

Only after the final diff has been reviewed:

1. Stage all relevant changes deliberately.
2. Create the commit with the prepared detailed commit message.
3. Capture the new commit hash.
4. Determine the current branch name.
5. Push to the current branch.

## Execution Rules

- Do not commit before reviewing the actual final diff.
- Do not push before the commit was created successfully.
- Do not claim success for any failed step.
- If commit fails, report the exact error.
- If push fails, report the exact error.
- If the quality gate fails, report the exact error.
- If the quality gate still fails after fixes, explain exactly what remains and whether it is:
  - Code issue
  - Tooling issue
  - Environment issue
  - External service issue
  - Pre-existing repository issue
- If unresolved blockers remain, do not hide them in the final summary.
- If both staged and unstaged changes exist, handle that deliberately and describe it in the assessment.
- If no commit is created because there are no relevant changes, report that explicitly.
- If push is rejected because the remote branch changed, do not force push unless explicitly instructed.

## Final Output After Execution

Print:

1. Whether `AGENTS.md` was found and which rules it defines
2. Whether `QUALITY.md` was found
3. The quality command documented in `QUALITY.md`
4. The CI pipeline tasks identified from `.github/workflows`
5. The exact local quality gate command used
6. Whether Java 17 was used
7. Whether Gradle 9.1 / the project wrapper was used
8. Whether the documented `QUALITY.md` command passed
9. Whether the full local Gradle quality gate passed
10. Whether SonarCloud was executed or skipped
11. If a quality command failed, the exact reason
12. Which fixes were applied because of the quality checks
13. Whether any failures were pre-existing, environment-related, or external-service-related
14. The final commit message
15. The branch name
16. The new commit hash
17. Whether the push succeeded

If push fails, report the exact reason and do not pretend success.

If a quality check fails, report the exact reason and do not pretend success.

If remaining blockers exist, state them explicitly.

---

## Project-Specific Quality Gate Summary

For this project, the expected logic is:

```text
AGENTS.md    = Agentenregeln / haben Vorrang bei Konflikten
QUALITY.md   = Qualitätsvertrag / verbindliche Vorgabe
./gradlew test                             = dokumentierter Mindestlauf
./gradlew clean check jacocoTestReport     = vollständiger lokaler Commit-Gate
./gradlew sonar                            = optionales externes Gate, nur mit Token
.github/workflows                          = CI-Referenz für Alignment-Check
```
