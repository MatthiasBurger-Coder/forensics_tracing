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
