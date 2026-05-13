# Workflow: Convert Ruflo-Style Agent Orchestration to Codex for Forensics Tracing

## Purpose

This workflow updates the existing `.agents` and `.codex` setup of `forensics_tracing` so that it follows the useful orchestration ideas from Ruflo, but in a Codex-compatible, repository-safe, and Forensics-specific form.

The goal is not to install Ruflo, Claude Flow, Claude Code hooks, MCP servers, background workers, or any external agent runtime. The goal is to convert the orchestration model into repository-local Codex process files, custom agents, and reusable skills.

The result must remain aligned with the current project baseline:

* Java 17 only
* Gradle 9.4.0
* JUnit 5
* ArchUnit
* JaCoCo
* SonarQube / SonarCloud quality checks
* Hexagonal architecture
* IF-less development preference
* Declarative programming preference
* Regression-first workflow
* Gradle and Maven connector parity
* Source code, comments, JavaDoc, test names, and repository documentation in English

## Core Conversion Rule

Ruflo is a Claude Code oriented multi-agent orchestration system. In this repository it must be converted into a Codex-local coordination model.

Do not copy Claude-specific files directly.
Do not copy `.claude/`, `.claude-plugin/`, Claude hooks, Claude commands, Claude settings, or MCP server registrations.
Do not add `npx ruflo`, `npx claude-flow`, or external swarm commands unless a later explicit task requests a real runtime integration.

Convert concepts only:

| Ruflo / Claude-style concept | Codex / forensics_tracing equivalent                                  |
| ---------------------------- | --------------------------------------------------------------------- |
| Swarm initialization         | `swarm_orchestrator` creates a read-only execution plan               |
| Many specialized agents      | `.codex/agents/*.toml` role definitions                               |
| Skills                       | `.agents/skills/*/SKILL.md` reusable workflows                        |
| Shared memory                | Explicit slice report and handoff notes in the active workflow result |
| Hooks                        | Manual verification checkpoints from `QUALITY.md`                     |
| Background workers           | No background work; run only in the current Codex task                |
| Claude Task tool             | Codex custom agent role selection                                     |
| Dual-mode Claude + Codex     | Codex-only multi-role workflow                                        |
| Plugin marketplace           | No plugin marketplace integration                                     |
| Self-learning memory         | No persistent learning unless explicitly requested                    |

## Existing Repository Structure to Preserve

Codex must first inspect the current repository state. At the time this workflow is written, the repository already contains:

```text
.
├── AGENTS.md
├── QUALITY.md
├── README.md
├── workflow.md
├── .agents/
│   └── skills/
│       ├── commit_message/
│       ├── documentation_sync/
│       ├── migration_workflow/
│       ├── quality_gate/
│       └── slice_workflow/
└── .codex/
    ├── config.toml
    └── agents/
        ├── architecture_reviewer.toml
        ├── commit_reviewer.toml
        ├── documentation_reviewer.toml
        ├── implementation_worker.toml
        ├── quality_reviewer.toml
        ├── repo_explorer.toml
        └── security_reviewer.toml
```

These existing files must not be blindly overwritten. Codex must preserve their intent and extend them only where needed.

## Target Extension

The workflow extends the existing structure with Forensics-specific orchestration roles and skills:

```text
.codex/
├── config.toml
└── agents/
    ├── repo_explorer.toml
    ├── swarm_orchestrator.toml
    ├── architecture_reviewer.toml
    ├── forensics_architect.toml
    ├── gradle_maven_parity_reviewer.toml
    ├── ast_btm_pipeline_reviewer.toml
    ├── joern_semantics_reviewer.toml
    ├── analysis_store_reviewer.toml
    ├── test_archunit_reviewer.toml
    ├── quality_reviewer.toml
    ├── security_reviewer.toml
    ├── documentation_reviewer.toml
    ├── implementation_worker.toml
    └── commit_reviewer.toml

.agents/
└── skills/
    ├── slice_workflow/
    ├── quality_gate/
    ├── commit_message/
    ├── documentation_sync/
    ├── migration_workflow/
    ├── swarm_orchestration/
    ├── forensics_slice_workflow/
    ├── gradle_maven_parity/
    ├── ast_btm_pipeline/
    ├── joern_semantic_analysis/
    ├── analysis_store_review/
    └── archunit_quality_review/
```

