# workflow.md — Persistent Forensics Analysis Store Foundation

## 1. Ziel dieses Workflows

Dieser Workflow erweitert das bestehende `forensics_tracing` Gradle-Plugin um die Grundlage für einen persistenten, versionierten **Forensics Analysis Store**.

Das Plugin soll nach erfolgreichem Scan- und BTM-Generierungslauf nicht mehr nur eine `.btm`-Datei erzeugen, sondern einen vollständigen statischen Rohdaten-Snapshot vorbereiten.

Zielartefakte dieses Slices:

```text
build/forensics/
├── forensics.btm
├── manifest.json
├── checksums.sha256
└── analysis-store/
    └── <h2 database files>
```

Der spätere Joern-Import, gRPC-Push, Runtime-Trace-Stream, Replay-Kontext und LLM-Kontext sind **nicht Bestandteil dieses Workflows**. Dieser Workflow baut nur das stabile Fundament dafür.

---

## 2. Architekturentscheidung

Die bisher temporäre H2-Scan-Datenbank wird zu einem persistenten, versionierten **Forensics Analysis Store** erweitert.

Der Analysis Store enthält in diesem Schritt:

```text
analysis_run
source_file
scan_method
scan_event
btm_rule
artifact_checksum
```

Alle Daten werden über eine gemeinsame `BuildIdentity` verbunden.

Diese `BuildIdentity` ist später der Vertrag zwischen:

```text
Gradle Plugin
BTM-Regeln
Runtime-Trace-Events
forensic_analytics Server
Joern-Semantikdaten
```

Wichtig:

```text
Joern wird in diesem Workflow NICHT eingebaut.
gRPC wird in diesem Workflow NICHT eingebaut.
Replay / LLM / Server-Import werden in diesem Workflow NICHT eingebaut.
```

---

## 3. Technische Leitplanken

* Gradle 9.1 verwenden.
* Die im Repository konfigurierte Java-Toolchain nicht ändern.
* Keine Änderung an der fachlichen BTM-Regel-Semantik.
* Keine Coverage-Schwellen senken.
* Keine bestehenden Tests entfernen oder deaktivieren.
* Keine Joern-Abhängigkeiten hinzufügen.
* Keine gRPC-Abhängigkeiten hinzufügen.
* Keine Server-Kommunikation implementieren.
* Keine Runtime-Replay-Logik implementieren.
* Source-Code-Kommentare ausschließlich auf Englisch schreiben.
* Hexagonale Architektur einhalten.
* Domain und Application dürfen nicht von H2, Gradle, Dateisystemdetails oder Plugin-Klassen abhängen.

---

## 4. Aktueller relevanter Stand im Projekt

Vorhandene zentrale Klassen:

```text
src/main/java/de/burger/forensics/domain/port/out/CodeScanPort.java
src/main/java/de/burger/forensics/domain/port/out/RuleRenderPort.java
src/main/java/de/burger/forensics/domain/model/ScanEvent.java
src/main/java/de/burger/forensics/domain/model/Rule.java
src/main/java/de/burger/forensics/domain/model/RuleId.java
src/main/java/de/burger/forensics/domain/model/RuleIdFactory.java
src/main/java/de/burger/forensics/application/AnalysisContext.java
src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java
src/main/java/de/burger/forensics/application/service/RuleGenerationResult.java
src/main/java/de/burger/forensics/plugin/btmgen/gradle/BtmGenExtension.java
src/main/java/de/burger/forensics/plugin/btmgen/gradle/BtmGenPlugin.java
src/main/java/de/burger/forensics/plugin/btmgen/gradle/GenerateBtmTask.java
src/main/java/de/burger/forensics/plugin/btmgen/writer/BtmFileWriter.java
```

Der bestehende Task `generateBtmRules` muss weiterhin funktionieren.

---

## 5. Zielbild nach Abschluss dieses Workflows

Nach erfolgreichem Lauf von:

```bash
./gradlew generateBtmRules
```

sollen zusätzlich zur BTM-Datei folgende Artefakte entstehen:

```text
build/forensics/forensics.btm
build/forensics/manifest.json
build/forensics/checksums.sha256
build/forensics/analysis-store/<h2 files>
```

Der Lauf gilt als erfolgreich, wenn:

```text
1. Source-Dateien fingerprinted wurden.
2. ScanEvents in H2 gespeichert wurden.
3. Methodeninformationen in H2 gespeichert wurden.
4. BTM-Regeln mit stabilen IDs in H2 gespeichert wurden.
5. Die BTM-Datei weiterhin korrekt erzeugt wird.
6. Eine manifest.json geschrieben wird.
7. Checksums für relevante Artefakte geschrieben werden.
8. Die Cleanup-Policy steuert, ob die H2-Datenbank erhalten bleibt.
```

