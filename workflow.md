# Workflow: Codex Multi-Agent & Skills Structure Setup

## Ziel

Dieses Workflow-Dokument beschreibt einen automatisiert abarbeitbaren Codex-Workflow, um ein bestehendes Repository um eine saubere Multi-Agent- und Skill-Struktur zu erweitern.

Codex soll vorhandene Prozessdateien zuerst auswerten, daraus Regeln extrahieren und anschließend die neue Struktur kontrolliert anlegen.

Die Arbeit erfolgt in klar getrennten Slices. Jeder Slice hat:

* Ziel
* Eingaben
* konkrete Arbeitsschritte
* erwartete Ergebnisse
* Validierung
* Stop-Bedingungen

Codex soll die Slices der Reihe nach abarbeiten. Rückfragen sind nur erlaubt, wenn eine Stop-Bedingung eintritt.

---

## Zielstruktur

```text
.
├── AGENTS.md
├── QUALITY.md
├── workflow.md
├── .codex/
│   ├── config.toml
│   └── agents/
│       ├── repo_explorer.toml
│       ├── architecture_reviewer.toml
│       ├── quality_reviewer.toml
│       ├── security_reviewer.toml
│       ├── documentation_reviewer.toml
│       ├── implementation_worker.toml
│       └── commit_reviewer.toml
└── .agents/
    └── skills/
        ├── slice_workflow/
        │   └── SKILL.md
        ├── quality_gate/
        │   └── SKILL.md
        ├── commit_message/
        │   └── SKILL.md
        ├── documentation_sync/
        │   └── SKILL.md
        └── migration_workflow/
            └── SKILL.md
```

---

## Globale Arbeitsregeln für Codex

Codex muss während des gesamten Workflows folgende Regeln beachten:

1. Bestehende Prozessdateien dürfen nicht blind überschrieben werden.
2. Bestehende Projektregeln müssen zuerst gelesen und extrahiert werden.
3. Projektregeln dürfen nicht erfunden werden.
4. Bestehende Qualitätstore dürfen nicht abgeschwächt werden.
5. Architekturregeln müssen erhalten bleiben.
6. Hexagonale Architektur ist strikt zu bewahren, wenn sie im Projekt bereits vorgegeben ist.
7. Änderungen erfolgen Slice für Slice.
8. Kein Slice darf mehrere fachlich unabhängige Ziele vermischen.
9. Codex darf keinen Commit erstellen, außer dies wird ausdrücklich verlangt.
10. Bei unklaren, widersprüchlichen oder fehlenden Informationen muss Codex stoppen und die offenen Punkte dokumentieren.
11. Analyse-Agenten dürfen parallel arbeiten.
12. Schreibende Umsetzung erfolgt sequenziell und nur durch `implementation_worker`.
13. Mehrere write-capable Agents dürfen niemals parallel denselben Working Tree verändern.
14. Source-Code-Kommentare müssen auf Englisch sein.
15. Antworten und Abschlussberichte an den Nutzer sollen auf Deutsch erfolgen.

---

## Autopilot-Regel

Codex soll diesen Workflow möglichst automatisch abarbeiten.

Das bedeutet:

* Keine unnötigen Rückfragen.
* Keine Bestätigungsfragen zwischen den Slices.
* Keine spekulativen Erweiterungen.
* Keine Änderungen außerhalb des beschriebenen Scopes.
* Bei lösbaren Unsicherheiten: Repository prüfen und bestmöglich fortfahren.
* Bei echten Blockern: stoppen, dokumentieren, keine riskante Änderung durchführen.

Echte Blocker sind nur:

* AGENTS.md oder QUALITY.md enthalten widersprüchliche harte Vorgaben.
* Build-Konfiguration und QUALITY.md widersprechen sich so stark, dass kein korrektes Quality Gate ableitbar ist.
* Bestehende `.codex`- oder `.agents`-Struktur enthält inkompatible Regeln.
* Eine Datei müsste überschrieben werden, deren Inhalt nicht sicher integriert werden kann.
* Das Repository ist nicht lesbar oder relevante Dateien können nicht geöffnet werden.