Existing files remain authoritative unless a later slice explicitly updates them.

## Global Working Rules

Codex must follow these rules for every slice:

1. Read before editing.
2. Do not guess file names, task names, method names, package names, or plugin IDs.
3. Preserve existing hard rules from `AGENTS.md` and `QUALITY.md`.
4. Do not weaken coverage, quality gates, Gradle plugin validation, or architecture checks.
5. Keep changes minimal and traceable.
6. Use read-only agents for analysis and review.
7. Use only `implementation_worker` for write operations.
8. Never allow two write-capable agents to modify the same working tree in parallel.
9. Do not create commits unless explicitly requested.
10. Do not add external dependencies, npm packages, MCP servers, or runtime hooks in this workflow.
11. Stop and report when repository evidence contradicts the requested change.
12. Keep repository documentation in English.
13. User-facing summaries may be written in German only outside repository files.

## Orchestration Model

The converted Codex swarm works in three phases.

### Phase A: Parallel Read-Only Discovery

The following agents may inspect the repository in parallel:

* `repo_explorer`
* `swarm_orchestrator`
* `architecture_reviewer`
* `forensics_architect`
* `gradle_maven_parity_reviewer`
* `ast_btm_pipeline_reviewer`
* `joern_semantics_reviewer`
* `analysis_store_reviewer`
* `test_archunit_reviewer`
* `quality_reviewer`
* `security_reviewer`
* `documentation_reviewer`

They must not modify files.

### Phase B: Consolidation

`swarm_orchestrator` consolidates findings into an ordered slice plan.

The plan must include:

* affected files
* proposed changes
* dependencies between slices
* risk level
* verification commands
* stop conditions

### Phase C: Sequential Implementation

Only `implementation_worker` may edit files.

Implementation must happen slice by slice. After each implementation slice, Codex must run the narrowest meaningful verification from `QUALITY.md`. Full verification is required when project rules, quality rules, build logic, plugin behavior, or generated artifacts are affected.

---

# Slice 0: Read-Only Repository Verification

## Goal

Verify the current repository structure before changing anything.

## Required Actions

1. Inspect the repository root.
2. Read `AGENTS.md`.
3. Read `QUALITY.md`.
4. Read `README.md`.
5. Read existing `workflow.md`.
6. Inspect `.codex/config.toml`.
7. Inspect all `.codex/agents/*.toml` files.
8. Inspect all `.agents/skills/*/SKILL.md` files.
9. Inspect `build.gradle.kts` and `settings.gradle.kts` only if needed for quality or Gradle version verification.
10. Create an internal current-state summary.

## Expected Result

A read-only summary containing:

```text
Existing process files:
Existing Codex agents:
Existing skills:
Existing quality commands:
Existing architecture rules:
Existing connector parity rules:
Open points:
Conflicts:
```

## Stop Conditions

Stop if:

* `AGENTS.md` cannot be read.
* `QUALITY.md` cannot be read.
* `.codex` or `.agents` contains files that cannot be safely classified.
* Existing instructions contradict the target extension.

---

# Slice 1: Ruflo-to-Codex Concept Mapping

## Goal

Create the mapping from Ruflo concepts to repository-local Codex concepts.

## Required Actions

1. Do not import Ruflo code.
2. Do not create Claude-specific files.
3. Identify which Ruflo concepts are useful for this repository.
4. Convert only the useful concepts into `.codex/agents` and `.agents/skills`.
5. Document rejected concepts as non-goals.

## Required Mapping

Codex must use this exact mapping:

```text
Ruflo-style swarm coordination -> swarm_orchestrator agent
Ruflo-style specialized agents -> forensics-specific Codex agent files
Ruflo-style skills -> repository-local SKILL.md files
Ruflo-style shared memory -> explicit handoff report in the current workflow execution
Ruflo-style hooks -> explicit verification checkpoints
Ruflo-style background workers -> not implemented
Ruflo MCP tools -> not implemented
Ruflo plugin marketplace -> not implemented
Claude Code Task tool -> not implemented
Dual Claude/Codex mode -> Codex-only role model
```

