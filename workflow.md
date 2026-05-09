# workflow.md — Persistent Analysis Store und Joern Semantic Enrichment

## 1. Ziel dieses Workflows

Dieser Workflow erweitert das bestehende `forensics_tracing` Gradle-Plugin in zwei sauber getrennten Phasen:

```text
Phase 1: Persistent Forensics Analysis Store
Phase 2: Joern Semantic Enrichment Flow
```

Das Plugin soll nach erfolgreichem Scan- und BTM-Generierungslauf nicht nur eine `.btm`-Datei erzeugen, sondern einen vollständigen, versionierten statischen Rohdaten-Snapshot bereitstellen. Dieser Snapshot wird anschließend optional durch Joern-Semantikdaten angereichert.

Der spätere gRPC-Push, Runtime-Trace-Stream, Replay-Kontext und LLM-Kontext sind weiterhin nicht Bestandteil dieses Workflows. Dieser Workflow schafft aber die notwendigen Datenstrukturen, IDs und Artefakte, damit diese Schritte später sauber aufsetzen können.

## 2. Architekturentscheidung

Die bisher temporäre H2-Scan-Datenbank wird zu einem persistenten, versionierten Forensics Analysis Store erweitert.

Dieser Store enthält zunächst die statischen Rohdaten aus dem JavaParser-basierten Scan und der BTM-Generierung:

```text
analysis_run
source_file
scan_method
scan_event
btm_rule
artifact_checksum
```

Anschließend wird dieser Store optional mit Joern-Daten angereichert:

```text
joern_import_run
joern_node
joern_edge
joern_method
joern_call_relation
joern_control_flow_relation
joern_data_flow_path
joern_data_flow_step
semantic_anchor
```

Alle Daten werden über eine gemeinsame `BuildIdentity` verbunden.

```text
Gradle Plugin
BTM-Regeln
Joern-Semantikdaten
Runtime-Trace-Events
forensic_analytics Server
```

Wichtig:

```text
Joern wird als externer Outbound-Adapter angebunden.
Joern wird nicht als direkte Plugin-Library-Abhängigkeit eingebaut.
gRPC wird in diesem Workflow nicht eingebaut.
Replay / LLM / Server-Import werden in diesem Workflow nicht eingebaut.
```

## 3. Technische Leitplanken

* Gradle 9.1 verwenden.
* Die im Repository konfigurierte Java-Toolchain nicht ändern.
* Joern darf eigene externe Runtime-Anforderungen haben, darf aber die Plugin-Toolchain nicht erzwingen.
* Keine Änderung an der fachlichen BTM-Regel-Semantik.
* Keine Coverage-Schwellen senken.
* Keine bestehenden Tests entfernen oder deaktivieren.
* Joern nicht als direkte `implementation`-Dependency des Plugins einbinden.
* Joern über CLI, Prozessadapter oder später Containeradapter ausführen.
* Keine gRPC-Abhängigkeiten hinzufügen.
* Keine Server-Kommunikation implementieren.
* Keine Runtime-Replay-Logik implementieren.
* Source-Code-Kommentare ausschließlich auf Englisch schreiben.
* Hexagonale Architektur einhalten.
* Domain und Application dürfen nicht von H2, Gradle, Joern CLI, Dateisystemdetails oder Plugin-Klassen abhängen.

## 4. Zielartefakte

Standardlauf ohne Joern:

```text
build/forensics/
├── forensics.btm
├── manifest.json
├── checksums.sha256
└── analysis-store/
    └── <h2 database files>
```

Lauf mit Joern:

```text
build/forensics/
├── forensics.btm
├── manifest.json
├── checksums.sha256
├── analysis-store/
│   └── <h2 database files>
└── joern/
    ├── cpg.bin
    ├── callgraph.json
    ├── controlflow.json
    ├── dataflow.json
    └── slices.json
```

## 5. Nicht-Ziele

Nicht umsetzen:

```text
gRPC Publisher
forensic_analytics Server API
TraceIngestService
Runtime Event Streaming
Replay Engine
LLM Prompt Builder
Graph DB Export
Vector Store Export
automatische Fehlerkorrektur
automatischer Deployment-Flow
```