---

## 6. Nicht-Ziele

Nicht umsetzen:

```text
Joern CLI Adapter
Joern JSON Import
joern_node / joern_edge Tabellen
gRPC Publisher
forensic_analytics Server API
TraceIngestService
Runtime Event Streaming
Replay Engine
LLM Prompt Builder
Graph DB Export
Vector Store Export
```

Diese Themen bauen später auf diesem Store auf.

---

## 7. STOP-Regeln für Codex

Codex muss stoppen und berichten, wenn eine dieser Situationen eintritt:

```text
1. Es existiert bereits eine H2-Store-Implementierung mit anderem Schema.
2. Es existiert bereits ein Analysis-Run-Konzept mit inkompatiblen IDs.
3. Die vorhandene GenerateRulesUseCase-Struktur wurde im Checkout wesentlich verändert.
4. Das Projekt verwendet bereits einen anderen Datenbank-Migrationsmechanismus.
5. Die Java-Toolchain müsste geändert werden, um den Workflow umzusetzen.
6. Die BTM-Ausgabe würde sich fachlich ändern.
7. Bestehende ArchUnit-Regeln würden nur durch Aufweichen erfüllbar.
8. Neue Abhängigkeiten würden Dependency Verification blockieren und keine saubere Metadatenpflege möglich sein.
```

Keine stillen Architekturannahmen treffen.

---

# 8. Slices

## Slice 0 — Preflight und Ist-Zustand sichern

### Ziel

Vor Änderungen den aktuellen Zustand erfassen und sicherstellen, dass der Workflow auf dem echten Projektstand aufsetzt.

### Aufgaben

1. Git-Status prüfen.
2. Aktuelle Projektstruktur prüfen.
3. Vorhandene H2-, DB-, Store- oder Analysis-Run-Klassen suchen.
4. Aktuelle Gradle-Tasks prüfen.
5. Basistests ausführen.

### Befehle

```bash
git status --short
find src/main/java -type f | sort
find src/test/java -type f | sort
./gradlew clean test
./gradlew check
./gradlew validatePlugins
```

### Akzeptanzkriterien

* Ausgangszustand ist bekannt.
* Bestehende Tests laufen oder bestehende Fehler sind dokumentiert.
* Keine Codeänderung in diesem Slice.

---

## Slice 1 — Domain-Modell für BuildIdentity und Analysis Run einführen

### Ziel

Stabile, domänennahe Identitäten schaffen, die später für Joern, gRPC und Runtime-Trace wiederverwendet werden können.

### Neue Packages

```text
src/main/java/de/burger/forensics/domain/model/analysis/
```

### Neue Klassen

```text
AnalysisRunId.java
BuildId.java
BuildIdentity.java
AnalysisRunStatus.java
AnalysisStoreCleanupPolicy.java
AnalysisSchemaVersion.java
SourceFingerprint.java
ArtifactChecksum.java
```

### Inhalt

`AnalysisRunId`:

```text
- String value
- darf nicht null oder blank sein
- Factory für random UUID
```

`BuildId`:

```text
- String value
- darf nicht null oder blank sein
- später deterministisch aus Fingerprints ableitbar
```

`BuildIdentity`:

```text
projectKey
analysisRunId
buildId
sourceFingerprint
classpathFingerprint
btmRulesFingerprint
artifactFingerprint
pluginVersion
schemaVersion
createdAt
```

In diesem Slice dürfen diese Felder teilweise mit `UNKNOWN` oder `NOT_COMPUTED` belegt werden, solange das Modell stabil ist und später erweitert werden kann.

`AnalysisRunStatus`:

```text
CREATED
SCANNING
BTM_GENERATED
COMPLETED
FAILED
```

`AnalysisStoreCleanupPolicy`:

```text
DELETE_ON_SUCCESS
KEEP_ON_SUCCESS
KEEP_ON_FAILURE
KEEP_ALWAYS
```

Empfohlener Default für diesen Entwicklungsschritt:

```text
KEEP_ON_SUCCESS
```

### Tests

Neue Tests:

```text
src/test/java/de/burger/forensics/domain/model/analysis/AnalysisRunIdTest.java
src/test/java/de/burger/forensics/domain/model/analysis/BuildIdentityTest.java
src/test/java/de/burger/forensics/domain/model/analysis/ArtifactChecksumTest.java
```

### Akzeptanzkriterien