## Expected Result

A short internal mapping report.

## Stop Conditions

Stop if:

* Codex would need to add external runtime dependencies.
* Codex would need to install Ruflo, Claude Flow, or MCP tools.
* The requested conversion cannot be represented using repository-local files.

---

# Slice 2: Update `AGENTS.md`

## Goal

Extend `AGENTS.md` with Codex orchestration rules without weakening any existing project rule.

## Required Actions

1. Read the full existing `AGENTS.md`.
2. Preserve all existing sections.
3. Add a section named `Codex Multi-Agent Orchestration` if it does not already exist.
4. Add a section named `Ruflo Concept Conversion Boundary` if it does not already exist.
5. Do not remove existing Java, Gradle, JUnit, ArchUnit, JaCoCo, SonarQube, architecture, IF-less, declarative programming, STOP, No Guessing, or connector parity rules.

## Required Text to Add or Merge

```md
## Codex Multi-Agent Orchestration

The repository uses a Codex-local multi-agent model based on repository files under `.codex/agents/` and `.agents/skills/`.

Read-only agents may be used in parallel for exploration, architecture review, quality review, security review, documentation review, Gradle/Maven parity review, AST/BTM pipeline review, Joern semantic review, and analysis-store review.

Only `implementation_worker` may modify files, and it must implement exactly one approved slice at a time.

Multiple write-capable agents must never modify the same working tree in parallel.

The `swarm_orchestrator` role is responsible for consolidating read-only findings into a slice plan. It does not modify files.

Each implementation slice must include:

- affected files
- exact change intent
- verification command
- stop condition
- summary of risks

## Ruflo Concept Conversion Boundary

This repository may reuse Ruflo-style concepts such as specialized agents, skills, orchestration, handoff notes, and verification checkpoints.

This repository must not copy Claude-specific runtime files or add Ruflo, Claude Flow, MCP servers, background workers, hooks, plugin marketplace dependencies, or persistent agent memory unless a future explicit task requires a real integration.

Ruflo concepts are converted into Codex-local process files only.
```

## Validation

Check that `AGENTS.md` still contains:

* Java 17 baseline
* Gradle 9.4.0 baseline
* JUnit 5
* ArchUnit
* JaCoCo
* SonarQube / SonarCloud quality references
* Hexagonal architecture rules
* IF-less development rules
* Declarative programming preference
* STOP and Report rule
* No Guessing rule
* Build-tool connector parity rule
* Quality gate reference to `QUALITY.md`

## Stop Conditions

Stop if:

* The existing `AGENTS.md` cannot be updated without deleting hard rules.
* Existing rules contradict the new orchestration boundary.
* The file format is unexpectedly corrupted.

---

# Slice 3: Update `.codex/config.toml` Conservatively

## Goal

Keep the Codex configuration conservative and repository-local.

## Required Actions

1. Read `.codex/config.toml`.
2. Preserve existing values unless clearly invalid.
3. Ensure the `[agents]` section exists.
4. Keep `max_threads = 5` unless the existing repository has a stricter value.
5. Keep `max_depth = 1` unless the existing repository has a stricter value.
6. Do not add external MCP server definitions.
7. Do not add credentials.
8. Do not add Ruflo or Claude Flow commands.
9. Do not add broad `danger-full-access` settings.
10. Do not add unsupported configuration keys unless the existing repository already uses them.

## Preferred Minimal Result

```toml
[agents]
max_threads = 5
max_depth = 1
```

If comments are added, they must explain the local orchestration boundary:

```toml
# Codex-local orchestration only.
# Do not add Ruflo, Claude Flow, MCP servers, hooks, or external runtime dependencies here.
```

## Validation

Run, if Python is available:

```bash
python - <<'PY'
from pathlib import Path
import tomllib
path = Path('.codex/config.toml')
with path.open('rb') as file:
    tomllib.load(file)
print('config.toml validation passed')
PY
```

## Stop Conditions

Stop if:

* Existing `.codex/config.toml` contains keys that cannot be safely preserved.
* The TOML file cannot be parsed.
* Updating the file would require guessing Codex CLI configuration semantics.

---

# Slice 4: Add or Update Codex Agents

