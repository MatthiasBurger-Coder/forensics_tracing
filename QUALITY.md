# Code Quality & SOLID Checks

This repository contains automated checks for SOLID principles (SRP, OCP, LSP, ISP, DIP) implemented as tests under `forensics-btmgen/forensics-plugin/src/test/java/de/burger/forensics/quality/solid/`. The rules are intentionally lenient to avoid false positives while guiding design improvements.

## Running

```
./gradlew test
```

## Package Coverage Report

The `checkPackageCoverage` task parses the JaCoCo XML report, writes a per-package report, and fails when a package is below 80% line coverage or 80% branch coverage (if branch data exists).

```
./gradlew clean test jacocoTestReport checkPackageCoverage
```

Report output (example, mocked):

```
Package coverage report
Line threshold: 80.00%
Branch threshold: 80.00%
packageName	lineCoverage	branchCoverage	missedLines	missedBranches	totalLines	totalBranches
de.burger.forensics.domain	72.50%	65.00%	11	7	40	20
de.burger.forensics.adapters	88.00%	n/a	3	0	25	0
```

The report is written to `build/reports/coverage/package-coverage.txt`.

## Notes

- The tests rely on lightweight reflection-based heuristics because no additional dependencies were introduced.
- No dependency versions have been changed.
- Gradle plugin modules avoid bundling an SLF4J provider to prevent multiple bindings.
- To tighten the rules, adjust the assertions in the SOLID test suite or extend the support utilities.