* Domain-Modelle enthalten keine H2-, Gradle- oder Dateisystemabhängigkeiten.
* Ungültige IDs werden abgelehnt.
* Tests decken Blank-/Null-Fälle ab.

---

## Slice 2 — AnalysisStorePort definieren

### Ziel

Eine saubere Port-Schnittstelle für das Speichern der Analyse-Rohdaten schaffen.

### Neue Datei

```text
src/main/java/de/burger/forensics/domain/port/out/AnalysisStorePort.java
```

### Erwartete Verantwortung

Der Port speichert:

```text
analysis_run
source_file
scan_method
scan_event
btm_rule
artifact_checksum
```

### Vorgeschlagene Schnittstelle

```java
package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisRunStatus;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFileSnapshot;

import java.util.List;
import java.util.Map;

/**
 * Stores raw data produced during a forensics analysis run.
 */
public interface AnalysisStorePort extends AutoCloseable {

    void initializeSchema();

    void createAnalysisRun(BuildIdentity identity);

    void updateAnalysisRunStatus(AnalysisRunId analysisRunId, AnalysisRunStatus status);

    void storeSourceFiles(AnalysisRunId analysisRunId, List<SourceFileSnapshot> sourceFiles);

    void storeScanEvents(AnalysisRunId analysisRunId, List<ScanEvent> events);

    void storeRules(AnalysisRunId analysisRunId, List<Rule> rules, Map<String, String> renderedRulesByRuleId);

    void storeArtifactChecksums(AnalysisRunId analysisRunId, List<ArtifactChecksum> checksums);

    @Override
    void close();
}
```

Falls `SourceFileSnapshot` noch nicht existiert, in `domain/model/analysis` ergänzen.

### Neue Klasse

```text
SourceFileSnapshot.java
```

Felder:

```text
relativePath
absolutePath
sha256
fileSize
lastModifiedEpochMillis
```

### Akzeptanzkriterien

* `AnalysisStorePort` liegt im Domain-Port-Package.
* Keine H2- oder SQL-Klassen im Port.
* Keine Gradle-Typen im Port.
* Keine File-I/O-Implementierung im Domain-Modell.

---

## Slice 3 — H2 Analysis Store Adapter implementieren

### Ziel

Den Outbound-Adapter für H2 erstellen, ohne die Domain mit H2 zu koppeln.

### Dependency

In `gradle/libs.versions.toml` eine H2-Abhängigkeit ergänzen.

In `build.gradle.kts` die H2-Abhängigkeit als `implementation` ergänzen.

Keine Joern- oder gRPC-Abhängigkeiten hinzufügen.

### Neue Packages

```text
src/main/java/de/burger/forensics/adapters/h2/
```

### Neue Klassen

```text
H2AnalysisStoreAdapter.java
H2AnalysisStoreConfig.java
H2ConnectionFactory.java
H2SchemaInitializer.java
SqlTransactionRunner.java
```

### Schema

Minimal benötigte Tabellen:

```sql
CREATE TABLE IF NOT EXISTS analysis_run (
    analysis_run_id VARCHAR(64) PRIMARY KEY,
    project_key VARCHAR(255) NOT NULL,
    build_id VARCHAR(128) NOT NULL,
    source_fingerprint VARCHAR(128),
    classpath_fingerprint VARCHAR(128),
    btm_rules_fingerprint VARCHAR(128),
    artifact_fingerprint VARCHAR(128),
    plugin_version VARCHAR(64),
    schema_version VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS source_file (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    analysis_run_id VARCHAR(64) NOT NULL,
    relative_path VARCHAR(2048) NOT NULL,
    absolute_path VARCHAR(4096) NOT NULL,
    sha256 VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    last_modified_epoch_millis BIGINT NOT NULL,
    CONSTRAINT fk_source_file_run FOREIGN KEY (analysis_run_id) REFERENCES analysis_run(analysis_run_id)
);

CREATE TABLE IF NOT EXISTS scan_method (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    analysis_run_id VARCHAR(64) NOT NULL,
    method_key VARCHAR(4096) NOT NULL,
    fqcn VARCHAR(2048) NOT NULL,
    method_name VARCHAR(512) NOT NULL,
    signature VARCHAR(4096),
    return_type VARCHAR(1024),
    CONSTRAINT fk_scan_method_run FOREIGN KEY (analysis_run_id) REFERENCES analysis_run(analysis_run_id)
);

CREATE TABLE IF NOT EXISTS scan_event (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    analysis_run_id VARCHAR(64) NOT NULL,
    fqcn VARCHAR(2048) NOT NULL,
    method_name VARCHAR(512) NOT NULL,
    signature VARCHAR(4096),
    rule_template VARCHAR(64) NOT NULL,
    line_number INT NOT NULL,
    condition_text CLOB,
    language VARCHAR(64),
    return_type VARCHAR(1024),
    CONSTRAINT fk_scan_event_run FOREIGN KEY (analysis_run_id) REFERENCES analysis_run(analysis_run_id)
);

CREATE TABLE IF NOT EXISTS btm_rule (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    analysis_run_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(128) NOT NULL,
    fqcn VARCHAR(2048) NOT NULL,
    method_name VARCHAR(512) NOT NULL,
    rule_template VARCHAR(64) NOT NULL,
    line_number INT NOT NULL,
    rendered_rule CLOB NOT NULL,
    emitted_to_btm BOOLEAN NOT NULL,
    CONSTRAINT fk_btm_rule_run FOREIGN KEY (analysis_run_id) REFERENCES analysis_run(analysis_run_id)
);

CREATE TABLE IF NOT EXISTS artifact_checksum (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    analysis_run_id VARCHAR(64) NOT NULL,
    artifact_path VARCHAR(4096) NOT NULL,
    artifact_type VARCHAR(128) NOT NULL,
    sha256 VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    CONSTRAINT fk_artifact_checksum_run FOREIGN KEY (analysis_run_id) REFERENCES analysis_run(analysis_run_id)
);
```