---

# Slice 0: Repository-Inventur

## Ziel

Codex verschafft sich einen vollständigen Überblick über vorhandene Prozess-, Qualitäts-, Architektur- und Build-Dateien.

## Eingaben

* Repository-Root
* vorhandene Dokumentation
* vorhandene Build-Dateien
* vorhandene Codex-/Agent-Dateien, falls vorhanden

## Arbeitsschritte

1. Repository-Root prüfen.
2. Vorhandene Prozessdateien suchen.
3. Vorhandene Build-Dateien suchen.
4. Vorhandene `.codex`-Struktur suchen.
5. Vorhandene `.agents`-Struktur suchen.
6. Relevante Dateien lesen.
7. Eine interne Extraktionsübersicht erstellen.

## Zu suchende Prozessdateien

```text
AGENTS.md
QUALITY.md
workflow.md
WORKFLOW.md
Commit.md
COMMIT.md
Commit-Prompt*.md
migration_workplan.md
MIGRATION*.md
README.md
CONTRIBUTING.md
ARCHITECTURE.md
docs/**/*.md
```

## Zu suchende Build-Dateien

```text
build.gradle
build.gradle.kts
settings.gradle
settings.gradle.kts
gradle/libs.versions.toml
pom.xml
```

## Erwartetes Ergebnis

Eine interne Übersicht mit:

* gefundenen Prozessdateien
* gefundenen Build-Dateien
* bestehenden Agent-/Skill-Dateien
* extrahierten Projektregeln
* extrahierten Architekturregeln
* extrahierten Qualitätsregeln
* extrahierten Commit-Regeln
* extrahierten Workflow-/Slice-Regeln
* offenen Punkten
* möglichen Widersprüchen

## Validierung

Codex muss im Abschlussbericht dieses Slices kurz dokumentieren:

* welche Dateien gefunden wurden
* welche Dateien besonders relevant sind
* ob bereits eine `.codex`- oder `.agents`-Struktur existiert

## Stop-Bedingungen

Stoppen, wenn:

* das Repository nicht gelesen werden kann
* zentrale Prozessdateien zwar vorhanden sind, aber nicht geöffnet werden können
* bereits bestehende `.codex`- oder `.agents`-Dateien nicht sicher eingeordnet werden können

---

# Slice 1: Regel-Extraktion und Ownership-Modell

## Ziel

Codex ordnet die vorhandenen Regeln den richtigen Ziel-Dateien zu.

## Ownership-Modell

| Datei / Struktur            | Verantwortung                                      |
| --------------------------- | -------------------------------------------------- |
| `AGENTS.md`                 | dauerhafte Projektregeln für Codex                 |
| `QUALITY.md`                | verbindliche Build-, Test- und Quality-Gate-Regeln |
| `workflow.md`               | konkrete Aufgaben- oder Projektworkflows           |
| `.codex/config.toml`        | projektbezogene Codex-Konfiguration                |
| `.codex/agents/*.toml`      | spezialisierte Codex-Agentenrollen                 |
| `.agents/skills/*/SKILL.md` | wiederverwendbare Codex-Workflows                  |

## Arbeitsschritte

1. Regeln aus bestehenden Prozessdateien extrahieren.
2. Regeln nach Ownership-Modell sortieren.
3. Doppelte Regeln erkennen.
4. Widersprüche erkennen.
5. Bestehende harte Vorgaben markieren.
6. Regeln identifizieren, die in `AGENTS.md` ergänzt werden müssen.
7. Regeln identifizieren, die in Skills ausgelagert werden können.

## Erwartetes Ergebnis

Eine interne Mapping-Tabelle:

```text
Extracted rule -> Source file -> Target file/skill -> Action
```

Beispiele:

```text
"Work slice by slice" -> AGENTS.md / workflow.md -> AGENTS.md + slice_workflow skill -> preserve
"Run ./gradlew check" -> QUALITY.md -> QUALITY.md + quality_gate skill -> preserve
"Commit message must explain what/why/how" -> Commit.md -> AGENTS.md + commit_message skill -> preserve
```

