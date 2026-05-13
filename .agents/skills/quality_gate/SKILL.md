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