### Hinweise

* Schema-Initialisierung idempotent bauen.
* Keine Flyway-/Liquibase-Einführung in diesem Slice.
* SQL nur im H2-Adapter halten.
* Transaktionen verwenden.
* Connection sauber schließen, bevor Checksums über H2-Dateien berechnet werden.

### Tests

Neue Tests:

```text
src/test/java/de/burger/forensics/adapters/h2/H2AnalysisStoreAdapterTest.java
src/test/java/de/burger/forensics/adapters/h2/H2SchemaInitializerTest.java
```

Testfälle:

```text
- Schema kann in leerer DB initialisiert werden.
- createAnalysisRun speichert Run.
- storeScanEvents speichert Events.
- storeRules speichert RuleId und gerenderte Regel.
- Status kann aktualisiert werden.
- Adapter kann geschlossen werden.
```

### Akzeptanzkriterien

* H2-Abhängigkeit bleibt auf Adapterebene.
* Domain und Application importieren keine H2-Klassen.
* Tests laufen mit temporärem Verzeichnis.

---

## Slice 4 — Source Fingerprinting ergänzen

### Ziel

Einen stabilen `sourceFingerprint` und Datei-Fingerprints erzeugen.

### Neue Klasse

```text
src/main/java/de/burger/forensics/application/service/SourceFingerprintService.java
```

Alternativ, falls bereits ein passender Application-Service existiert, diesen erweitern.

### Verhalten

Der Service scannt Java-Dateien unter `sourceRoot` und erzeugt:

```text
List<SourceFileSnapshot>
SourceFingerprint
```

Regeln:

```text
1. Nur reguläre `.java` Dateien berücksichtigen.
2. Pfade relativ zum sourceRoot speichern.
3. Pfadseparatoren auf `/` normalisieren.
4. Dateien deterministisch nach relativem Pfad sortieren.
5. Pro Datei SHA-256 über Dateiinhalt berechnen.
6. Gesamt-Fingerprint aus sortierten Paaren relativePath + sha256 berechnen.
```

### Akzeptanzkriterien

* Gleicher Source-Stand erzeugt gleichen Fingerprint.
* Geänderte Datei erzeugt anderen Fingerprint.
* Unterschiedliche Traversal-Reihenfolge ändert Fingerprint nicht.
* Keine Gradle-Abhängigkeit im Service.

### Tests

```text
src/test/java/de/burger/forensics/application/service/SourceFingerprintServiceTest.java
```

Testfälle:

```text
- stable fingerprint for unchanged files
- changed content changes fingerprint
- unrelated file extension is ignored
- relative paths use `/`
```

---

## Slice 5 — RuleGenerationResult um Domain Rules erweitern

### Ziel

Die generierten Domain-Regeln verfügbar machen, damit sie zusammen mit den gerenderten BTM-Regeln gespeichert werden können.

### Änderung

Aktuell:

```java
public record RuleGenerationResult(List<String> renderedRules, AnalysisContext context) {
}
```

Ziel:

```java
public record RuleGenerationResult(
        List<Rule> rules,
        List<String> renderedRules,
        AnalysisContext context
) {
}
```