## Validierung

Codex muss sicherstellen:

* keine bestehende harte Regel wird entfernt
* keine Qualitätsanforderung wird abgeschwächt
* keine Architekturregel wird umgedeutet
* keine neue Projektregel wird ohne Quelle erfunden

## Stop-Bedingungen

Stoppen, wenn:

* Regeln einander widersprechen
* nicht klar ist, ob eine bestehende Regel verbindlich oder veraltet ist
* `QUALITY.md` und Build-Dateien unterschiedliche harte Quality Gates nahelegen

---

# Slice 2: AGENTS.md erstellen oder aktualisieren

## Ziel

Codex erstellt oder aktualisiert `AGENTS.md` als zentrale Projektanweisung.

## Arbeitsschritte

1. Prüfen, ob `AGENTS.md` existiert.
2. Wenn vorhanden: Inhalt erhalten und vorsichtig erweitern.
3. Wenn nicht vorhanden: aus den extrahierten Regeln neu erstellen.
4. Multi-Agent- und Skill-Struktur referenzieren.
5. Stop-and-Report-Regel ergänzen.
6. No-Guessing-Regel ergänzen.
7. Commit-Disziplin ergänzen.
8. Quality-Gate-Verweis ergänzen.
9. Architekturregeln erhalten.

## Pflichtstruktur für AGENTS.md

```md
# Project Agent Instructions

## Repository Purpose

Summarize the repository purpose based only on existing documentation.

## Architecture Rules

Extract architecture rules from existing docs.

If hexagonal architecture is documented, preserve it strictly.

Domain code must not depend on:

- adapters
- framework code
- CLI
- gRPC
- persistence
- UI
- build tooling
- external systems

unless the existing project explicitly defines otherwise.

## Language and Documentation Rules

- The assistant should communicate with the user in German.
- Source-code comments must be written in English.
- Generated technical process files may be written in English unless an existing file clearly uses German and should remain German.

## Working Mode

- Work slice by slice.
- Inspect before editing.
- Plan before implementation.
- Keep changes minimal and targeted.
- Do not mix unrelated concerns in one slice.

## Quality Gate

- Read QUALITY.md before modifying code.
- Run the documented quality gate after each completed implementation slice.
- If the quality gate fails, stop and report:
  - failing command
  - failing test or check
  - likely cause
  - proposed next step

## No-Guessing Rule

- Do not invent missing files, classes, methods, APIs, packages, or commands.
- Search the repository first.
- If still unresolved, stop and report the uncertainty.

## Commit Discipline

- Do not commit unless explicitly asked.
- Before preparing a commit, inspect git status and diff.
- Commit messages must explain:
  - what changed
  - why it changed
  - how it was verified
  - known limitations

## Multi-Agent Usage

- Use read-only agents for exploration, architecture review, documentation review, security review, and quality review.
- Use implementation_worker only for one approved slice at a time.
- Do not let multiple write-capable agents modify the same working tree in parallel.

## Available Codex Agents

The project may define custom agents under `.codex/agents/`.

## Available Skills

The project may define reusable skills under `.agents/skills/`.
```

## Validierung

Codex prüft:

* `AGENTS.md` existiert
* vorhandene Regeln wurden nicht entfernt
* neue Multi-Agent-Struktur ist referenziert
* keine spekulativen Regeln wurden ergänzt

## Stop-Bedingungen

Stoppen, wenn:

* vorhandene `AGENTS.md` nicht konfliktfrei erweitert werden kann
* bestehende Regeln unklar oder widersprüchlich sind

---

# Slice 3: .codex/config.toml erstellen oder aktualisieren

## Ziel

Codex legt eine konservative projektbezogene Codex-Konfiguration an.

## Ziel-Datei

```text
.codex/config.toml
```

## Inhalt

```toml
[agents]
max_threads = 5
max_depth = 1
```

## Arbeitsschritte

