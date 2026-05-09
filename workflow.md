# Codex Workflow: PUML-/PlantUML-Generierung vollständig entfernen

## Ziel

Entferne die komplette Generierung von PUML-/PlantUML-Dateien aus dem Projekt `forensics_tracing`.

Nach Abschluss soll das Projekt weiterhin Byteman-Rule-Dateien (`.btm`) erzeugen, testen und veröffentlichen können. Die PUML-/PlantUML-Ausgabe darf nicht mehr über Gradle-Tasks, Extension-Properties, Ports, Adapter, Services, Writer, Tests, Beispiele oder Dokumentation erreichbar sein.

## Rolle für Codex

Du arbeitest als Senior Java/Gradle-Plugin-Entwickler mit Fokus auf hexagonale Architektur, Gradle-Plugin-Qualität, JUnit 5 und ArchUnit.

Arbeite konservativ, nachvollziehbar und testgetrieben. Entferne nur PUML-/PlantUML-bezogene Funktionalität. Die Byteman-BTM-Generierung, Runtime-Tracing-Hilfen und bestehende Architekturgrenzen dürfen nicht beschädigt werden.

## Harte Rahmenbedingungen

* Projekt-Toolchain nicht ändern.
* Gradle Wrapper verwenden, keine globale Gradle-Installation voraussetzen.
* Keine Java-Version hoch- oder heruntersetzen.
* Keine unnötigen Dependency-Upgrades.
* Keine Coverage-Grenzen senken.
* Keine Architekturregeln abschwächen.
* Keine toten Kompatibilitätsstubs behalten, wenn sie nur PUML-/PlantUML-Funktionalität simulieren.
* Source-Code und Source-Code-Kommentare müssen englisch sein.
* Dokumentation darf deutsch oder englisch sein, aber fachlich eindeutig.
* Wenn eine Klasse, Methode oder Konfiguration nicht eindeutig zugeordnet werden kann: nicht raten, sondern Befund dokumentieren und nur sichere Änderungen durchführen.

## Erwartetes Ergebnis

Am Ende muss gelten:

1. Es gibt keine PUML-/PlantUML-Generierung mehr.
2. Es gibt keine Gradle-Task mehr, die `.puml`, PlantUML oder Diagramm-Ausgaben erzeugt.
3. Es gibt keine Extension-Properties mehr, die PUML-/PlantUML-Ausgaben konfigurieren.
4. Es gibt keine produktiven Klassen mehr, deren alleinige Verantwortung PUML-/PlantUML-Rendering oder `.puml`-Dateischreibung ist.
5. Es gibt keine Tests mehr, die PUML-/PlantUML-Ausgabe erwarten.
6. Es gibt keine README-/Dokumentationsanleitung mehr, die PUML-/PlantUML-Generierung beschreibt.
7. Die Byteman-BTM-Generierung bleibt vollständig funktionsfähig.
8. Die Qualitätsprüfung läuft erfolgreich durch.

## Nicht-Ziele

Nicht entfernen:

* Byteman-Regelgenerierung
* AST-Scanning für Java-Quellen
* Branch-/Condition-Erkennung
* Runtime-Tracing-Helfer
* Logging-/Correlation-Unterstützung
* Generische Analysemodelle, falls sie nicht ausschließlich PUML-spezifisch sind
* Zukünftige Graph-/CPG-Konzepte, sofern sie noch nicht an PlantUML gekoppelt sind

Wenn ein Modell generisch ist, aber aktuell nur von PUML verwendet wird, triff eine Architekturentscheidung:

* Ist es wirklich Teil des Kernmodells? Dann behalten und von PUML-Begriffen entkoppeln.
* Ist es nur Ausgabe-/Rendering-Infrastruktur für PUML? Dann entfernen.

## Arbeitsweise

Arbeite in kleinen, prüfbaren Schritten:

1. Status erfassen.
2. PUML-/PlantUML-Stellen finden.
3. Architekturgrenze bestimmen.
4. Tests zuerst anpassen oder ergänzen.
5. Produktivcode entfernen.
6. Build-/Plugin-Konfiguration bereinigen.
7. Dokumentation bereinigen.
8. Qualitätssicherung ausführen.
9. Abschlussbericht erstellen.