### Anpassung in `GenerateRulesUseCase`

Der Use Case erzeugt bereits intern:

```text
List<Rule> rules
List<Rule> filtered
List<String> rendered
```

Der Result soll die tatsächlich für die Ausgabe relevanten Regeln enthalten.

Empfehlung:

```text
RuleGenerationResult(filtered, rendered, context)
```

### Wichtig

Die gerenderte BTM-Datei darf sich dadurch nicht fachlich ändern.

### Tests anpassen

Vorhandene Tests für `GenerateRulesUseCase` und `GenerateBtmTask` anpassen.

Neue/erweiterte Testfälle:

```text
- result contains rendered rules
- result contains domain rules
- rendered rule count remains compatible
- existing BTM generation behavior remains stable
```

### Akzeptanzkriterien

* Bestehender API-Bruch ist im Projekt vollständig angepasst.
* Keine Änderung an RuleIdFactory ohne zwingenden Grund.
* BTM-Output bleibt stabil.

---

## Slice 6 — GenerateBtmTask mit Analysis Store verbinden

### Ziel

`GenerateBtmTask` orchestriert künftig zusätzlich den Analysis Store.

Der Task bleibt Inbound-Adapter und verdrahtet:

```text
JavaParserScanner
GenerateRulesUseCase
BytemanRuleRenderAdapter
H2AnalysisStoreAdapter
SourceFingerprintService
BtmFileWriter
ManifestWriter
ChecksumWriter
```

### Erweiterungen in `BtmGenExtension`

Neue Properties:

```java
private final Property<Boolean> analysisStoreEnabled;
private final Property<File> analysisStoreDirectory;
private final Property<String> cleanupPolicy;
private final Property<String> projectKey;
private final Property<File> manifestFile;
private final Property<File> checksumsFile;
```

Empfohlene Defaults:

```text
analysisStoreEnabled = true
analysisStoreDirectory = build/forensics/analysis-store
cleanupPolicy = KEEP_ON_SUCCESS
projectKey = project.name
manifestFile = build/forensics/manifest.json
checksumsFile = build/forensics/checksums.sha256
```

Da `BtmGenExtension` keinen direkten Zugriff auf `Project` haben muss, kann `projectKey` im Task über `getProject().getName()` finalisiert werden, wenn die Extension keinen Wert liefert.

### Erweiterungen in `GenerateBtmTask`

Neue Gradle Properties:

```text
@Input Property<Boolean> analysisStoreEnabled
@OutputDirectory DirectoryProperty analysisStoreDirectory
@Input Property<String> cleanupPolicy
@Input Property<String> projectKey
@OutputFile RegularFileProperty manifestFile
@OutputFile RegularFileProperty checksumsFile
```

### Ablauf im Task

```text
1. sourceRoot und outputFile auflösen.
2. Source-Dateien fingerprinten.
3. AnalysisRunId erzeugen.
4. BuildIdentity erzeugen.
5. H2 Store initialisieren.
6. analysis_run mit Status CREATED speichern.
7. Status SCANNING setzen.
8. GenerateRulesUseCase ausführen.
9. ScanEvents, Methoden, Rules speichern.
10. BTM-Datei schreiben.
11. Status BTM_GENERATED setzen.
12. Checksums berechnen.
13. Manifest schreiben.
14. Status COMPLETED setzen.
15. Cleanup-Policy anwenden.
```

Bei Fehler:

```text
1. Fehler loggen.
2. analysis_run soweit möglich auf FAILED setzen.
3. Cleanup-Policy anwenden.
4. Build weiterhin fehlschlagen lassen.
```

### Akzeptanzkriterien

* `generateBtmRules` schreibt weiterhin die BTM-Datei.
* Analysis Store wird standardmäßig erzeugt.
* Bei Fehlern wird der Build nicht verschluckt.
* H2 Store wird sauber geschlossen.
* `analysisStoreEnabled=false` stellt möglichst nahe am bisherigen Verhalten wieder her.

---

## Slice 7 — BTM Header mit BuildIdentity ergänzen

### Ziel

Die erzeugte BTM-Datei muss erkennbar zu einem Analysis Run gehören.

### Neue Klasse

```text
src/main/java/de/burger/forensics/plugin/btmgen/render/BtmHeaderRenderer.java
```

### Header-Format

Am Anfang der BTM-Datei soll ein Kommentarblock stehen:

```text
# Forensics Analysis
# schemaVersion: 1
# projectKey: <project-key>
# analysisRunId: <analysis-run-id>
# buildId: <build-id>
# sourceFingerprint: <source-fingerprint>
# btmRulesFingerprint: <btm-rules-fingerprint>
# pluginVersion: <plugin-version>
```