1. Ordner `.codex/` anlegen, falls nicht vorhanden.
2. Prüfen, ob `.codex/config.toml` existiert.
3. Falls nicht vorhanden: Datei mit Baseline-Konfiguration erstellen.
4. Falls vorhanden: vorhandene Konfiguration erhalten und `[agents]` konservativ ergänzen.
5. Keine spekulativen MCP-Server hinzufügen.
6. Keine Credentials hinzufügen.
7. Keine Provider- oder Profil-Konfiguration hinzufügen, sofern nicht bereits vorhanden und dokumentiert.

## Validierung

Codex prüft:

* `.codex/config.toml` existiert
* `[agents]` ist vorhanden
* `max_threads = 5` ist gesetzt oder bewusst konservativer
* `max_depth = 1` ist gesetzt

Optional, falls Python verfügbar:

```bash
python - <<'PY'
from pathlib import Path
import tomllib
path = Path('.codex/config.toml')
with path.open('rb') as f:
    tomllib.load(f)
print('config.toml validation passed.')
PY
```

## Stop-Bedingungen

Stoppen, wenn:

* bestehende `.codex/config.toml` eine inkompatible Agent-Konfiguration enthält
* bestehende Konfiguration nicht ohne Risiko geändert werden kann

---

# Slice 4: Custom Agents erstellen

## Ziel

Codex erstellt projektspezifische Custom Agents unter `.codex/agents/`.

## Ziel-Ordner

```text
.codex/agents/
```

## Arbeitsschritte

1. Ordner `.codex/agents/` anlegen, falls nicht vorhanden.
2. Bestehende Agent-Dateien prüfen.
3. Fehlende Agent-Dateien erstellen.
4. Vorhandene Agent-Dateien nicht blind überschreiben.
5. Bei Namenskonflikten Inhalt vergleichen und vorsichtig mergen.
6. Read-only-Agenten mit `sandbox_mode = "read-only"` markieren, sofern unterstützt.

---

## Agent: repo_explorer

### Datei

```text
.codex/agents/repo_explorer.toml
```

### Inhalt

```toml
name = "repo_explorer"
description = "Explores repository structure, important files, modules, build setup, and existing process documentation without modifying code."
sandbox_mode = "read-only"
developer_instructions = """
You are a repository exploration agent.
Do not modify files.
Inspect the repository structure, build files, documentation, and process files.
Identify important modules, entry points, architectural boundaries, quality commands, and existing workflows.
Return concise findings with file paths and relevance.
Do not guess. If something cannot be found, report it explicitly.
"""
```

---

## Agent: architecture_reviewer

### Datei

```text
.codex/agents/architecture_reviewer.toml
```

### Inhalt

```toml
name = "architecture_reviewer"
description = "Reviews architecture boundaries, dependency direction, ports, adapters, and migration risks."
sandbox_mode = "read-only"
developer_instructions = """
You are a strict architecture reviewer.
Do not modify files.
Check whether the project follows its documented architecture rules.
Pay special attention to hexagonal architecture, dependency direction, domain purity, ports, adapters, and framework leakage.
Report violations with file paths, affected symbols, severity, and concrete remediation proposals.
Do not invent architecture rules that are not present in the repository instructions.
"""
```

---

## Agent: quality_reviewer

### Datei

```text
.codex/agents/quality_reviewer.toml
```

### Inhalt

```toml
name = "quality_reviewer"
description = "Reviews tests, coverage, build verification, Gradle or Maven quality gates, and QUALITY.md consistency."
sandbox_mode = "read-only"
developer_instructions = """
You are responsible for quality verification.
Do not modify files.
Read QUALITY.md and relevant build files before making claims.
Identify the exact commands required to verify the project.
Check whether documented quality commands match the actual build configuration.
Report missing tests, weak assertions, stale commands, coverage gaps, and verification risks.
Do not weaken existing quality gates.
"""
```

---

## Agent: security_reviewer

### Datei

```text
.codex/agents/security_reviewer.toml
```

### Inhalt