## Goal

Extend `.codex/agents/` with Forensics-specific read-only reviewer agents and one orchestrator.

## Required Actions

1. Inspect all existing `.codex/agents/*.toml` files.
2. Preserve existing agent files.
3. Add only missing files from the list below.
4. If a file already exists, merge only missing intent safely.
5. All reviewer and orchestrator agents must use `sandbox_mode = "read-only"`.
6. `implementation_worker` remains the only write-capable role.

## Required Agent Files

### `.codex/agents/swarm_orchestrator.toml`

```toml
name = "swarm_orchestrator"
description = "Coordinates read-only agent findings into a slice-based implementation plan without modifying files."
sandbox_mode = "read-only"
developer_instructions = """
You are the Codex-local swarm orchestrator for this repository.
Do not modify files.
Read AGENTS.md, QUALITY.md, the active workflow file, and relevant agent findings.
Convert parallel read-only findings into an ordered slice plan.
Each slice must include affected files, exact intent, risks, verification commands, and stop conditions.
Do not add Ruflo, Claude Flow, MCP servers, hooks, background workers, plugin marketplace integration, or external runtime dependencies.
Stop and report if repository evidence is contradictory or incomplete.
"""
```

### `.codex/agents/forensics_architect.toml`

```toml
name = "forensics_architect"
description = "Reviews Forensics Tracing architecture, hexagonal boundaries, forensic analysis flow, and module responsibility."
sandbox_mode = "read-only"
developer_instructions = """
You are the Forensics Tracing architecture specialist.
Do not modify files.
Review whether changes preserve hexagonal architecture, domain/application purity, build-tool adapter boundaries, Gradle/Maven parity, runtime tracing boundaries, and analysis pipeline separation.
Pay special attention to BTM generation, JavaParser scanning, Joern semantic enrichment, Analysis Store persistence, and plugin adapter responsibilities.
Report findings with file paths, affected packages, severity, and concrete remediation proposals.
Do not invent architecture rules beyond AGENTS.md and existing repository documentation.
"""
```

### `.codex/agents/gradle_maven_parity_reviewer.toml`

```toml
name = "gradle_maven_parity_reviewer"
description = "Reviews Gradle and Maven connector parity for forensic capabilities."
sandbox_mode = "read-only"
developer_instructions = """
You review Gradle and Maven connector parity.
Do not modify files.
Check whether capabilities exposed through the Gradle connector are also exposed through the Maven connector and vice versa.
Verify build-tool-neutral request/result models, runner delegation, source root aggregation, include/exclude behavior, analysis-store behavior, Joern behavior, manifest/checksum structure, and generated artifact shape.
Report parity gaps with file paths, affected classes, behavior differences, and proposed remediation.
Do not recommend Gradle-only or Maven-only capabilities unless the task explicitly allows a temporary exception.
"""
```

### `.codex/agents/ast_btm_pipeline_reviewer.toml`

```toml
name = "ast_btm_pipeline_reviewer"
description = "Reviews JavaParser AST scanning, scan events, condition rendering, and Byteman rule generation flow."
sandbox_mode = "read-only"
developer_instructions = """
You review the AST-to-BTM pipeline.
Do not modify files.
Inspect JavaParser scanner behavior, scan event models, method extraction, condition rendering, import/type context preservation, BTM rendering, deterministic output, and unresolved reference handling.
Report risks that could affect generated Byteman rule correctness or runtime loading.
Prefer regression-first fixes and minimal changes.
"""
```

### `.codex/agents/joern_semantics_reviewer.toml`

```toml
name = "joern_semantics_reviewer"
description = "Reviews Joern / CPG semantic enrichment integration and artifact handling."
sandbox_mode = "read-only"
developer_instructions = """
You review Joern semantic enrichment.
Do not modify files.
Check Joern executable resolution, CPG generation, call graph, control-flow, data-flow, slices, semantic anchors, manifest/checksum integration, and Analysis Store import behavior.
Check that Joern is optional and does not break normal BTM generation when disabled.
Report risks with file paths, affected artifacts, and proposed remediation.
"""
```

### `.codex/agents/analysis_store_reviewer.toml`

