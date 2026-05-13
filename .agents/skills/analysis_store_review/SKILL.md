# Skill: Analysis Store Review

## Description
Reviews persistence, manifest, checksum, and deterministic artifact behavior for the Forensics Analysis Store.

## Instructions
1. Locate Analysis Store schema and write paths.
2. Locate manifest generation.
3. Locate checksum generation.
4. Locate cleanup policy handling.
5. Locate generated artifact tests.
6. Check deterministic ordering and reproducibility.
7. Check whether Gradle and Maven produce equivalent package shapes.
8. Define tests for persistence and artifact integrity.

## Expected Inputs
- analysis-store source files
- manifest/checksum files
- Gradle/Maven output configuration
- related tests
- README documentation

## Expected Outputs
- persistence impact analysis
- artifact integrity checklist
- deterministic output risks
- test plan
- verification commands

## Stop Conditions
Stop if:
- schema changes are needed without migration strategy
- generated artifacts cannot be reproduced deterministically
- cleanup policy behavior is unclear