Führe keine großflächigen Umbenennungen durch, wenn eine gezielte Entfernung reicht.

---

# Schritt 1: Repository-Status erfassen

Führe zuerst aus:

```bash
git status --short
./gradlew --version
find . -maxdepth 3 -type f | sort | sed 's#^./##' | head -200
```

Dokumentiere kurz:

* aktueller Branch
* uncommitted changes
* Gradle-Version aus dem Wrapper
* Java-Toolchain aus dem Projekt

Wenn bereits uncommitted Änderungen vorhanden sind, ändere sie nicht blind. Arbeite weiter, aber dokumentiere im Abschlussbericht, welche Dateien von dir geändert wurden.

---

# Schritt 2: PUML-/PlantUML-Vorkommen vollständig suchen

Suche breit und case-insensitive:

```bash
rg -n --hidden --glob '!/.git/**' --glob '!/.gradle/**' --glob '!build/**' \
  -i 'puml|plantuml|plant uml|\.puml|diagram|swimlane|uml|graphviz|dot|generate.*diagram|generate.*puml|render.*puml|write.*puml|pumlFile|diagramFile|plantUml'
```

Zusätzlich gezielt nach Dateinamen suchen:

```bash
find . \
  -path './.git' -prune -o \
  -path './.gradle' -prune -o \
  -path './build' -prune -o \
  -type f \( \
    -iname '*puml*' -o \
    -iname '*plantuml*' -o \
    -iname '*diagram*' -o \
    -iname '*swimlane*' -o \
    -iname '*.puml' \
  \) -print | sort
```

Suche außerdem nach Gradle-Tasks und Extension-Properties:

```bash
rg -n --hidden --glob '!/.git/**' --glob '!/.gradle/**' --glob '!build/**' \
  -i 'task\(|tasks\.register|Property<|RegularFileProperty|DirectoryProperty|output.*File|output.*Dir|extension|generate'
```

## Wenn keine Treffer gefunden werden

Wenn keine PUML-/PlantUML-relevanten Treffer gefunden werden:

1. Keine Fake-Änderungen erzeugen.
2. Einen kurzen Befund erstellen: "No PUML/PlantUML generation found in current checkout."
3. Trotzdem prüfen, ob README, Tests oder Gradle-Tasks versteckte Diagramm-Ausgaben erwähnen.
4. Nur offensichtliche tote Dokumentationsreste entfernen.
5. Abschlussbericht mit Suchbefehlen und Ergebnis erstellen.

---

# Schritt 3: Fundstellen klassifizieren

Klassifiziere alle Treffer in diese Gruppen:

## A. Öffentliche Plugin-API

Beispiele:

* Gradle Extension Properties wie `pumlOutputFile`, `diagramOutputFile`, `plantUmlEnabled`
* Gradle Tasks wie `generatePuml`, `generatePlantUml`, `generateDiagrams`
* README-Beispiele für `.puml`-Konfiguration

Entscheidung:

* Entfernen, nicht nur deaktivieren.
* Tests anpassen, sodass diese API nicht mehr erwartet wird.
* Falls ein Task automatisch registriert wurde, Registrierung entfernen.

## B. Application Layer

Beispiele:

* Use Cases für Diagramm-/PUML-Generierung
* Ports wie `DiagramRenderPort`, `PumlRenderPort`, `PlantUmlWriterPort`
* Result-Objekte mit PUML-Ausgabepfaden

Entscheidung:

* PUML-spezifische Use Cases, Ports und DTO-Felder entfernen.
* Generische Modelle nur behalten, wenn sie weiterhin für BTM- oder Analysefunktionen benötigt werden.
* Application Layer darf nicht von PlantUML, Dateiformat `.puml` oder Diagramm-Ausgabe wissen.

## C. Domain Layer

Beispiele:

* Domain-Modelle mit Namen wie `PumlNode`, `PumlEdge`, `Swimlane`, `DiagramLine`
* Domain-Services, die PlantUML-Syntax erzeugen

Entscheidung:

* PUML-Syntax gehört nicht in die Domain und ist zu entfernen.
* Generische Analysekonzepte dürfen bleiben, müssen aber frei von PUML-Begriffen sein.
* Keine Domain-Abhängigkeit auf Rendering- oder Dateiausgabe beibehalten.

