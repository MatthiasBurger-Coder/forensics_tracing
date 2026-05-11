# Workplan: `forensics_tracing` — Engine-Handoff nach `forensic_analytics` und Java-25/JUnit-6-Abschluss

## 1. Ziel

Dieses Workplan-Dokument beschreibt die notwendigen Schritte im Repository `forensics_tracing`, damit die Migration zur Analytics-Plattform tatsächlich auf dem Hauptstand abgeschlossen wird.

Zielzustand:

```text
Repository: forensics_tracing
Rolle: Build-Tool-Adapter / Plugin-Producer
Baseline: Java 25, JUnit 6, Gradle 9.4.0
Handoff: erzeugt optional ein engine-request.json für forensic_analytics
Legacy: lokale BTM-Erzeugung bleibt vorerst erhalten
Main-Branch: enthält den Engine-Request-Handoff
```

`forensics_tracing` bleibt das Gradle-/Maven-Plugin. Es soll nicht zur Analytics-Engine werden.

---

## 2. Ausgangslage

Der Vergleich hat gezeigt:

* `forensics_tracing/main` enthält noch Java-17-/JUnit-5-Konfiguration.
* `forensics_tracing/main` enthält den Engine-Request-Handoff noch nicht sichtbar.
* Der Branch `feature/migration-slice-08-plugin-adapter-boundary` enthält bereits die relevanten Handoff-Klassen und Einstellungen.
* Die Analytics-Seite kann ein `engine-request.json` bereits importieren.
* Die vollständige Migration ist erst abgeschlossen, wenn der Handoff auf `main` liegt und mit `forensic_analytics/main` getestet wurde.

---

## 3. Non-Goals

Nicht Teil dieses Workplans:

* Keine Verschiebung von Gradle-Tasks oder Maven-Mojos nach `forensic_analytics`.
* Keine Entfernung des lokalen Legacy-BTM-Modus.
* Keine direkte gRPC-Client-Pflicht im Plugin.
* Keine Einführung von Spring oder Serverlogik im Plugin.
* Keine Big-Bang-Entfernung der vorhandenen Analysefunktionen.
* Keine Senkung von Coverage- oder Architekturregeln.
* Keine Deaktivierung von Dependency Verification.

---

## 4. Zielarchitektur für dieses Repository

```text
forensics_tracing
  -> Gradle Plugin Adapter
  -> Maven Mojo Adapter
  -> lokale Legacy-BTM-Erzeugung
  -> optionale Erzeugung engine-request.json
  -> später optional: gRPC-Client oder Engine-CLI-Aufruf
```

Erlaubt:

```text
Gradle Task
Maven Mojo
Extension/Parameter Mapping
Consumer-Projekt-Erkennung
SourceSet-/Reactor-Erkennung
Lokale Artefakterzeugung
Engine-Request-Datei als Handoff-Artefakt
```

Nicht erlaubt:

```text
Analytics Server
Graphdatenbank-Orchestrierung
Replay-Engine
LLM-Kontextlogik
Joern-Docker-Ownership als Engine-Verantwortung
UI oder API-Server
```

---

## 5. Slice 0 — Preflight

### Ziel

Sicheren Arbeitszustand herstellen und beide relevanten Branches prüfen.

### Commands

```bash
git status --short
git branch --show-current
git fetch --all --prune
git branch --list
git branch --list "feature/migration-slice-08-plugin-adapter-boundary"
java --version
./gradlew --version
```

Windows PowerShell:

```powershell
git status --short
git branch --show-current
git fetch --all --prune
git branch --list
git branch --list "feature/migration-slice-08-plugin-adapter-boundary"
java --version
.\gradlew.bat --version
```

### Akzeptanzkriterien

```text
[ ] Working Tree ist sauber oder alle lokalen Änderungen sind dokumentiert.
[ ] Branch feature/migration-slice-08-plugin-adapter-boundary ist vorhanden.
[ ] Gradle Wrapper ist ausführbar.
[ ] Java 25 ist lokal verfügbar, falls die Baseline-Migration direkt mit umgesetzt wird.
```

### Stop-and-Report

Stoppen, wenn:

```text
- Unklare lokale Änderungen vorhanden sind.
- Der Handoff-Branch fehlt.
- Der Gradle Wrapper nicht läuft.
- Java 25 nicht verfügbar ist und die Baseline-Migration Teil des aktuellen Durchlaufs ist.
```

---

## 6. Slice 1 — Handoff-Branch gegen `main` prüfen

### Ziel

Ermitteln, ob `feature/migration-slice-08-plugin-adapter-boundary` sauber nach `main` übernommen werden kann.

### Commands