```toml
name = "security_reviewer"
description = "Reviews security-sensitive changes, dependency risks, unsafe defaults, secret handling, and test isolation."
sandbox_mode = "read-only"
developer_instructions = """
You are a security review agent.
Do not modify files.
Review changes or plans for security-sensitive risks.
Check dependency handling, secret handling, command execution, deserialization, network exposure, file-system access, and test isolation.
Report concrete risks with file paths and remediation proposals.
Avoid speculative claims. Mark uncertainty explicitly.
"""
```

---

## Agent: documentation_reviewer

### Datei

```text
.codex/agents/documentation_reviewer.toml
```

### Inhalt

```toml
name = "documentation_reviewer"
description = "Reviews README, workflow files, examples, AGENTS.md, QUALITY.md, and generated process documentation for consistency."
sandbox_mode = "read-only"
developer_instructions = """
You review documentation consistency.
Do not modify files unless explicitly assigned a documentation-only slice.
Check README, AGENTS.md, QUALITY.md, workflow files, examples, and migration documents.
Find stale commands, outdated examples, contradictory rules, missing setup steps, and unclear instructions.
Return concrete documentation findings with file paths and proposed edits.
"""
```

---

## Agent: implementation_worker

### Datei

```text
.codex/agents/implementation_worker.toml
```

### Inhalt

```toml
name = "implementation_worker"
description = "Implements exactly one approved slice at a time with minimal, targeted changes."
developer_instructions = """
You are an implementation worker.
Modify files only for the explicitly approved slice.
Keep unrelated files untouched.
Follow AGENTS.md, QUALITY.md, and the active workflow file.
Prefer small, defensible changes.
After editing, run the relevant checks from QUALITY.md where feasible.
If requirements are unclear, conflicting, or unsupported by the repository, stop and report instead of guessing.
"""
```

---

## Agent: commit_reviewer

### Datei

```text
.codex/agents/commit_reviewer.toml
```

### Inhalt

```toml
name = "commit_reviewer"
description = "Reviews git status, staged and unstaged diffs, verification evidence, and prepares a traceable commit message."
sandbox_mode = "read-only"
developer_instructions = """
You are a commit review agent.
Do not modify files.
Inspect git status, staged changes, unstaged changes, and relevant diffs.
Verify whether the documented quality gate was run.
Prepare a commit message that explains:
- what changed
- why it changed
- how it was verified
- known limitations or follow-up work
Do not recommend committing if the quality gate failed or verification is missing.
"""
```

## Validierung

Codex prüft, ob alle Agent-Dateien vorhanden sind:

```text
.codex/agents/repo_explorer.toml
.codex/agents/architecture_reviewer.toml
.codex/agents/quality_reviewer.toml
.codex/agents/security_reviewer.toml
.codex/agents/documentation_reviewer.toml
.codex/agents/implementation_worker.toml
.codex/agents/commit_reviewer.toml
```

Optional, falls Python verfügbar:

```bash
python - <<'PY'
from pathlib import Path
import tomllib

agent_dir = Path('.codex/agents')
required = ['name', 'description', 'developer_instructions']

for path in sorted(agent_dir.glob('*.toml')):
    with path.open('rb') as f:
        data = tomllib.load(f)
    for key in required:
        if key not in data:
            raise SystemExit(f'{path} misses {key}')
print('Agent TOML validation passed.')
PY
```

## Stop-Bedingungen

Stoppen, wenn:

* bestehende Agent-Dateien inkompatible Rollen mit gleichem Namen enthalten
* TOML-Dateien nicht valide geschrieben werden können
* bestehende Agent-Instruktionen nicht sicher erhalten werden können

---

# Slice 5: Skills erstellen

## Ziel

Codex erstellt wiederverwendbare Skills unter `.agents/skills/`.

## Ziel-Ordner

```text
.agents/skills/
```

## Arbeitsschritte

1. Ordner `.agents/skills/` anlegen, falls nicht vorhanden.
2. Bestehende Skills prüfen.
3. Fehlende Skills erstellen.
4. Vorhandene Skills nicht blind überschreiben.
5. Bei Namenskonflikten Inhalt vergleichen und vorsichtig mergen.
6. Jeder Skill muss eine `SKILL.md` enthalten.

---

