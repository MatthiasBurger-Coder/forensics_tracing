# Skill: Forensics Slice Workflow

## Description
Creates implementation slices for the thin Forensics Tracing Gradle and Maven gRPC plugins.

## Instructions
1. Read AGENTS.md and QUALITY.md.
2. Identify the affected Gradle extension, task, Maven Mojo, gRPC client, proto, test, or documentation surface.
3. Verify the exact current contract before planning edits.
4. Confirm whether the requested behavior belongs in this plugin or in the server project.
5. Identify required JUnit 5, gRPC, and ArchUnit tests.
6. Produce small implementation slices with verification commands.

## Expected Inputs
- user task
- affected source files
- AGENTS.md
- QUALITY.md
- README.md
- current workflow file

## Expected Outputs
- boundary impact map
- ordered slice plan
- affected files per slice
- test plan
- verification commands
- open risks

## Stop Conditions
Stop if:
- ownership between plugin and server is unclear
- the requested change requires local analysis behavior in this plugin
- the proto, Gradle task, or Maven Mojo contract cannot be verified
- verification commands cannot be identified