```toml
name = "analysis_store_reviewer"
description = "Reviews Analysis Store persistence, manifest, checksum, and deterministic artifact behavior."
sandbox_mode = "read-only"
developer_instructions = """
You review the Forensics Analysis Store.
Do not modify files.
Check persistence model, schema usage, run identity, source file tracking, scan events, methods, generated rules, Joern imports, semantic anchors, manifest output, checksum output, cleanup policy, deterministic ordering, and reproducibility.
Report correctness, integrity, and performance risks with concrete file references.
"""
```

### `.codex/agents/test_archunit_reviewer.toml`

```toml
name = "test_archunit_reviewer"
description = "Reviews JUnit 5, ArchUnit, coverage, and regression-test quality for changed slices."
sandbox_mode = "read-only"
developer_instructions = """
You review tests and architecture verification.
Do not modify files.
Check whether changes have focused JUnit 5 tests, meaningful assertions, relevant ArchUnit coverage, regression tests for defects, and JaCoCo implications.
Verify that test placement follows repository conventions and that no quality thresholds are weakened.
Report missing tests, weak assertions, architecture gaps, and exact verification commands.
"""
```

## Existing Agent Files to Preserve

Do not delete or weaken:

```text
.codex/agents/repo_explorer.toml
.codex/agents/architecture_reviewer.toml
.codex/agents/quality_reviewer.toml
.codex/agents/security_reviewer.toml
.codex/agents/documentation_reviewer.toml
.codex/agents/implementation_worker.toml
.codex/agents/commit_reviewer.toml
```

## Optional Update to `implementation_worker.toml`

If safe, add or merge this sentence into the developer instructions:

```text
Do not run concurrently with another write-capable worker in the same working tree.
```

## Validation

Run, if Python is available:

```bash
python - <<'PY'
from pathlib import Path
import tomllib
required = {'name', 'description', 'developer_instructions'}
for path in sorted(Path('.codex/agents').glob('*.toml')):
    with path.open('rb') as file:
        data = tomllib.load(file)
    missing = required - data.keys()
    if missing:
        raise SystemExit(f'{path} misses {sorted(missing)}')
print('agent TOML validation passed')
PY
```

## Stop Conditions

Stop if:

* An existing agent with the same name has incompatible meaning.
* TOML cannot be validated.
* Updating an existing agent would require deleting its current safety instructions.

---

# Slice 5: Add or Update Skills

## Goal

Extend `.agents/skills/` with reusable workflows that support Codex-local orchestration for Forensics Tracing.

## Required Actions

1. Inspect all existing `.agents/skills/*/SKILL.md` files.
2. Preserve existing skills.
3. Add only missing skill directories.
4. If a skill already exists, merge missing sections safely.
5. Each skill must contain:

    * `# Skill:`
    * `## Description`
    * `## Instructions`
    * `## Expected Inputs`
    * `## Expected Outputs`
    * `## Stop Conditions`
6. Do not add scripts unless explicitly needed.
7. Do not add external dependencies.

## Required Skill Files

### `.agents/skills/swarm_orchestration/SKILL.md`

```md
# Skill: Swarm Orchestration

## Description
Converts a complex task into a Codex-local multi-agent workflow using read-only reviewers, one orchestrator, and one sequential implementation worker.

## Instructions
1. Read the user task.
2. Read AGENTS.md.
3. Read QUALITY.md.
4. Read the active workflow file.
5. Assign read-only review concerns to the matching Codex agents.
6. Consolidate findings into slices.
7. Ensure only implementation_worker modifies files.
8. Define verification commands for each slice.
9. Do not add Ruflo, Claude Flow, MCP servers, hooks, or background workers.

## Expected Inputs
- user task
- AGENTS.md
- QUALITY.md
- active workflow file
- relevant repository files
- read-only agent findings

## Expected Outputs
- orchestration plan
- ordered slices
- agent responsibility map
- verification plan
- risks and stop conditions

## Stop Conditions
Stop if:
- repository rules contradict the task
- a required file cannot be found
- the task requires external runtime integration not explicitly approved
- two write-capable workers would be needed concurrently
```

### `.agents/skills/forensics_slice_workflow/SKILL.md`