```bash
git switch main
git pull --ff-only
git switch feature/migration-slice-08-plugin-adapter-boundary
git pull --ff-only || true
git diff --stat main...feature/migration-slice-08-plugin-adapter-boundary
git diff main...feature/migration-slice-08-plugin-adapter-boundary -- src/main/java src/test/java build.gradle.kts gradle README.md QUALITY.md AGENTS.md
```

### Zu prüfende Handoff-Bestandteile

Mindestens vorhanden sein müssen:

```text
EngineIngestionRequest
EngineIngestionPayload
EnginePayloadKind
EngineIngestionRequestWriter
engineRequestEnabled
engineRequestFile
Gradle Mapping
Maven Mapping
BuildToolConnectorParityTest Erweiterung
EngineIngestionRequestWriterTest
```

### Akzeptanzkriterien

```text
[ ] Branch enthält Engine-Request-Modell.
[ ] Branch enthält Engine-Request-Writer.
[ ] Gradle kann engineRequestEnabled und engineRequestFile setzen.
[ ] Maven kann forensics.engineRequestEnabled und forensics.engineRequestFile setzen.
[ ] Payload-Kinds passen zu forensic_analytics.
[ ] Legacy-Modus bleibt default.
```

---

## 7. Slice 2 — Branch auf aktuellen `main` rebasen oder mergen

### Ziel

Den Handoff-Code konfliktfrei auf den aktuellen Hauptstand bringen.

### Variante A: Rebase bevorzugt für saubere Historie

```bash
git switch feature/migration-slice-08-plugin-adapter-boundary
git rebase main
```

### Variante B: Merge, falls Rebase nicht gewünscht ist

```bash
git switch feature/migration-slice-08-plugin-adapter-boundary
git merge main
```

### Konfliktregeln

Bei Konflikten:

```text
- Legacy-BTM-Verhalten erhalten.
- Handoff-Code nicht entfernen.
- Gradle- und Maven-Parität erhalten.
- Keine Analytics-Engine-Klassen in das Plugin ziehen.
- Keine Java-17-Konfiguration wiederherstellen, wenn Slice 4 direkt folgt.
```

### Targeted Verification

```bash
./gradlew test \
  --tests '*BtmGenerationRequestTest' \
  --tests '*BtmGenerationRunnerTest' \
  --tests '*EngineIngestionRequestWriterTest' \
  --tests '*BtmGenExtensionTest' \
  --tests '*GenerateBtmTaskTest' \
  --tests '*BtmGenPluginTest' \
  --tests '*MavenBtmGenParametersTest' \
  --tests '*BuildToolConnectorParityTest' \
  --tests '*PluginAdapterArchitectureTest' \
  --dependency-verification strict \
  --console=plain \
  --stacktrace
```

Windows:

```powershell
.\gradlew.bat test `
  --tests '*BtmGenerationRequestTest' `
  --tests '*BtmGenerationRunnerTest' `
  --tests '*EngineIngestionRequestWriterTest' `
  --tests '*BtmGenExtensionTest' `
  --tests '*GenerateBtmTaskTest' `
  --tests '*BtmGenPluginTest' `
  --tests '*MavenBtmGenParametersTest' `
  --tests '*BuildToolConnectorParityTest' `
  --tests '*PluginAdapterArchitectureTest' `
  --dependency-verification strict `
  --console=plain `
  --stacktrace
```

### Akzeptanzkriterien

```text
[ ] Handoff-Branch ist konfliktfrei aktuell.
[ ] Targeted Tests laufen erfolgreich.
[ ] Keine Plugin-Adapter-Verantwortung wurde nach Analytics verschoben.
```

---

## 8. Slice 3 — Handoff auf `main` bringen

### Ziel

Den Engine-Request-Handoff in `forensics_tracing/main` übernehmen.

### Vorgehen

Empfohlen:

```text
1. PR von feature/migration-slice-08-plugin-adapter-boundary nach main öffnen oder aktualisieren.
2. PR-Beschreibung mit What/Why/How/Verification ergänzen.
3. CI prüfen.
4. PR mergen.
```

Falls lokal gemergt werden soll:

```bash
git switch main
git pull --ff-only
git merge --no-ff feature/migration-slice-08-plugin-adapter-boundary
```

### Pflichtprüfung nach Merge auf `main`

```bash
git switch main
git pull --ff-only
rg -n "EngineIngestionRequest|engineRequestEnabled|engineRequestFile|EnginePayloadKind|engine-request" src README.md QUALITY.md AGENTS.md build.gradle.kts gradle || true
```

### Akzeptanzkriterien