### Wichtig

* Der Header darf Byteman nicht ungültig machen.
* Keine Änderung an den eigentlichen Rule Bodies.
* Rule IDs bleiben stabil.

### Akzeptanzkriterien

* BTM-Datei enthält Header.
* Vorhandene Rule-Tests bleiben gültig oder werden gezielt um Header-Erwartung erweitert.
* Header enthält dieselbe `analysisRunId` wie H2 und Manifest.

---

## Slice 8 — Manifest und Checksums schreiben

### Ziel

Ein maschinenlesbares Manifest und eine Checksum-Datei erzeugen.

### Neue Packages

```text
src/main/java/de/burger/forensics/adapters/filesystem/
```

### Neue Klassen

```text
AnalysisManifestWriter.java
ChecksumFileWriter.java
ArtifactChecksumService.java
```

### manifest.json Inhalt

Minimal:

```json
{
  "schemaVersion": "1",
  "projectKey": "example-project",
  "analysisRunId": "...",
  "buildId": "...",
  "sourceFingerprint": "sha256:...",
  "btmRulesFingerprint": "sha256:...",
  "pluginVersion": "0.0.2-SNAPSHOT",
  "createdAt": "2026-05-09T00:00:00Z",
  "artifacts": [
    {
      "path": "forensics.btm",
      "type": "byteman-rules",
      "sha256": "...",
      "sizeBytes": 1234
    },
    {
      "path": "analysis-store",
      "type": "h2-analysis-store",
      "sha256": "...",
      "sizeBytes": 1234
    }
  ]
}
```

### checksums.sha256 Format

```text
<sha256>  forensics.btm
<sha256>  manifest.json
<sha256>  analysis-store/<file>
```

### Hinweise

* Keine JSON-Bibliothek erzwingen, wenn das Projekt keine haben möchte.
* Falls JSON manuell geschrieben wird, eigenes Escaping sauber testen.
* Checksums erst berechnen, nachdem H2-Verbindungen geschlossen wurden.

### Tests

```text
src/test/java/de/burger/forensics/adapters/filesystem/AnalysisManifestWriterTest.java
src/test/java/de/burger/forensics/adapters/filesystem/ChecksumFileWriterTest.java
src/test/java/de/burger/forensics/adapters/filesystem/ArtifactChecksumServiceTest.java
```

### Akzeptanzkriterien

* Manifest ist valides JSON.
* Checksums sind deterministisch.
* Manifest und H2 enthalten dieselbe `analysisRunId`.
* Artifact Checksums werden auch in H2 gespeichert.

---

## Slice 9 — Cleanup-Policy implementieren

### Ziel

Steuern, ob der Analysis Store nach dem Lauf erhalten bleibt oder gelöscht wird.

### Verhalten

```text
KEEP_ON_SUCCESS
  Erfolg: Store bleibt erhalten
  Fehler: Store kann erhalten bleiben, wenn Debugging sinnvoll ist

DELETE_ON_SUCCESS
  Erfolg: Store wird gelöscht
  Fehler: Store bleibt erhalten

KEEP_ON_FAILURE
  Erfolg: Store wird gelöscht
  Fehler: Store bleibt erhalten

KEEP_ALWAYS
  Erfolg: Store bleibt erhalten
  Fehler: Store bleibt erhalten
```

### Empfehlung für Entwicklung

```text
KEEP_ON_SUCCESS
```

### Zusätzlicher Task

Optional, aber empfohlen:

```text
cleanForensicsAnalysisStore
```

Dieser Task löscht:

```text
build/forensics/analysis-store
build/forensics/manifest.json
build/forensics/checksums.sha256
```

Er soll nicht automatisch an `clean` gehängt werden, außer es ist bereits Projektkonvention.

### Akzeptanzkriterien

* Cleanup-Policy ist per Extension konfigurierbar.
* Erfolg und Fehler werden unterschiedlich behandelt.
* Keine versehentliche Löschung vor Manifest-/Checksum-Erstellung.

---

## Slice 10 — Gradle Plugin Wiring finalisieren

### Ziel

Die neuen Extension-Werte sauber in `BtmGenPlugin` und `GenerateBtmTask` verdrahten.

### Aufgaben

1. `BtmGenExtension` erweitern.
2. Defaults in Extension und Task konsistent setzen.
3. `BtmGenPlugin` setzt Task-Konventionen.
4. `generateBtmRules` bleibt der Haupttask.
5. Optional `cleanForensicsAnalysisStore` registrieren.

