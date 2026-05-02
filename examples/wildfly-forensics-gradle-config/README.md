# WildFly Forensics Gradle Sidecar Configuration

This directory contains a sidecar Gradle build for scanning an external WildFly checkout with the Forensics BTM generator plugin.

WildFly itself remains Maven-based and is not modified. The sidecar build applies `de.burger.forensics.btmgen`, resolves that plugin from the local `forensics_tracing` repository through `includeBuild`, and passes Maven `src/main/java` source roots explicitly through `btmGen.sourceRoots`.

`scanSubprojects=false` is required because WildFly is not a Gradle multi-project build. That flag only scans Gradle subprojects in the current build; it cannot discover Maven modules in WildFly.

## Layout

The sidecar expects either an explicit WildFly checkout path:

```bash
-PwildflyRoot=/path/to/wildfly
```

or this sibling layout:

```text
workspace/
|-- forensics_tracing/
|-- wildfly/
`-- forensics_tracing/examples/wildfly-forensics-gradle-config/
```

The selected WildFly root is validated before scanning. It must be a directory containing at least `pom.xml` and `mvnw`.

## Commands

Run from this directory:

```bash
cd examples/wildfly-forensics-gradle-config

../../gradlew --version

../../gradlew printWildFlyForensicsSourceRoots \
  -PwildflyRoot=/path/to/wildfly \
  -PforensicsPluginRoot=../..

../../gradlew generateBtmRules \
  -PwildflyRoot=/path/to/wildfly \
  -PforensicsPluginRoot=../..
```

With the sibling layout:

```bash
cd forensics_tracing/examples/wildfly-forensics-gradle-config
../../gradlew printWildFlyForensicsSourceRoots
../../gradlew generateBtmRules
```

Use `printWildFlyForensicsSourceRoots` before `generateBtmRules` to confirm the selected checkout, discovered roots, output paths, and cache paths before running the expensive scan.

## Output

The generated files stay under this sidecar project and do not write into WildFly:

```text
examples/wildfly-forensics-gradle-config/.forensics/build/btm/wildfly-main.btm
examples/wildfly-forensics-gradle-config/.forensics/build/reports/wildfly-scan-profile.json
examples/wildfly-forensics-gradle-config/.forensics/cache/wildfly-scan-cache
```

The H2 parser cache uses `.forensics/cache/wildfly-scan-cache` as its database base path. H2 may create files with its normal suffixes, such as `.mv.db`, beside that base path.

## Source Root Selection

By default, the sidecar walks the WildFly checkout and includes Maven-style production roots ending in:

```text
/src/main/java
```

The walk skips these directories:

```text
.git
.gradle
.idea
.mvn
.forensics
target
```

The WildFly `testsuite` subtree is excluded by default. Include it explicitly when needed:

```bash
../../gradlew printWildFlyForensicsSourceRoots \
  -Pforensics.wildfly.includeTestsuite=true
```

Test source roots are excluded by default. Include Maven `src/test/java` roots with:

```bash
../../gradlew printWildFlyForensicsSourceRoots \
  -Pforensics.wildfly.includeTestSources=true
```

Both options can be combined.

## Scan Options

Useful optional properties:

```bash
-Pforensics.wildfly.includeTestsuite=true
-Pforensics.wildfly.includeTestSources=true
-Pforensics.wildfly.minBranches=0
-Pforensics.wildfly.includeEntryExit=false
-Pforensics.wildfly.includes=org.jboss.as.server,org.wildfly.extension.undertow
```

`forensics.wildfly.includes` is a comma-separated list of package or class prefixes that narrows generated rules to matching fully qualified class names.

`forensics.wildfly.includeEntryExit` is mapped to the verified `GenerateBtmTask.includeEntryExit` task property. The current plugin extension does not expose this setting on `btmGen`.

## Cache and Profiling

The sidecar enables the parser cache:

```kotlin
cacheEnabled.set(true)
cacheBackend.set("h2")
```

It also enables profiling and writes the report to:

```text
.forensics/build/reports/wildfly-scan-profile.json
```

Strict parsing remains disabled by default because large external repositories can contain source patterns that should not stop broad forensic discovery:

```kotlin
strictParsing.set(false)
```

Dependency-aware cache invalidation remains disabled:

```kotlin
dependencyAwareInvalidation.set(false)
```

The current production `GenerateBtmTask` throws `GradleException("Dependency-aware cache invalidation is not implemented yet.")` when this flag is enabled with the parser cache. The example keeps the flag `false` until production support exists.