```text
[ ] `main` enthält EngineIngestionRequest.
[ ] `main` enthält EngineIngestionRequestWriter.
[ ] `main` enthält Gradle- und Maven-Konfiguration für Engine Request.
[ ] Dokumentation beschreibt engineRequestEnabled und engineRequestFile.
[ ] Legacy default bleibt erhalten.
```

---

## 9. Slice 4 — Java 25 / JUnit 6 Baseline-Migration in `forensics_tracing`

### Ziel

`forensics_tracing` ebenfalls auf die Systembaseline Java 25 / JUnit 6 bringen.

Dieser Slice ist erforderlich, wenn das **gesamte System** dieselbe Baseline haben soll.

### Dateien

```text
gradle/libs.versions.toml
build.gradle.kts
.github/workflows/*.yml
.github/workflows/*.yaml
QUALITY.md
AGENTS.md
README.md
Commit.md
gradle/verification-metadata.xml
```

### Zentrale Änderungen

In `gradle/libs.versions.toml`:

```toml
junit = "6.0.3"
jacoco = "0.8.14"
```

Zusätzlich prüfen und bei Bedarf aktualisieren:

```text
AspectJ Weaver
Mockito
Byte Buddy
JavaParser
Lombok
```

In `build.gradle.kts`:

```kotlin
val javaBaseline = 25
val java25 = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(javaBaseline))
}
```

Alle aktiven Java-17-Stellen ersetzen:

```text
JavaLanguageVersion.of(17)
JavaVersion.VERSION_17
options.release.set(17)
java17
```

JUnit Platform Launcher über den JUnit BOM führen und keine separate `junit-platform = "1.x"` Version behalten.

### Suchbefehl

```bash
rg -n "Java 17|JDK 17|JUnit 5|junit5|java17|VERSION_17|release\.set\(17\)|JavaLanguageVersion\.of\(17\)|junit-platform\s*=|5\.13\.4|1\.11\.3|0\.8\.13" \
  AGENTS.md QUALITY.md README.md Commit.md build.gradle.kts settings.gradle.kts gradle .github src || true
```

### Verification

```bash
./gradlew clean compileJava compileTestJava --dependency-verification lenient --console=plain --stacktrace
./gradlew test --dependency-verification lenient --console=plain --stacktrace
```

Danach Dependency Verification aktualisieren, falls nötig:

```bash
./gradlew help --write-verification-metadata sha256 --dependency-verification lenient --console=plain
./gradlew clean test --write-verification-metadata sha256 --dependency-verification lenient --console=plain --stacktrace
```

Strict Gate:

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification checkPackageCoverage \
  --dependency-verification strict \
  --console=plain \
  --stacktrace

./gradlew validatePlugins \
  --dependency-verification strict \
  --no-daemon \
  --console=plain \
  --stacktrace
```

### Akzeptanzkriterien

```text
[ ] Build und Tests laufen mit Java 25.
[ ] Tests laufen mit JUnit 6.
[ ] Kein aktiver JUnit-Platform-1.x-Katalogeintrag bleibt übrig.
[ ] JaCoCo unterstützt Java-25-Bytecode.
[ ] AspectJ-/Mockito-/ByteBuddy-Tests laufen unter Java 25.
[ ] CI nutzt JDK 25.
[ ] Dependency Verification läuft strict.
```

---

## 10. Slice 5 — Engine-Request-Artefakt real erzeugen

### Ziel

Nachweisen, dass das Plugin ein von Analytics konsumierbares `engine-request.json` erzeugt.

### Beispiel Gradle

```bash
./gradlew generateBtmRules \
  -Pforensics.engineRequestEnabled=true \
  -Pforensics.engineRequestFile=build/forensics/engine-request.json \
  --dependency-verification strict \
  --console=plain \
  --stacktrace
```

Windows:

```powershell
.\gradlew.bat generateBtmRules `
  -Pforensics.engineRequestEnabled=true `
  -Pforensics.engineRequestFile=build/forensics/engine-request.json `
  --dependency-verification strict `
  --console=plain `
  --stacktrace
```

### Erwartete Artefakte

```text
build/forensics/engine-request.json
build/forensics/forensics.btm oder konfigurierte BTM-Datei
optional manifest/checksums bei analysisStoreEnabled=true
```

### Inhaltliche Prüfung

```bash
cat build/forensics/engine-request.json
```

Erwartete Struktur:

```json
{
  "schemaVersion": "...",
  "buildIdentity": { ... },
  "moduleIdentity": { ... },
  "pluginIdentity": { ... },
  "payloads": [
    {
      "payloadId": "byteman-rules",
      "kind": "RULE_ARTIFACTS",
      "contentType": "text/x-byteman",
      "file": "...",
      "attributes": { ... }
    }
  ]
}
```