## Skill: slice_workflow

### Datei

```text
.agents/skills/slice_workflow/SKILL.md
```

### Inhalt

```md
# Skill: Slice Workflow

## Description

Creates a structured slice-based implementation plan from a task, repository rules, and existing workflow documentation.

## Instructions

1. Read the user task.
2. Read AGENTS.md.
3. Read QUALITY.md.
4. Inspect relevant repository files.
5. Identify the smallest meaningful implementation slices.
6. Order slices by dependency and risk.
7. Define done criteria for each slice.
8. Do not implement before the slice plan is complete.

## Expected Inputs

- user task
- AGENTS.md
- QUALITY.md
- existing workflow files
- relevant source files

## Expected Outputs

- ordered slice plan
- affected files per slice
- verification commands per slice
- risks and open points

## Stop Conditions

Stop if:

- requirements are contradictory
- required files cannot be found
- the quality gate is unclear
- the requested change conflicts with architecture rules
```

---

## Skill: quality_gate

### Datei

```text
.agents/skills/quality_gate/SKILL.md
```

### Inhalt

```md
# Skill: Quality Gate

## Description

Identifies and executes the repository quality gate without weakening existing verification rules.

## Instructions

1. Read QUALITY.md.
2. Inspect build files.
3. Identify the correct build tool.
4. Identify test, coverage, validation, and verification commands.
5. Run checks where feasible.
6. Summarize pass/fail results.
7. Report exact failing commands.

## Expected Inputs

- QUALITY.md
- build.gradle or build.gradle.kts
- settings.gradle or settings.gradle.kts
- pom.xml if present
- current diff

## Expected Outputs

- commands executed
- result summary
- failing checks
- suspected cause
- recommended next step

## Stop Conditions

Stop if:

- the documented quality gate is inconsistent with the build
- a command fails
- a test fails
- required tooling is missing
- thresholds would need to be changed
```

---

## Skill: commit_message

### Datei

```text
.agents/skills/commit_message/SKILL.md
```

### Inhalt

```md
# Skill: Commit Message

## Description

Creates a traceable commit message from git status, diffs, and verification evidence.

## Instructions

1. Inspect git status.
2. Inspect staged and unstaged changes.
3. Read relevant diffs.
4. Check whether quality commands were run.
5. Create a commit message explaining:
   - what changed
   - why it changed
   - how it was verified
   - known limitations

## Expected Inputs

- git status
- git diff
- verification results
- related workflow or issue context

## Expected Outputs

- proposed commit title
- detailed commit body
- verification section
- limitations section

## Stop Conditions

Stop if:

- quality gate failed
- verification is missing
- staged and unstaged changes are mixed unexpectedly
- unrelated changes are present
```

---

## Skill: documentation_sync

### Datei

```text
.agents/skills/documentation_sync/SKILL.md
```

### Inhalt

```md
# Skill: Documentation Sync

## Description

Keeps project documentation, examples, workflow files, and process instructions consistent with the current implementation.

## Instructions

1. Inspect README.md.
2. Inspect AGENTS.md.
3. Inspect QUALITY.md.
4. Inspect workflow files.
5. Inspect examples.
6. Compare documented commands with build files.
7. Identify stale examples, outdated commands, and contradictory instructions.
8. Propose documentation-only slices.

## Expected Inputs

- README.md
- AGENTS.md
- QUALITY.md
- workflow files
- examples
- build files

## Expected Outputs

- documentation findings
- stale sections
- proposed corrections
- documentation-only slice plan

## Stop Conditions

Stop if:

- implementation behavior cannot be verified
- documentation contradicts itself
- a command cannot be validated
```

---

## Skill: migration_workflow

### Datei

```text
.agents/skills/migration_workflow/SKILL.md
```

### Inhalt