## 6. STOP-Regeln für Codex

Codex muss stoppen und berichten, wenn eine dieser Situationen eintritt:

```text
1. Es existiert bereits eine H2-Store-Implementierung mit anderem Schema.
2. Es existiert bereits ein Analysis-Run-Konzept mit inkompatiblen IDs.
3. Es existiert bereits ein Joern-Adapter oder SemanticAnalysisPort mit anderer Architektur.
4. Die vorhandene GenerateRulesUseCase-Struktur wurde im Checkout wesentlich verändert.
5. Das Projekt verwendet bereits einen anderen Datenbank-Migrationsmechanismus.
6. Die Java-Toolchain müsste geändert werden, um Joern direkt einzubinden.
7. Die BTM-Ausgabe würde sich fachlich ändern.
8. Bestehende ArchUnit-Regeln würden nur durch Aufweichen erfüllbar.
9. Neue Abhängigkeiten würden Dependency Verification blockieren und keine saubere Metadatenpflege möglich sein.
10. Joern ist lokal nicht installiert und ein echter Integrationstest würde dadurch instabil werden.
```

Keine stillen Architekturannahmen treffen.

# 7. Slices

## Slice 0 — Preflight und Ist-Zustand sichern

### Ziel

Vor Änderungen den aktuellen Zustand erfassen und sicherstellen, dass der Workflow auf dem echten Projektstand aufsetzt.

### Befehle

```bash
git status --short
find src/main/java -type f | sort
find src/test/java -type f | sort
rg -n "H2|AnalysisStore|AnalysisRun|BuildIdentity|Joern|SemanticAnalysis|CPG|Code Property Graph" src/main src/test build.gradle.kts gradle || true
./gradlew clean test
./gradlew check
./gradlew validatePlugins
```

### Akzeptanzkriterien

* Ausgangszustand ist bekannt.
* Bestehende Tests laufen oder bestehende Fehler sind dokumentiert.
* Keine Codeänderung in diesem Slice.

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
SourceFileSnapshot.java
```

### BuildIdentity

`BuildIdentity` enthält mindestens:

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

Später ergänzbar:

```text
joernFingerprint
joernVersion
analysisPackageFingerprint
```

### Akzeptanzkriterien

* Domain-Modelle enthalten keine H2-, Gradle-, Joern-, CLI- oder Dateisystemabhängigkeiten.
* Ungültige IDs werden abgelehnt.
* Tests decken Blank-/Null-Fälle ab.

## Slice 2 — AnalysisStorePort definieren

### Ziel

Eine saubere Port-Schnittstelle für das Speichern der Analyse-Rohdaten schaffen.

### Neue Datei

```text
src/main/java/de/burger/forensics/domain/port/out/AnalysisStorePort.java
```

### Verantwortung

Der Port speichert zunächst:

```text
analysis_run
source_file
scan_method
scan_event
btm_rule
artifact_checksum
```

Später wird derselbe Port oder ein spezialisierter `SemanticAnalysisStorePort` für Joern-Daten erweitert.

### Akzeptanzkriterien

* `AnalysisStorePort` liegt im Domain-Port-Package.
* Keine H2- oder SQL-Klassen im Port.
* Keine Gradle-Typen im Port.
* Keine Joern-Typen im Port.

## Slice 3 — H2 Analysis Store Adapter implementieren

### Ziel

Den Outbound-Adapter für H2 erstellen, ohne die Domain mit H2 zu koppeln.

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

### Basisschema

Minimal benötigte Tabellen:

```text
analysis_run
source_file
scan_method
scan_event
btm_rule
artifact_checksum
```

### Akzeptanzkriterien

* Schema-Initialisierung ist idempotent.
* SQL bleibt im H2-Adapter.
* H2-Verbindungen werden sauber geschlossen.
* Tests laufen mit temporärem Verzeichnis.

## Slice 4 — Source Fingerprinting ergänzen

### Ziel

Einen stabilen `sourceFingerprint` und Datei-Fingerprints erzeugen.

### Neue Klasse

```text
src/main/java/de/burger/forensics/application/service/SourceFingerprintService.java
```

### Regeln

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
* Keine Gradle- oder Joern-Abhängigkeit im Service.

## Slice 5 — RuleGenerationResult um Domain Rules erweitern

### Ziel

Die generierten Domain-Regeln verfügbar machen, damit sie zusammen mit den gerenderten BTM-Regeln gespeichert und später mit Joern-Knoten korreliert werden können.

### Änderung

Ziel:

```java
public record RuleGenerationResult(
        List<Rule> rules,
        List<String> renderedRules,
        AnalysisContext context
) {
}
```

### Akzeptanzkriterien

* Die gerenderte BTM-Datei ändert sich fachlich nicht.
* Die tatsächlich ausgegebenen Rules sind persistierbar.
* Rule IDs bleiben stabil.

## Slice 6 — GenerateBtmTask mit Analysis Store verbinden

### Ziel

`GenerateBtmTask` orchestriert künftig zusätzlich den Analysis Store.

### Ablauf

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

### Akzeptanzkriterien

* `generateBtmRules` schreibt weiterhin die BTM-Datei.
* Analysis Store wird standardmäßig erzeugt.
* H2 Store wird sauber geschlossen.
* `analysisStoreEnabled=false` stellt möglichst nahe am bisherigen Verhalten wieder her.

## Slice 7 — BTM Header mit BuildIdentity ergänzen

### Ziel

Die erzeugte BTM-Datei muss eindeutig zu einem Analysis Run gehören.

### Header-Format

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

### Akzeptanzkriterien

* Header ist Byteman-kompatibel.
* Rule Bodies bleiben fachlich unverändert.
* Header, Manifest und H2 enthalten dieselbe `analysisRunId`.

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

### Manifest-Inhalt

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
  "joernEnabled": false,
  "createdAt": "2026-05-09T00:00:00Z",
  "artifacts": []
}
```