```md
# Skill: Forensics Slice Workflow

## Description
Creates implementation slices for Forensics Tracing features such as BTM generation, JavaParser scanning, Joern enrichment, Analysis Store persistence, Gradle/Maven connectors, runtime tracing, and reporting.

## Instructions
1. Read AGENTS.md and QUALITY.md.
2. Identify the affected forensic capability.
3. Identify the build-tool-neutral core behavior.
4. Identify Gradle adapter impact.
5. Identify Maven adapter impact.
6. Identify Analysis Store, manifest, checksum, and generated artifact impact.
7. Identify required JUnit 5 and ArchUnit tests.
8. Produce small implementation slices with verification commands.

## Expected Inputs
- user task
- affected source files
- AGENTS.md
- QUALITY.md
- README.md
- current workflow file

## Expected Outputs
- capability impact map
- ordered slice plan
- affected files per slice
- test plan
- verification commands
- open risks

## Stop Conditions
Stop if:
- capability ownership is unclear
- Gradle/Maven parity cannot be preserved
- domain/application boundary would be violated
- generated artifact behavior cannot be verified
```

### `.agents/skills/gradle_maven_parity/SKILL.md`

```md
# Skill: Gradle Maven Parity

## Description
Checks and plans equivalent forensic capability exposure across Gradle and Maven connectors.

## Instructions
1. Identify the forensic capability under change.
2. Locate the build-tool-neutral request/result model.
3. Locate the shared runner or application service.
4. Locate the Gradle mapping.
5. Locate the Maven mapping.
6. Compare behavior, defaults, source aggregation, validation, output files, manifest, checksums, Analysis Store, and Joern settings.
7. Define parity tests.
8. Report gaps before implementation.

## Expected Inputs
- AGENTS.md
- README.md
- Gradle adapter files
- Maven adapter files
- shared runner/application files
- related tests

## Expected Outputs
- parity checklist
- parity gaps
- required file changes
- required tests
- verification commands

## Stop Conditions
Stop if:
- a capability exists in only one connector and the task does not allow that exception
- shared core behavior cannot be found
- Maven or Gradle behavior would need to be guessed
```

### `.agents/skills/ast_btm_pipeline/SKILL.md`

```md
# Skill: AST BTM Pipeline

## Description
Reviews and plans changes in the JavaParser AST scanning to Byteman rule generation pipeline.

## Instructions
1. Locate scanner input configuration.
2. Locate JavaParser scanner classes.
3. Locate scan event models.
4. Locate condition rendering logic.
5. Locate BTM rendering logic.
6. Check context preservation for packages, imports, nested types, and source locations.
7. Check deterministic output ordering.
8. Define regression tests for generated rules.

## Expected Inputs
- JavaParser scanner source files
- domain/application scan event models
- BTM renderer files
- generated BTM examples or tests
- related issue or finding

## Expected Outputs
- pipeline impact analysis
- correctness risks
- regression test plan
- implementation slices
- verification commands

## Stop Conditions
Stop if:
- source context is lost and no safe propagation model is defined
- generated Byteman behavior would change without tests
- scanner and renderer responsibilities are unclear
```

### `.agents/skills/joern_semantic_analysis/SKILL.md`

```md
# Skill: Joern Semantic Analysis

## Description
Reviews and plans optional Joern / CPG semantic enrichment without breaking normal BTM generation.

## Instructions
1. Locate Joern configuration and execution code.
2. Verify that Joern is optional.
3. Check generated Joern artifacts.
4. Check manifest/checksum integration.
5. Check Analysis Store import behavior.
6. Check Gradle and Maven parity.
7. Define tests that do not require a real Joern installation unless explicitly marked integration-only.

## Expected Inputs
- Joern adapter files
- analysis-store files
- Gradle and Maven parameter mappings
- README usage documentation
- related tests

## Expected Outputs
- semantic enrichment impact analysis
- optional-runtime risk assessment
- parity checklist
- test plan
- verification commands

## Stop Conditions
Stop if:
- Joern would become required for normal BTM generation
- tests would require an unavailable local Joern installation without a test double
- artifact paths or checksums cannot be verified
```

### `.agents/skills/analysis_store_review/SKILL.md`

