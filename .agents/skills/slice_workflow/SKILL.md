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