### Akzeptanzkriterien

* Manifest ist valides JSON.
* Checksums sind deterministisch.
* Manifest und H2 enthalten dieselbe `analysisRunId`.
* Artifact Checksums werden auch in H2 gespeichert.

## Slice 9 — Cleanup-Policy implementieren

### Ziel

Steuern, ob der Analysis Store nach dem Lauf erhalten bleibt oder gelöscht wird.

### Policies

```text
DELETE_ON_SUCCESS
KEEP_ON_SUCCESS
KEEP_ON_FAILURE
KEEP_ALWAYS
```

### Akzeptanzkriterien

* Cleanup-Policy ist per Extension konfigurierbar.
* Erfolg und Fehler werden unterschiedlich behandelt.
* Keine versehentliche Löschung vor Manifest-/Checksum-Erstellung.

## Slice 10 — SemanticAnalysisPort einführen

### Ziel

Eine domänennahe Schnittstelle für externe semantische Codeanalyse schaffen, ohne Joern direkt in Domain oder Application zu koppeln.

### Neue Datei

```text
src/main/java/de/burger/forensics/domain/port/out/SemanticAnalysisPort.java
```

### Neue Domain-Modelle

```text
src/main/java/de/burger/forensics/domain/model/semantic/SemanticAnalysisRequest.java
src/main/java/de/burger/forensics/domain/model/semantic/SemanticAnalysisResult.java
src/main/java/de/burger/forensics/domain/model/semantic/SemanticMethod.java
src/main/java/de/burger/forensics/domain/model/semantic/SemanticNode.java
src/main/java/de/burger/forensics/domain/model/semantic/SemanticEdge.java
src/main/java/de/burger/forensics/domain/model/semantic/CallRelation.java
src/main/java/de/burger/forensics/domain/model/semantic/ControlFlowRelation.java
src/main/java/de/burger/forensics/domain/model/semantic/DataFlowPath.java
src/main/java/de/burger/forensics/domain/model/semantic/DataFlowStep.java
```

### Grundsatz

Die Domain-Modelle dürfen keine Joern-Klassen, Joern-Package-Namen oder CLI-spezifische Details enthalten.

### Akzeptanzkriterien

* `SemanticAnalysisPort` liegt im Domain-Port-Package.
* Joern bleibt austauschbarer Provider.
* Tests decken Null-/Blank-Fälle der semantischen Modelle ab.