### Beispielkonfiguration für Nutzer

```kotlin
btmGen {
    sourceRoot.set(file("src/main/java"))
    outputFile.set(file("build/forensics/forensics.btm"))

    analysisStoreEnabled.set(true)
    analysisStoreDirectory.set(file("build/forensics/analysis-store"))
    cleanupPolicy.set("KEEP_ON_SUCCESS")
    projectKey.set("legacy-demo-shop")
    manifestFile.set(file("build/forensics/manifest.json"))
    checksumsFile.set(file("build/forensics/checksums.sha256"))
}
```

### Akzeptanzkriterien

* Gradle Configuration Cache wird nicht unnötig verschlechtert.
* Keine eager Project-Auswertung, wo Provider möglich sind.
* Task-Inputs und Outputs sind annotiert.
* `./gradlew tasks --group forensics` zeigt sinnvolle Task-Beschreibungen.

---

## Slice 11 — ArchUnit und Architekturtests absichern

### Ziel

Sicherstellen, dass H2 und Dateisystemadapter nicht in Domain/Application einsickern.

### Prüfungen

Bestehende ArchUnit-Regeln prüfen und bei Bedarf gezielt erweitern.

Neue Regeln, falls sinnvoll:

```text
- domain must not depend on java.sql
- domain must not depend on org.h2
- application must not depend on org.h2
- application must not depend on Gradle APIs
- h2 adapter may implement AnalysisStorePort
```

### Akzeptanzkriterien

* H2 bleibt im Adapter.
* Gradle bleibt im Plugin-Inbound-Adapter.
* Domain bleibt frameworkfrei.
* Application bleibt adapterfrei.

---

## Slice 12 — Integrationstest mit Gradle TestKit

### Ziel

Nachweisen, dass ein echtes Testprojekt mit dem Plugin die neuen Artefakte erzeugt.

### Test erweitern oder neu anlegen

```text
src/test/java/de/burger/forensics/plugin/btmgen/gradle/GenerateBtmTaskAnalysisStoreTest.java
```

### Test-Szenario

Temporäres Gradle-Projekt erzeugen mit:

```text
settings.gradle.kts
build.gradle.kts
src/main/java/com/example/DemoService.java
```

Dann ausführen:

```bash
./gradlew generateBtmRules
```

### Erwartete Dateien

```text
build/forensics/forensics.btm
build/forensics/manifest.json
build/forensics/checksums.sha256
build/forensics/analysis-store/<h2 files>
```

### Erwartete Inhalte

```text
- BTM enthält Header mit analysisRunId.
- Manifest enthält dieselbe analysisRunId.
- Checksums enthalten forensics.btm.
- H2 Store enthält mindestens einen analysis_run.
- H2 Store enthält ScanEvents.
- H2 Store enthält BTM Rules.
```

### Akzeptanzkriterien

* Test läuft reproduzierbar.
* Kein echter externer Server nötig.
* Keine Joern-Installation nötig.

---

## Slice 13 — Dokumentation aktualisieren

### Ziel

README/QUALITY nur minimal und korrekt aktualisieren.

### README Ergänzung

Kurzer Abschnitt:

```text
Forensics Analysis Store
```

Inhalt:

```text
- Was erzeugt generateBtmRules zusätzlich?
- Wo liegen H2 Store, Manifest und Checksums?
- Wie wird Cleanup gesteuert?
- Hinweis: Joern/gRPC/Server-Push folgen später.
```

### Keine Änderungen

Nicht ändern:

```text
- SonarCloud Badge entfernen
- Coverage-Schwellen senken
- Release-Konfiguration ändern
- Java-Toolchain ändern
```

### Akzeptanzkriterien

* README beschreibt neue Artefakte.
* Keine falsche Aussage zu Joern oder gRPC.
* Keine Behauptung, dass der forensic_analytics Server bereits existiert.

---

# 9. Empfohlene interne Ablaufreihenfolge im Code

Der finale Ablauf in `GenerateBtmTask` soll ungefähr so aussehen:

```text
resolve configuration
create output directories
create analysis run id
calculate source fingerprints
create initial build identity
open analysis store
initialize schema
create analysis run
mark status SCANNING
run GenerateRulesUseCase
store source files
store scan events
store methods
store btm rules
write btm file
calculate btm rules fingerprint
write manifest
write checksums
store artifact checksums
mark status COMPLETED
close analysis store
apply cleanup policy
```

Bei Fehler:

```text
mark status FAILED if store is available
close store
apply cleanup policy for failure
rethrow exception
```