## D. Infrastructure / Adapter

Beispiele:

* `PumlFileWriter`
* `PlantUmlRenderer`
* `DiagramWriter`
* Adapter für PlantUML-Server, Graphviz oder `.puml`-Ausgabe

Entscheidung:

* Entfernen, wenn ausschließlich für PUML-Ausgabe zuständig.
* Aus Dependency Injection, Registries und Task-Wiring entfernen.

## E. Build / Dependencies

Beispiele:

* PlantUML-Abhängigkeiten in `libs.versions.toml`
* `implementation(libs.plantuml)`
* Konfigurationen für PlantUML CLI oder PlantUML Server

Entscheidung:

* Unbenutzte Dependencies entfernen.
* Version-Catalog-Einträge entfernen, wenn nicht mehr verwendet.
* Dependency Verification nur anfassen, wenn durch entfernte Dependencies notwendig.

## F. Tests

Beispiele:

* Tests, die `.puml`-Dateien erwarten
* Snapshot-Tests für PlantUML-Syntax
* Gradle TestKit Tests für Diagramm-Tasks

Entscheidung:

* PUML-Tests entfernen oder in BTM-spezifische Tests umbauen.
* Neue Regressionstests ergänzen, die sicherstellen, dass nur BTM-Ausgaben erzeugt werden.

## G. Dokumentation / Beispiele

Beispiele:

* README-Abschnitte über PlantUML
* Beispiel-Konfigurationen mit `.puml`
* Architektur-Dokumente, die PUML-Ausgabe als aktuelle Funktion beschreiben

Entscheidung:

* Aktuelle Nutzungsdokumentation bereinigen.
* Falls PUML als entfernte Alt-Funktion erwähnt werden muss, klar als entfernt/deprecated im Changelog-Kontext markieren.
* Keine Anleitung stehen lassen, die Nutzer zu einer nicht mehr vorhandenen Funktion führt.

---

# Schritt 4: Tests als Sicherheitsnetz vorbereiten

Vor Änderungen mindestens relevante Tests identifizieren:

```bash
rg -n --hidden --glob '!/.git/**' --glob '!/.gradle/**' --glob '!build/**' \
  -i 'puml|plantuml|diagram|swimlane|generatePuml|generatePlantUml|generateDiagram' src/test README.md build.gradle.kts gradle
```

Falls TestKit-Tests existieren, ergänze oder ändere sie so, dass folgende Erwartungen gelten:

* Plugin registriert keine PUML-/PlantUML-Task mehr.
* Extension enthält keine PUML-/PlantUML-Properties mehr.
* Standardausführung erzeugt nur die konfigurierte `.btm`-Datei.
* Es wird keine `.puml`-Datei im Build-Verzeichnis geschrieben.

Beispielhafte Testabsicht, nicht blind übernehmen:

```java
@Test
void pluginDoesNotRegisterPlantUmlGenerationTask() {
    Project project = ProjectBuilder.builder().build();

    project.getPlugins().apply("de.burger.forensics.btmgen");

    assertThat(project.getTasks().findByName("generatePuml")).isNull();
    assertThat(project.getTasks().findByName("generatePlantUml")).isNull();
    assertThat(project.getTasks().findByName("generateDiagrams")).isNull();
}
```

Kommentare im Testcode nur auf Englisch schreiben.

---

# Schritt 5: Öffentliche Gradle-Plugin-API bereinigen

Prüfe insbesondere:

* `src/main/java/**/BtmGenExtension.java`
* `src/main/java/**/BtmGenPlugin.java`
* `src/main/java/**/GenerateBtmTask.java`
* mögliche zusätzliche Task-Klassen
* `build.gradle.kts`
* `gradle/libs.versions.toml`
* Plugin-Descriptor-Tests

Entferne:

* PUML-Properties aus Extensions
* PUML-Output-Felder aus Tasks
* Task-Registrierung für PUML-/Diagramm-Ausgaben
* Default-Konventionen für `.puml`-Dateien
* Logging, das PUML-Ausgabe ankündigt
* Validierung, die PUML-Ausgabe erwartet

Achte darauf:

* `GenerateBtmTask` darf weiterhin `.btm` schreiben.
* Task Inputs/Outputs müssen Gradle-cache-kompatibel bleiben.
* Keine unnötigen `@Input`/`@OutputFile`-Properties zurücklassen.
* Keine gebrochenen Provider-/Property-Konventionen erzeugen.

---

# Schritt 6: Application- und Domain-Schicht bereinigen

Suche nach PUML-Konzepten in Application und Domain:

```bash
rg -n -i 'puml|plantuml|diagram|swimlane|uml' src/main/java/de/burger/forensics/application src/main/java/de/burger/forensics/domain
```

Entferne PUML-spezifische Elemente vollständig:

* Use Cases
* Ports
* DTO-Felder
* Domain-Modelle
* Enum-Werte
* Result-Felder
* Factory-Methoden

Wenn ein Result-Objekt bisher sowohl BTM- als auch PUML-Ergebnisse enthielt:

* PUML-Feld entfernen.
* Konstruktoren, Builder, Tests und Assertions anpassen.
* Namen nicht unnötig ändern, wenn sie weiterhin BTM-neutral korrekt sind.

Architekturregel:

* Domain bleibt frei von Infrastrukturdetails.
* Application orchestriert weiterhin nur fachliche Use Cases.
* Rendering konkreter Dateiformate bleibt Adapter-Verantwortung.
* Da PUML entfernt wird, darf kein PUML-Adapter mehr verdrahtet sein.

---

# Schritt 7: Infrastructure-/Adapter-Code entfernen

Suche nach konkreter Rendering-/Writer-Infrastruktur:

```bash
find src/main/java -type f | sort | rg -i 'puml|plantuml|diagram|swimlane|uml'
rg -n -i 'puml|plantuml|diagram|swimlane|uml' src/main/java/de/burger/forensics/infrastructure src/main/java/de/burger/forensics/plugin
```

Entferne Klassen, die ausschließlich PUML erzeugen, zum Beispiel sinngemäß:

* `PumlRenderer`
* `PlantUmlRenderer`
* `PumlFileWriter`
* `DiagramFileWriter`
* `SwimlaneRenderer`
* `PlantUmlAdapter`
* `DiagramGenerationTask`

Danach Import- und Wiring-Fehler beheben.

Wichtig:

* Keine leeren Pakete mit toten Klassen behalten.
* Keine `UnsupportedOperationException`-Stubs einbauen.
* Keine Feature-Flags wie `plantUmlEnabled=false` behalten, wenn die Funktion vollständig entfernt werden soll.

---

# Schritt 8: Build-Konfiguration und Dependencies bereinigen

Prüfe den Version Catalog:

```bash
rg -n -i 'plantuml|puml|diagram|graphviz|guru.nidi|dot' gradle/libs.versions.toml build.gradle.kts settings.gradle.kts
```

Entferne ungenutzte Einträge:

* Versionen
* Libraries
* Plugins
* Configurations
* Dependency-Aliases

Danach prüfen:

```bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencies --configuration compileClasspath
```

Wenn PlantUML-/Diagramm-Abhängigkeiten verschwinden, aber `gradle/verification-metadata.xml` vorhanden ist:

* Keine unnötige Neuerzeugung der gesamten Datei.
* Nur dann bereinigen, wenn der Build oder die Dependency Verification wegen entfernter/veralteter Einträge fehlschlägt.
* Keine neuen Trust-Blöcke hinzufügen, sofern nicht zwingend nötig.

---

# Schritt 9: Dokumentation und Beispiele bereinigen

Prüfe:

```bash
rg -n -i 'puml|plantuml|diagram|swimlane|uml' README.md QUALITY.md docs examples src/test
```

Aktualisiere:

* README Plugin-Beschreibung
* Beispielkonfiguration
* Output-Beschreibung
* Task-Beschreibung
* Qualitäts-/Run-Anleitungen
* Beispielcode

Nach der Änderung muss die README eindeutig sagen:

* Das Plugin generiert Byteman `.btm`-Regeln.
* Es wird keine PUML-/PlantUML-Ausgabe erzeugt.
* Output-Konfiguration bezieht sich nur auf `.btm`.

Entferne alle Beispiele wie:

```kotlin
pumlOutputFile.set(...)
generatePuml
generatePlantUml
generateDiagrams
```

---

# Schritt 10: Regressionstests ausführen