## Slice 11 — Joern-Konfiguration in der Gradle Extension ergänzen

### Ziel

Joern optional und explizit konfigurierbar machen.

### Neue Extension Properties

```text
joernEnabled
joernExecutable
joernParseExecutable
joernSliceExecutable
joernWorkspaceDirectory
joernOutputDirectory
joernMaxHeap
joernTimeoutSeconds
joernFailOnError
```

### Defaults

```text
joernEnabled = false
joernWorkspaceDirectory = build/forensics/joern/workspace
joernOutputDirectory = build/forensics/joern
joernFailOnError = true
```

### Beispielkonfiguration

```kotlin
btmGen {
    sourceRoot.set(file("src/main/java"))
    outputFile.set(file("build/forensics/forensics.btm"))

    analysisStoreEnabled.set(true)
    analysisStoreDirectory.set(file("build/forensics/analysis-store"))

    joernEnabled.set(true)
    joernExecutable.set(file("/opt/joern/joern"))
    joernParseExecutable.set(file("/opt/joern/joern-parse"))
    joernSliceExecutable.set(file("/opt/joern/joern-slice"))
    joernOutputDirectory.set(file("build/forensics/joern"))
    joernFailOnError.set(true)
}
```

### Akzeptanzkriterien

* Joern ist standardmäßig deaktiviert.
* Der bestehende `generateBtmRules`-Lauf bleibt ohne Joern stabil.
* Keine Joern-Installation ist für normale Tests erforderlich.

## Slice 12 — Joern CLI Outbound Adapter implementieren

### Ziel

Joern extern ausführen und Joern-Artefakte erzeugen.

### Neue Packages

```text
src/main/java/de/burger/forensics/adapters/joern/
```

### Neue Klassen

```text
JoernCliSemanticAnalysisAdapter.java
JoernCommandExecutor.java
JoernCommandResult.java
JoernOutputParser.java
JoernAnalysisException.java
JoernAnalysisConfig.java
JoernArtifactPaths.java
```

### Verhalten

Der Adapter führt Joern als externen Prozess aus:

```text
joern-parse -> cpg.bin
joern / joern script -> callgraph.json
joern / joern script -> controlflow.json
joern-slice -> dataflow.json / slices.json
```

### Akzeptanzkriterien

* Joern wird nicht als direkte Java-Library eingebunden.
* Prozessausführung ist kapselbar und testbar.
* Timeouts werden respektiert.
* stdout/stderr werden diagnostisch erhalten.
* Unit-Tests verwenden FakeJoernCommandExecutor und JSON-Fixtures.

## Slice 13 — Joern H2-Schema erweitern

### Ziel

Joern-Rohdaten und Korrelationsinformationen im Analysis Store speichern.

### Neue Tabellen

```text
joern_import_run
joern_node
joern_edge
joern_method
joern_call_relation
joern_control_flow_relation
joern_data_flow_path
joern_data_flow_step
semantic_anchor
```

### Semantik

`semantic_anchor` verbindet die bestehenden Rohdaten mit Joern:

```text
scan_event -> joern_node
btm_rule   -> scan_event -> joern_node
```

### Matching-Felder

Die Korrelation darf nicht nur über Zeilennummer erfolgen.

```text
relativePath
fqcn
methodName
signature
lineNumber
normalizedCode
```

### Confidence

```text
FQCN_METHOD_LINE_CODE = 0.95
FILE_METHOD_LINE      = 0.80
METHOD_LINE           = 0.65
LINE_ONLY             = 0.40
```

### Akzeptanzkriterien

* Joern-Tabellen sind über `analysis_run_id` versioniert.
* Import ist idempotent pro `analysisRunId` und `joernFingerprint`.
* `semantic_anchor` speichert `confidence` und `match_strategy`.

## Slice 14 — Joern Import Use Case ergänzen

### Ziel

Den Joern-Fluss als eigenen Use Case modellieren.

### Neue Application-Klasse

```text
src/main/java/de/burger/forensics/application/service/AnalyzeSemanticsUseCase.java
```

### Ablauf

