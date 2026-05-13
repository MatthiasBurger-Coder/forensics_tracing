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
