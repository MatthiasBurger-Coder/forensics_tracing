docs: fix QUALITY.md quality gate documentation

## Context

File to change: QUALITY.md
Java version: 17 (see build.gradle.kts: JavaLanguageVersion.of(17))
Gradle version: 9.1

The current QUALITY.md has three concrete problems:

1. The documented quality gate command is incomplete.
   Current: `./gradlew clean test jacocoTestReport checkPackageCoverage`
   Missing: `jacocoTestCoverageVerification` — this task is wired into `check`
   via `tasks.check { dependsOn("jacocoTestCoverageVerification") }` in build.gradle.kts
   but is not mentioned anywhere in QUALITY.md.

2. The example output shows package coverage below the 80% threshold
   (domain: 72.50% / 65.00%) without marking it as a failing result.
   A reader or agent cannot tell whether this is an acceptable or failing state.

3. There is no documented failure policy — no statement that a failing gate
   blocks commits or pushes.

## Task

Update QUALITY.md only.

Do NOT modify build.gradle.kts.
Do NOT modify any production source files.
Do NOT modify any test files.
Do NOT lower coverage thresholds.
Do NOT remove or disable any quality checks.

## Required changes

### 1. Replace the quality gate command section

Replace:
./gradlew clean test jacocoTestReport checkPackageCoverage

With the complete explicit command:
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage --console=plain --stacktrace

Add a short explanation of why each task is listed explicitly:
- `test` — runs the full test suite including ArchUnit and SOLID checks
- `jacocoTestReport` — generates the JaCoCo XML report required by downstream tasks
- `jacocoTestCoverageVerification` — verifies the configured JaCoCo coverage rules
  (wired into `check` in build.gradle.kts, but listed explicitly here for clarity)
- `checkPackageCoverage` — verifies per-package line and branch coverage against
  the 80% thresholds; this task is NOT part of the default Gradle `check` lifecycle
  and must always be called explicitly

Also document `./gradlew check` as a partial diagnostic command:
./gradlew clean check --console=plain --stacktrace

Mark it clearly as incomplete for this repository because `checkPackageCoverage`
is not wired into `check`. The complete explicit command above remains
the authoritative gate for contributors and agents.

### 2. Fix the example output

The current example shows:
de.burger.forensics.domain    72.50%   65.00%   (below threshold)

Replace it with a passing example that does not mislead:
de.burger.forensics.domain       86.20%    84.10%     0    1    40    20
de.burger.forensics.adapters     91.40%    n/a        2    0    25     0

Keep the existing tab-separated column format to match the real task output.
Add the header line as it appears in the actual task output:
Package coverage report
Line threshold: 80.00%
Branch threshold: 80.00%
packageName  lineCoverage  branchCoverage  missedLines  missedBranches  totalLines  totalBranches

If a failing example is also useful for documentation purposes, add it in a
separate clearly labeled block:
# Example of a FAILING result — gate would fail with these values:
de.burger.forensics.domain    72.50%    65.00%    11    7    40    20

Do not mix passing and failing examples without clear labels.

### 3. Add a failure policy section

Add a new section titled "Failure Policy" containing:
- The quality gate fails if any of the required tasks fails.
- A failing quality gate blocks commits and pushes.
- Coverage thresholds must not be lowered to make the gate pass.
- If a failure cannot be fixed within the current task scope, the blocker
  must be documented explicitly — do not commit anyway.
- Agents must STOP and report when the gate fails. Do not proceed silently.

## Constraints

- Keep all command examples copy-paste ready with no line breaks inside commands.
- Do not document commands that are weaker than the complete gate without
  explicitly labeling them as partial or diagnostic.
- Keep the existing Notes section at the bottom of the file unchanged unless
  it directly contradicts the corrected quality gate documentation.
- Do not rewrite the SOLID section — only the quality gate documentation changes.

## Validation

After editing QUALITY.md, verify internal consistency:
- Every command shown is syntactically correct.
- The complete gate command includes `test`, `jacocoTestReport`,
  `jacocoTestCoverageVerification`, and `checkPackageCoverage`
  in the correct order.
- The example output reflects passing values (>= 80%) unless explicitly labeled as failing.
- The failure policy section is present and unambiguous.

Do NOT run any Gradle commands as part of this task.
Do NOT commit or push as part of this task.
This task is documentation-only.