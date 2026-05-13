# Skill: Gradle Maven Parity

## Description
Checks and plans equivalent forensic capability exposure across Gradle and Maven connectors.

## Instructions
1. Identify the forensic capability under change.
2. Locate the build-tool-neutral request/result model.
3. Locate the shared runner or application service.
4. Locate the Gradle mapping.
5. Locate the Maven mapping.
6. Compare behavior, defaults, source aggregation, validation, output files, manifest, checksums, Analysis Store, and Joern settings.
7. Define parity tests.
8. Report gaps before implementation.

## Expected Inputs
- AGENTS.md
- README.md
- Gradle adapter files
- Maven adapter files
- shared runner/application files
- related tests

## Expected Outputs
- parity checklist
- parity gaps
- required file changes
- required tests
- verification commands

## Stop Conditions
Stop if:
- a capability exists in only one connector and the task does not allow that exception
- shared core behavior cannot be found
- Maven or Gradle behavior would need to be guessed
