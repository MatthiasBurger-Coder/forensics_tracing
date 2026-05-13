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