```md
# Skill: Analysis Store Review

## Description
Reviews persistence, manifest, checksum, and deterministic artifact behavior for the Forensics Analysis Store.

## Instructions
1. Locate Analysis Store schema and write paths.
2. Locate manifest generation.
3. Locate checksum generation.
4. Locate cleanup policy handling.
5. Locate generated artifact tests.
6. Check deterministic ordering and reproducibility.
7. Check whether Gradle and Maven produce equivalent package shapes.
8. Define tests for persistence and artifact integrity.

## Expected Inputs
- analysis-store source files
- manifest/checksum files
- Gradle/Maven output configuration
- related tests
- README documentation

## Expected Outputs
- persistence impact analysis
- artifact integrity checklist
- deterministic output risks
- test plan
- verification commands

## Stop Conditions
Stop if:
- schema changes are needed without migration strategy
- generated artifacts cannot be reproduced deterministically
- cleanup policy behavior is unclear
```

### `.agents/skills/archunit_quality_review/SKILL.md`

```md
# Skill: ArchUnit Quality Review

## Description
Reviews JUnit 5, ArchUnit, JaCoCo, and local quality-gate impact for a planned or completed slice.

## Instructions
1. Read QUALITY.md.
2. Inspect affected production files.
3. Inspect affected tests.
4. Identify required JUnit 5 regression tests.
5. Identify required ArchUnit boundary checks.
6. Identify whether JaCoCo coverage is affected.
7. Select the narrowest meaningful verification command.
8. Escalate to the full quality gate when build logic, architecture, plugin behavior, or generated artifact behavior changes.

## Expected Inputs
- QUALITY.md
- changed files
- related tests
- build files if quality behavior is affected

## Expected Outputs
- test adequacy review
- missing test list
- ArchUnit impact
- coverage risk
- verification commands
- pass/fail summary

## Stop Conditions
Stop if:
- the quality gate is unclear
- coverage would need to be weakened
- architecture rules would need to be relaxed
- verification cannot be executed and no reason is documented
```

## Existing Skills to Preserve

Do not delete or weaken:

```text
.agents/skills/slice_workflow/SKILL.md
.agents/skills/quality_gate/SKILL.md
.agents/skills/commit_message/SKILL.md
.agents/skills/documentation_sync/SKILL.md
.agents/skills/migration_workflow/SKILL.md
```

## Validation

Run:

```bash
find .agents/skills -name SKILL.md -type f | sort
```

Run, if Python is available:

```bash
python - <<'PY'
from pathlib import Path
required = [
    '# Skill:',
    '## Description',
    '## Instructions',
    '## Expected Inputs',
    '## Expected Outputs',
    '## Stop Conditions',
]
for path in sorted(Path('.agents/skills').glob('*/SKILL.md')):
    text = path.read_text(encoding='utf-8')
    for section in required:
        if section not in text:
            raise SystemExit(f'{path} misses {section}')
print('skill validation passed')
PY
```

## Stop Conditions

Stop if:

* Existing skills conflict with the new skill meaning.
* A skill cannot be safely merged.
* Required sections are missing after the update.

---

# Slice 6: Documentation Consistency Check

## Goal

Ensure that the updated orchestration structure is documented without creating unnecessary new documentation.

## Required Actions

1. Re-read `AGENTS.md`.
2. Re-read `.codex/config.toml`.
3. Re-read all `.codex/agents/*.toml` files.
4. Re-read all `.agents/skills/*/SKILL.md` files.
5. Check whether `README.md` contradicts the new orchestration setup.
6. Do not rewrite README broadly.
7. Do not add new root-level documentation files unless explicitly needed.
8. If documentation gaps remain, record them in the final report as follow-up work.

## Expected Result

A short consistency report:

```text
AGENTS.md references orchestration: yes/no
Codex agents valid: yes/no
Skills valid: yes/no
README contradiction found: yes/no
Follow-up documentation needed: yes/no
```

## Stop Conditions

Stop if:

* README or AGENTS.md directly contradicts the orchestration model.
* Fixing documentation would require broad unrelated rewrites.

---

# Slice 7: Technical Validation

## Goal

Validate the created or updated process files without running unnecessary full builds for documentation-only changes.

## Required Commands

Run:

```bash
find .codex -type f | sort
find .agents/skills -type f | sort
```

Run TOML validation if Python is available:

```bash
python - <<'PY'
from pathlib import Path
import tomllib
paths = [Path('.codex/config.toml')]
paths.extend(sorted(Path('.codex/agents').glob('*.toml')))
for path in paths:
    with path.open('rb') as file:
        tomllib.load(file)
print('TOML validation passed')
PY
```

Run skill validation if Python is available:

```bash
python - <<'PY'
from pathlib import Path
required = [
    '# Skill:',
    '## Description',
    '## Instructions',
    '## Expected Inputs',
    '## Expected Outputs',
    '## Stop Conditions',
]
for path in sorted(Path('.agents/skills').glob('*/SKILL.md')):
    text = path.read_text(encoding='utf-8')
    for section in required:
        if section not in text:
            raise SystemExit(f'{path} misses {section}')
print('Skill validation passed')
PY
```

## Build Command Policy

If only `.codex`, `.agents`, `AGENTS.md`, and `workflow.md` changed, do not run a full Gradle build unless the repository policy explicitly requires it.

If build logic, source code, tests, plugin behavior, generated artifacts, quality rules, or README usage behavior changed, run the relevant command from `QUALITY.md`.

For full verification, use the authoritative local quality gate from `QUALITY.md`:

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --console=plain --stacktrace
```

When Gradle plugin metadata, task inputs, task outputs, or plugin implementation classes changed, also run:

```bash
./gradlew validatePlugins
```

## Stop Conditions

Stop if:

* TOML validation fails.
* Skill validation fails.
* Required files are missing.
* A quality command fails.

---

# Slice 8: Final Report

## Goal

Create a precise final report without committing.

## Required Actions

1. Run `git status --short`.
2. List created files.
3. List modified files.
4. Summarize preserved rules.
5. Summarize Ruflo concepts converted.
6. Summarize Ruflo concepts intentionally not implemented.
7. Summarize validation commands and results.
8. Document open points.
9. Do not commit.

## Required Report Format

```text
Summary
- ...

Created files
- ...

Modified files
- ...

Preserved repository rules
- ...

Converted Ruflo concepts
- ...

Intentionally not implemented
- ...

Validation
- Command: ...
  Result: ...

Open points
- ...

Recommended next prompt
- ...
```

## Stop Conditions

Stop if:

* `git status` cannot be executed.
* Modified files do not match the planned scope.
* Validation failed and the report does not clearly identify the blocker.

---

# Completion Criteria

The workflow is complete when:

* `AGENTS.md` contains Codex multi-agent orchestration rules.
* `AGENTS.md` contains the Ruflo conversion boundary.
* `.codex/config.toml` remains conservative and valid.
* Existing `.codex/agents/*.toml` files are preserved.
* New Forensics-specific read-only agents exist or are explicitly reported as intentionally skipped.
* Existing `.agents/skills/*/SKILL.md` files are preserved.
* New Forensics-specific skills exist or are explicitly reported as intentionally skipped.
* No Ruflo, Claude Flow, MCP, hook, background-worker, or plugin-marketplace runtime integration was added.
* TOML validation passed or the reason it could not run is documented.
* Skill validation passed or the reason it could not run is documented.
* No quality rule was weakened.
* No architecture rule was removed.
* No commit was created.

---

# Start Prompt for Codex

Use this prompt in Codex:

```text
Work through workflow.md automatically, slice by slice.

Goal:
Convert the useful Ruflo-style orchestration concepts into the existing forensics_tracing Codex setup by extending `.agents` and `.codex` safely.

Rules:
- Read AGENTS.md and QUALITY.md first.
- Preserve existing process files.
- Do not blindly overwrite `.agents` or `.codex` files.
- Do not add Ruflo, Claude Flow, MCP servers, hooks, background workers, plugin marketplace configuration, external dependencies, or credentials.
- Use read-only agents for exploration and review.
- Use implementation_worker only for sequential file modifications.
- Do not run multiple write-capable workers in parallel.
- Do not commit.
- Stop only when a defined Stop Condition occurs.

Start with Slice 0 and continue through Slice 8.
At the end, provide the final report in the required format.
```
