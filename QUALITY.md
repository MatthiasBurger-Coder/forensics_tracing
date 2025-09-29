# Code Quality & SOLID Checks

This repository contains automated checks for SOLID principles (SRP, OCP, LSP, ISP, DIP) implemented as tests under `forensics-btmgen/forensics-plugin/src/test/java/de/burger/forensics/quality/solid/`. The rules are intentionally lenient to avoid false positives while guiding design improvements.

## Running

```
./gradlew test
```

## Notes

- The tests rely on lightweight reflection-based heuristics because no additional dependencies were introduced.
- No dependency versions have been changed.
- Gradle plugin modules avoid bundling an SLF4J provider to prevent multiple bindings.
- To tighten the rules, adjust the assertions in the SOLID test suite or extend the support utilities.