```text
1. BuildIdentity und AnalysisRunId laden oder entgegennehmen.
2. SemanticAnalysisRequest erstellen.
3. SemanticAnalysisPort.analyze(...) ausführen.
4. Joern-Artefakte prüfen.
5. Joern-Ergebnis in H2 speichern.
6. semantic_anchor Matching ausführen.
7. Manifest und Checksums aktualisieren.
```

### Akzeptanzkriterien

* Use Case kennt nur Ports und Domain-Modelle.
* Keine Joern-CLI-Klassen im Application Layer.
* Kein Gradle API Import im Application Layer.

## Slice 15 — Gradle Tasks für Joern-Fluss ergänzen

### Ziel

Joern als expliziten, steuerbaren Build-Schritt verfügbar machen.

### Neue Tasks

```text
analyzeForensicsSemantics
importForensicsSemantics
forensicsAnalyze
```

### Task-Beziehungen

```text
forensicsAnalyze
  dependsOn generateBtmRules
  dependsOn analyzeForensicsSemantics
  dependsOn importForensicsSemantics
```

Bevorzugt ist ein expliziter Aggregat-Task. Joern soll nicht ungefragt bei jedem normalen Build laufen.

### Akzeptanzkriterien

* Joern läuft nicht ungefragt bei jedem normalen Build.
* `analyzeForensicsSemantics` benötigt `joernEnabled=true` oder bricht mit klarer Meldung ab.
* `forensicsAnalyze` erzeugt BTM + Analysis Store + Joern Enrichment.

## Slice 16 — Manifest um Joern-Artefakte erweitern

### Ziel

Das Manifest muss anzeigen, ob Joern verwendet wurde und welche Joern-Artefakte Bestandteil des Analyse-Snapshots sind.

### Manifest-Erweiterung

```json
{
  "joernEnabled": true,
  "joernVersion": "...",
  "joernFingerprint": "sha256:...",
  "joernArtifacts": [
    {
      "path": "joern/cpg.bin",
      "type": "joern-cpg",
      "sha256": "...",
      "sizeBytes": 1234
    },
    {
      "path": "joern/callgraph.json",
      "type": "joern-callgraph",
      "sha256": "...",
      "sizeBytes": 1234
    }
  ]
}
```

### Akzeptanzkriterien

* Manifest bleibt valides JSON.
* Joern-Artefakte stehen auch in `checksums.sha256`.
* Manifest, H2 und BTM Header bleiben über dieselbe `analysisRunId` verbunden.

## Slice 17 — Tests für Joern Flow

### Ziel

Joern-Integration testbar machen, ohne echte Joern-Installation für Unit- und Standard-Integrationstests vorauszusetzen.

### Tests

```text
SemanticAnalysisPortModelTest
JoernOutputParserTest
JoernCliSemanticAnalysisAdapterTest
H2JoernImportStoreTest
SemanticAnchorMatcherTest
AnalyzeSemanticsUseCaseTest
GenerateBtmTaskJoernDisabledTest
ForensicsAnalyzeTaskWithFakeJoernTest
```

### Teststrategie

```text
Unit Tests:
  FakeJoernCommandExecutor
  JSON Fixtures
  temporäre H2 DB

Gradle TestKit:
  joernEnabled=false als Standardfall
  joernEnabled=true mit Fake-Adapter oder Fixture-Modus

Optionaler lokaler Integrationstest:
  nur aktiv über explizites Flag, z. B. -PwithRealJoern=true
```

### Akzeptanzkriterien

* Standardtests benötigen keine echte Joern-Installation.
* Joern JSON Fixtures werden deterministisch importiert.
* `semantic_anchor` Matching wird nachvollziehbar getestet.

## Slice 18 — ArchUnit und Architekturtests erweitern

### Ziel

Absichern, dass Joern und H2 Adapter bleiben.

### Regeln

```text
- domain must not depend on java.sql
- domain must not depend on org.h2
- domain must not depend on Joern packages
- application must not depend on org.h2
- application must not depend on Gradle APIs
- application must not depend on Joern CLI adapter classes
- adapters.h2 may implement AnalysisStorePort
- adapters.joern may implement SemanticAnalysisPort
```