```md
# Skill: Migration Workflow

## Description

Plans and executes repository or module migrations in small, verifiable slices while preserving architecture and quality rules.

## Instructions

1. Inspect the source project.
2. Inspect the target project.
3. Identify reusable implementation parts.
4. Identify incompatible assumptions.
5. Create a migration slice plan.
6. Preserve architecture boundaries.
7. Run the quality gate after each implementation slice.
8. Document risks and open points.

## Expected Inputs

- source repository
- target repository
- migration task
- AGENTS.md
- QUALITY.md
- migration_workplan.md if present

## Expected Outputs

- migration plan
- ordered slices
- files to move or adapt
- verification plan
- risks and unresolved questions

## Stop Conditions

Stop if:

- source and target architecture conflict
- required source files cannot be found
- target quality gate is unclear
- migration would require speculative behavior changes
```

## Validierung

Codex prüft:

```text
.agents/skills/slice_workflow/SKILL.md
.agents/skills/quality_gate/SKILL.md
.agents/skills/commit_message/SKILL.md
.agents/skills/documentation_sync/SKILL.md
.agents/skills/migration_workflow/SKILL.md
```

Zusätzlich muss jede `SKILL.md` enthalten:

* `# Skill:`
* `## Description`
* `## Instructions`
* `## Expected Inputs`
* `## Expected Outputs`
* `## Stop Conditions`

## Stop-Bedingungen

Stoppen, wenn:

* bestehende Skills mit gleichem Namen inkompatible Bedeutung haben
* vorhandene Skill-Inhalte nicht sicher integriert werden können

---

# Slice 6: Dokumentationsabgleich

## Ziel

Codex gleicht die neu angelegte Struktur mit vorhandener Dokumentation ab.

## Arbeitsschritte

1. `AGENTS.md` erneut lesen.
2. `QUALITY.md` erneut lesen.
3. `README.md` prüfen, falls vorhanden.
4. Bestehende `workflow.md` prüfen, falls vorhanden.
5. Prüfen, ob die neue Multi-Agent-Struktur irgendwo erwähnt werden sollte.
6. Keine umfangreichen README-Umbauten durchführen, außer die bestehende Dokumentation wäre sonst offensichtlich falsch.
7. Falls größere Dokumentationsänderungen nötig wären: als offenen Folge-Slice dokumentieren, nicht automatisch großflächig ändern.

## Erwartetes Ergebnis

* kleine notwendige Referenzen ergänzt
* keine übermäßige Dokumentationsumschreibung
* offene Dokumentationsaufgaben dokumentiert

## Validierung

Codex prüft:

* `AGENTS.md` verweist auf `.codex/agents/`
* `AGENTS.md` verweist auf `.agents/skills/`
* `QUALITY.md` wurde nicht abgeschwächt
* README wurde nicht unnötig umgeschrieben

## Stop-Bedingungen

Stoppen, wenn:

* bestehende Dokumentation dem neuen Setup widerspricht
* README oder andere zentrale Dokumentation großflächig angepasst werden müsste

---

# Slice 7: Technische Validierung

## Ziel

Codex validiert die erzeugte Struktur technisch so weit wie möglich, ohne einen teuren Full Build zu erzwingen.

## Arbeitsschritte

1. Prüfen, ob alle Ziel-Dateien existieren.
2. TOML-Dateien syntaktisch prüfen, falls möglich.
3. Skill-Dateien auf Pflichtabschnitte prüfen.
4. Markdown-Dateien auf grobe Vollständigkeit prüfen.
5. Keine teuren Builds ausführen, sofern nur Dokumentation und Codex-Konfiguration geändert wurden.

## Pflichtprüfung

```bash
find .codex -type f | sort
find .agents/skills -type f | sort
```

## Optionale TOML-Prüfung

```bash
python - <<'PY'
from pathlib import Path
import tomllib

paths = [Path('.codex/config.toml')]
paths.extend(sorted(Path('.codex/agents').glob('*.toml')))

for path in paths:
    with path.open('rb') as f:
        tomllib.load(f)
print('TOML validation passed.')
PY
```

## Optionale Skill-Prüfung

