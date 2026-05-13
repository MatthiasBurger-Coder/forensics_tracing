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