### Akzeptanzkriterien

* H2 bleibt im H2-Adapter.
* Joern bleibt im Joern-Adapter.
* Gradle bleibt im Plugin-Inbound-Adapter.
* Domain bleibt frameworkfrei.

## Slice 19 — Dokumentation aktualisieren

### Ziel

README/QUALITY minimal und korrekt aktualisieren.

### README-Ergänzungen

```text
Forensics Analysis Store
Joern Semantic Enrichment
Generated Artifacts
How to run without Joern
How to run with Joern
Cleanup Policy
```

### Nicht behaupten

```text
- dass gRPC bereits implementiert ist
- dass forensic_analytics bereits angebunden ist
- dass Replay oder LLM-Kontext bereits erzeugt werden
```

### Akzeptanzkriterien

* README beschreibt Joern als optionalen semantischen Anreicherungsfluss.
* Standardlauf ohne Joern bleibt dokumentiert.
* Joern-Lauf ist klar als optionaler Build-Schritt dokumentiert.

# 8. Interne Ablaufreihenfolge

## Standardlauf ohne Joern

```text
resolve configuration
create output directories
create analysis run id
calculate source fingerprints
create build identity
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

## Lauf mit Joern

```text
run standard analysis store flow
verify joernEnabled
verify joern executables or configured adapter
run joern-parse
run joern query/script exports
run joern-slice if configured
parse joern artifacts
store joern_import_run
store joern_node and joern_edge
store call/control/data flow relations
match scan_event to joern_node
store semantic_anchor
update manifest with joern artifacts
update checksums
mark semantic enrichment completed
```

Bei Fehler:

```text
mark analysis_run or joern_import_run as FAILED if store is available
close store
apply cleanup policy
rethrow exception unless joernFailOnError=false
```

# 9. Done Definition

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
[ ] Joern ist standardmäßig deaktiviert.
[ ] analyzeForensicsSemantics ist explizit ausführbar.
[ ] Bei joernEnabled=true werden Joern-Artefakte erzeugt oder Fake-Fixtures importiert.
[ ] H2 enthält joern_import_run.
[ ] H2 enthält joern_node und joern_edge.
[ ] H2 enthält semantic_anchor.
[ ] Manifest enthält Joern-Artefakte bei joernEnabled=true.
[ ] Checksums enthalten Joern-Artefakte bei joernEnabled=true.
[ ] Domain enthält keine H2-/Gradle-/Joern-Abhängigkeiten.
[ ] Application enthält keine H2-/Gradle-/Joern-Adapter-Abhängigkeiten.
[ ] README ist minimal aktualisiert.
```

# 10. Qualitätsgate

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

Falls Dependency Verification aktiv ist, müssen neue Artefakte sauber in die Verification-Metadaten aufgenommen werden. Keine unsicheren Workarounds verwenden.

# 11. Erwartete Commit-Struktur

Empfohlene Commit-Aufteilung:

```text
1. feat: add persistent analysis identity model
2. feat: add h2 analysis store foundation
3. feat: persist btm scan and rule data
4. feat: write analysis manifest and checksums
5. feat: add semantic analysis domain port
6. feat: add optional joern configuration
7. feat: add joern cli semantic adapter
8. feat: import joern semantic data into analysis store
9. feat: anchor scan events to joern nodes
10. test: add joern enrichment fixture coverage
11. docs: document analysis store and joern enrichment flow
```

# 12. Erwarteter Endzustand

Nach diesem Workflow ist das Plugin noch kein Server-Client und noch keine Replay-Plattform.

Es erzeugt aber einen stabilen statischen Analyse-Snapshot:

```text
JavaParser Scan
  -> H2 Analysis Store
  -> BTM Rules
  -> Manifest
  -> Checksums
```

Und optional einen semantisch angereicherten Snapshot:

```text
Joern CPG
  -> Joern JSON Artifacts
  -> H2 Joern Tables
  -> semantic_anchor
```

Damit ist die Basis für den nächsten Evolutionsschritt vorhanden:

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

Dieser Workflow schafft damit den notwendigen Rohdatenkern und den Joern-Semantikfluss für die spätere Forensics Analytics Platform.
