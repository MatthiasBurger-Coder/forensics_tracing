# Commit Checklist

Use this checklist before committing changes in this repository.

## Required Inspection

Inspect the working tree before staging or committing:

```bash
git status
git diff
git diff --cached
```

Do not commit unrelated local files or generated build output.

## Commit Message Content

A good commit message explains:

- what changed
- why it changed
- how the Gradle or Maven plugin boundary was affected
- which files or components were touched
- whether tests were added or adjusted
- whether any behavior-relevant or breaking changes exist
- which verification commands were executed

## Repository Boundary

This repository is a thin Gradle and Maven gRPC plugin interface. Commit messages should call out any change that affects:

- Gradle extension properties
- task registration or task inputs
- Maven goals or Mojo parameters
- gRPC request mapping
- gRPC response handling
- dependency verification metadata
- plugin validation behavior
- documentation of the client/server boundary

Do not describe server-side analysis behavior as implemented here. Server functionality belongs to the Forensics Analytics server project.