```bash
python - <<'PY'
from pathlib import Path

required_sections = [
    '# Skill:',
    '## Description',
    '## Instructions',
    '## Expected Inputs',
    '## Expected Outputs',
    '## Stop Conditions',
]

for path in sorted(Path('.agents/skills').glob('*/SKILL.md')):
    text = path.read_text(encoding='utf-8')
    for section in required_sections:
        if section not in text:
            raise SystemExit(f'{path} misses {section}')
print('Skill validation passed.')
PY
```

## Erwartetes Ergebnis

* Struktur vorhanden
* TOML syntaktisch valide
* Skills vollständig
* keine unnötigen Build-Kommandos ausgeführt

## Stop-Bedingungen

Stoppen, wenn:

* TOML ungültig ist
* Pflichtdateien fehlen
* Skills unvollständig sind

---

# Slice 8: Abschlussbericht erstellen

## Ziel

Codex erstellt einen nachvollziehbaren Abschlussbericht ohne Commit.

## Arbeitsschritte

1. `git status` prüfen.
2. Geänderte Dateien auflisten.
3. Neu erstellte Dateien auflisten.
4. Kurz erklären, welche Regeln extrahiert wurden.
5. Kurz erklären, welche Regeln nicht auflösbar waren.
6. Validierungsergebnisse dokumentieren.
7. Empfohlenen Folgeprompt ausgeben.
8. Nicht committen.

## Abschlussbericht muss enthalten

```text
Summary
- Created files
- Modified files
- Preserved existing rules
- Extracted quality commands
- Open points
- Validation results
- Recommended next prompt
```

## Empfohlener Folgeprompt

```text
Read AGENTS.md, QUALITY.md, .codex/agents, and .agents/skills.

Use repo_explorer, architecture_reviewer, quality_reviewer, security_reviewer, and documentation_reviewer in read-only mode first.

Consolidate their findings.
Create an implementation slice plan.
Do not modify code until Slice 1 is clearly defined.

Use implementation_worker only for Slice 1.
After Slice 1, run the required checks from QUALITY.md.
Summarize the diff, verification result, risks, and open points.
Do not continue with Slice 2 unless explicitly instructed.
```

## Stop-Bedingungen

Stoppen, wenn:

* `git status` nicht ausgeführt werden kann
* Änderungen nicht nachvollziehbar sind
* Validierung fehlgeschlagen ist

---

# Gesamte Abschlusskriterien

Der Workflow ist vollständig abgeschlossen, wenn:

* `AGENTS.md` existiert.
* `AGENTS.md` referenziert `.codex/agents/`.
* `AGENTS.md` referenziert `.agents/skills/`.
* `.codex/config.toml` existiert.
* `.codex/agents/repo_explorer.toml` existiert.
* `.codex/agents/architecture_reviewer.toml` existiert.
* `.codex/agents/quality_reviewer.toml` existiert.
* `.codex/agents/security_reviewer.toml` existiert.
* `.codex/agents/documentation_reviewer.toml` existiert.
* `.codex/agents/implementation_worker.toml` existiert.
* `.codex/agents/commit_reviewer.toml` existiert.
* `.agents/skills/slice_workflow/SKILL.md` existiert.
* `.agents/skills/quality_gate/SKILL.md` existiert.
* `.agents/skills/commit_message/SKILL.md` existiert.
* `.agents/skills/documentation_sync/SKILL.md` existiert.
* `.agents/skills/migration_workflow/SKILL.md` existiert.
* vorhandene Prozessdokumentation wurde erhalten oder sauber referenziert.
* keine Qualitätsregeln wurden abgeschwächt.
* keine Architekturregeln wurden entfernt.
* offene Punkte wurden dokumentiert.
* kein Commit wurde erstellt.

---

# Startprompt für Codex

Diesen Prompt kann der Nutzer direkt in Codex verwenden:

```text
Work through workflow.md automatically slice by slice.

Follow the Autopilot Rule:
- Do not ask for confirmation between slices.
- Do not commit.
- Do not overwrite existing process files blindly.
- Preserve existing rules.
- Stop only if a defined Stop Condition occurs.

Start with Slice 0 and continue until Slice 8 is complete.

At the end, provide:
- created files
- modified files
- preserved rules
- validation results
- open points
- recommended next prompt
```