---

# 10. Tabellen-Mapping

## analysis_run

Quelle:

```text
BuildIdentity
AnalysisRunStatus
```

Zweck:

```text
Eindeutiger statischer Analyse-Snapshot.
```

## source_file

Quelle:

```text
SourceFingerprintService
```

Zweck:

```text
Späterer Abgleich zwischen Analysepaket und Runtime-Artefakt.
```

## scan_method

Quelle:

```text
AnalysisContext.methodContexts
```

Zweck:

```text
Methodenanker für ScanEvents, BTM-Regeln und später Joern.
```

## scan_event

Quelle:

```text
AnalysisContext.events
```

Zweck:

```text
Rohdaten aus JavaParserScanner.
```

## btm_rule

Quelle:

```text
RuleGenerationResult.rules
RuleGenerationResult.renderedRules
```

Zweck:

```text
Verbindung zwischen statischer Scan-Information und erzeugter Byteman-Regel.
```

## artifact_checksum

Quelle:

```text
ArtifactChecksumService
```

Zweck:

```text
Integritätsprüfung für späteren Upload und Server-Import.
```

---

# 11. Done Definition für den Gesamtworkflow

Der Workflow ist abgeschlossen, wenn folgende Punkte erfüllt sind:

```text
[ ] ./gradlew clean test läuft erfolgreich.
[ ] ./gradlew check läuft erfolgreich.
[ ] ./gradlew validatePlugins läuft erfolgreich.
[ ] generateBtmRules erzeugt weiterhin forensics.btm.
[ ] generateBtmRules erzeugt manifest.json.
[ ] generateBtmRules erzeugt checksums.sha256.
[ ] generateBtmRules erzeugt einen H2 Analysis Store.
[ ] H2 enthält analysis_run.
[ ] H2 enthält source_file Einträge.
[ ] H2 enthält scan_event Einträge.
[ ] H2 enthält btm_rule Einträge.
[ ] BTM Header enthält analysisRunId.
[ ] Manifest enthält dieselbe analysisRunId.
[ ] Checksums enthalten BTM, Manifest und H2-Dateien.
[ ] Cleanup-Policy ist konfigurierbar.
[ ] analysisStoreEnabled=false ist getestet.
[ ] Domain enthält keine H2-/Gradle-/Dateisystemabhängigkeiten.
[ ] Application enthält keine H2-/Gradle-Abhängigkeiten.
[ ] README ist minimal aktualisiert.
```

---

# 12. Tests, die mindestens vorhanden sein müssen

```text
AnalysisRunIdTest
BuildIdentityTest
ArtifactChecksumTest
SourceFingerprintServiceTest
H2SchemaInitializerTest
H2AnalysisStoreAdapterTest
AnalysisManifestWriterTest
ChecksumFileWriterTest
GenerateBtmTaskAnalysisStoreTest
```

Bestehende Tests müssen angepasst, aber nicht abgeschwächt werden.

---

# 13. Qualitätsgate

Nach jedem größeren Slice:

```bash
./gradlew test
```

Nach Abschluss des Workflows:

```bash
./gradlew clean test
./gradlew check
./gradlew validatePlugins
```

Falls Dependency Verification im Projekt aktiv ist, müssen neue H2-Artefakte sauber in die Verification-Metadaten aufgenommen werden. Keine unsicheren Workarounds verwenden.

---

# 14. Erwartete Commit-Struktur

Empfohlene Commit-Aufteilung:

```text
1. feat: add analysis identity domain model
2. feat: add analysis store port
3. feat: add h2 analysis store adapter
4. feat: add source fingerprinting
5. feat: expose generated domain rules
6. feat: persist btm analysis data
7. feat: write analysis manifest and checksums
8. feat: add analysis store cleanup policy
9. test: add analysis store integration coverage
10. docs: document forensics analysis store artifacts
```

---

# 15. Erwarteter Endzustand

Nach diesem Workflow ist das Plugin noch kein Server-Client und noch kein Joern-Analyzer.

Es ist aber bereit für den nächsten Evolutionsschritt:

```text
Joern Enrichment
  -> joern_node
  -> joern_edge
  -> semantic_anchor
```

Danach:

```text
gRPC Publish
  -> AnalysisIngestService
  -> streaming upload of analysis package
```

Danach:

```text
Runtime Trace Identity
  -> TraceEvent carries BuildIdentity
  -> forensic_analytics correlates live events with static snapshot
```

Dieser Workflow schafft damit den notwendigen stabilen Rohdatenkern für die spätere Forensics Analytics Platform.