### Akzeptanzkriterien

```text
[ ] engine-request.json wird erzeugt.
[ ] Payload-Dateien existieren real.
[ ] Payload-Kinds passen exakt zu forensic_analytics.
[ ] Relative und absolute Pfade sind importierbar.
[ ] Legacy-BTM-Ausgabe bleibt erhalten.
```

---

## 11. Slice 6 — Cross-Repo-Handoff-Smoke gegen `forensic_analytics`

### Ziel

Den erzeugten Request direkt mit `forensic_analytics` importieren.

### Voraussetzung

`forensic_analytics/main` ist lokal ausgecheckt und gebaut.

### Commands in `forensic_analytics`

```bash
./gradlew :forensic-analytics-cli:run \
  --args="ingest-request --request <path-to-forensics_tracing>/build/forensics/engine-request.json --output build/forensics/handoff-smoke" \
  --dependency-verification strict \
  --console=plain \
  --stacktrace
```

Windows:

```powershell
.\gradlew.bat :forensic-analytics-cli:run `
  --args="ingest-request --request D:\Projects\forensics_tracing\build\forensics\engine-request.json --output build\forensics\handoff-smoke" `
  --dependency-verification strict `
  --console=plain `
  --stacktrace
```

### Erwartete Analytics-Ausgabe

```text
status=COMPLETED
uploadedPayloads>=1
engine-request-import-summary.txt vorhanden
```

### Akzeptanzkriterien

```text
[ ] Analytics kann den von forensics_tracing erzeugten Request lesen.
[ ] Alle referenzierten Payload-Dateien werden geladen.
[ ] Import endet mit COMPLETED.
[ ] Summary enthält requestFile, status und uploadedPayloads.
```

---

## 12. Slice 7 — Dokumentation und Status abschließen

### Ziel

Repository-Dokumentation in `forensics_tracing` auf den finalen Zustand bringen.

### Zu aktualisieren

```text
README.md
QUALITY.md
AGENTS.md
workflow.md
Commit.md
optional docs/migration/MIGRATION_STATUS.md
```

### Inhalt muss erklären

```text
- forensics_tracing ist Build-Adapter / Plugin.
- forensic_analytics ist Zielplattform / Engine.
- Legacy-Modus bleibt default.
- engineRequestEnabled aktiviert den lokalen Handoff.
- engineRequestFile definiert die Handoff-Datei.
- gRPC-Client ist noch nicht Teil dieses Schritts.
- Java 25 / JUnit 6 gilt als aktive Baseline, falls Slice 4 umgesetzt wurde.
```

### Akzeptanzkriterien

```text
[ ] README beschreibt Engine-Request-Handoff.
[ ] QUALITY beschreibt Java 25/JUnit 6 oder klar den abweichenden Legacy-Status.
[ ] AGENTS ist widerspruchsfrei.
[ ] Migration Status nennt erledigte und offene Punkte.
```

---

## 13. Vollständige Definition of Done

```text
[ ] Handoff-Code liegt auf forensics_tracing/main.
[ ] engine-request.json kann vom Plugin erzeugt werden.
[ ] forensic_analytics kann das erzeugte engine-request.json importieren.
[ ] Gradle- und Maven-Adapter besitzen Parität für Engine Request.
[ ] Legacy-BTM-Modus funktioniert weiterhin.
[ ] Java 25 / JUnit 6 ist umgesetzt oder bewusst als separater offener Punkt dokumentiert.
[ ] Full Quality Gate läuft strict.
[ ] validatePlugins läuft strict.
[ ] Dokumentation ist aktualisiert.
[ ] Keine Build-Tool-Adapter wurden in forensic_analytics verschoben.
```

---

## 14. Commit-Strategie

Empfohlene Commits:

```text
feat(plugin): add engine request handoff for analytics
build: migrate tracing plugin to Java 25 and JUnit 6
test(plugin): verify analytics engine request generation
docs(migration): document tracing to analytics handoff
```

Wenn Java 25 / JUnit 6 zu groß ist, als eigener PR:

```text
PR 1: feat(plugin): add engine request handoff for analytics
PR 2: build: migrate tracing plugin to Java 25 and JUnit 6
```

---

## 15. Finaler Report

Am Ende muss der Agent berichten:

```text
- Branch/PR Status
- Geänderte Dateien
- Ob Handoff-Code auf main liegt
- Ob Java 25/JUnit 6 umgesetzt wurde
- Erzeugter engine-request.json Pfad
- Analytics-Smoke-Test Ergebnis
- Ausgeführte Commands
- Fehlgeschlagene Commands
- Sonar Status oder Skip-Grund
- Offene Risiken
```