Führe mindestens aus:

```bash
./gradlew clean test --console=plain
./gradlew check --console=plain
```

Wenn im Projekt vorhanden, zusätzlich:

```bash
./gradlew validatePlugins --console=plain
./gradlew jacocoTestReport jacocoTestCoverageVerification --console=plain
./gradlew checkPackageCoverage --console=plain
```

Wenn ein Task nicht existiert, dokumentiere das im Abschlussbericht und fahre mit den existierenden Quality-Gates fort.

---

# Schritt 11: Negative Suche nach Resten

Nach allen Änderungen erneut suchen:

```bash
rg -n --hidden --glob '!/.git/**' --glob '!/.gradle/**' --glob '!build/**' \
  -i 'puml|plantuml|plant uml|\.puml|generatePuml|generatePlantUml|pumlOutput|plantUml|PlantUML'
```

Erwartung:

* Keine produktiven Treffer.
* Keine Testtreffer, außer falls ein Test ausdrücklich sicherstellt, dass PUML nicht mehr existiert.
* Keine README-Anleitung zur PUML-Erzeugung.
* Keine Gradle-Konfiguration für PUML.

Wenn Treffer bleiben, jeden Treffer einzeln bewerten:

* Muss er entfernt werden?
* Ist er ein negativer Regressionstest?
* Ist er nur Teil dieses Workflows?

---

# Schritt 12: Architekturprüfung

Prüfe gedanklich und, falls vorhanden, per ArchUnit:

* Keine Domain-Abhängigkeit auf PUML-/PlantUML-Klassen.
* Keine Application-Abhängigkeit auf konkrete Dateiformat-Renderer.
* Plugin-Adapter orchestrieren nur noch BTM-Generierung.
* Keine zyklischen Abhängigkeiten durch die Entfernung entstanden.
* Keine toten Ports oder Adapter übrig.

Wenn ArchUnit-Tests existieren, müssen sie unverändert oder sinnvoll angepasst bestehen bleiben.

---

# Schritt 13: Abschlussbericht erstellen

Am Ende einen kurzen Bericht ausgeben:

## Summary

* Was wurde entfernt?
* Welche Dateien wurden geändert?
* Welche öffentlichen API-Elemente wurden entfernt?
* Welche Tests wurden angepasst oder ergänzt?

## Verification

Liste die ausgeführten Befehle mit Ergebnis:

```text
./gradlew clean test --console=plain -> PASS/FAIL
./gradlew check --console=plain -> PASS/FAIL
```

Falls ein Befehl fehlschlägt:

* Fehlerursache knapp benennen.
* Bereits behobene Punkte nennen.
* Nicht behaupten, dass die Aufgabe vollständig abgeschlossen ist.

## Remaining Notes

Nur aufführen, wenn wirklich etwas offen ist.

---

# Akzeptanzkriterien

Die Aufgabe ist erst abgeschlossen, wenn alle folgenden Punkte erfüllt sind:

* `rg -i 'puml|plantuml|\.puml|generatePuml|generatePlantUml|pumlOutput|plantUml'` findet keine produktiven PUML-Reste mehr.
* Das Plugin erzeugt weiterhin `.btm`-Dateien.
* Es wird keine `.puml`-Datei erzeugt.
* Es gibt keine PlantUML-spezifische Dependency mehr, sofern sie ausschließlich für PUML-Generierung genutzt wurde.
* README und Beispiele enthalten keine PUML-Generierungsanleitung mehr.
* JUnit-5-Tests laufen erfolgreich.
* ArchUnit-/Qualitätsregeln bleiben aktiv.
* Gradle-Plugin-Validierung bleibt erfolgreich, sofern der Task im Projekt verfügbar ist.

---

# Commit-Vorschlag

Wenn alles grün ist, verwende sinngemäß diesen Commit-Titel:

```text
Remove PlantUML generation from BTM plugin
```

Commit-Body:

```text
Remove PUML/PlantUML generation paths from the Gradle plugin while keeping Byteman rule generation intact.

- removed PlantUML-specific task/extension wiring
- removed PUML renderer/writer code where present
- removed PUML-related tests or converted them to BTM-focused regression tests
- updated documentation to describe BTM output only
- verified the Gradle quality gate
```
