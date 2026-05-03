# workflow.md — Fix BTM Generation for Maven/Gradle Analysis

## Rolle

Du arbeitest als Codex-Agent in einem Java-Projekt für Forensics-Tracing und Byteman-Regelgenerierung.

Du bist spezialisiert auf:

* Java 17
* Gradle 9.4
* Maven-Plugin-Adapter
* Byteman-Regelsyntax
* JavaParser-basierte Source-Code-Analyse
* JUnit 5 Tests
* ArchUnit Tests
* saubere Port-/Adapter-Trennung

Alle Source-Code-Kommentare, JavaDoc-Kommentare, Commit Messages und technische Namen im Code müssen auf Englisch verfasst werden.

Antworten und Abschlussberichte an den Nutzer dürfen auf Deutsch erfolgen.

---

## Ziel

Der Generator erzeugt aktuell eine `forensics.btm`, die grundsätzlich beweist, dass der Scanner läuft. Die Datei ist aber noch nicht sauber genug für belastbare Analyse großer Projekte wie WildFly.

Diese Workflow-Aufgabe soll die Byteman-Regelerzeugung so korrigieren, dass:

1. `THROW`-Regeln syntaktisch und fachlich korrekt sind.
2. `RETURN`-/`AT EXIT`-Regeln nicht mehrfach pro Methode redundant erzeugt werden.
3. Methodensignaturen optional bis in die Byteman-Regeln transportiert werden können.
4. Der Maven-Scanbereich nicht versehentlich `src/test/java`, `target`, `build`, `.git` oder komplette Repository-Wurzeln analysiert.
5. Der Gradle-Teil nicht beschädigt wird.
6. Der Maven-Teil, falls vorhanden, denselben Use-Case wie der Gradle-Adapter nutzt.
7. Alle Änderungen durch JUnit-Tests abgesichert werden.

---

## Wichtiger Ausgangsbefund

Eine erzeugte `forensics.btm` enthielt ungefähr:

```text
125700 generated rules
0 duplicate rule ids
0 malformed rule blocks
5486 THROW rules
27589 RETURN rules
36532 IF_TRUE / IF_FALSE rules
25518 METHOD_ENTER rules
25518 METHOD_EXIT rules
```

Die Datei war formal groß und generiert, aber drei Dinge waren auffällig:

### Problem 1 — falsche `THROW`-Condition

Aktuelles problematisches Muster:

```text
RULE ... : throw some.Class#method#method
CLASS some.Class
METHOD method
HELPER de.burger.forensics.infrastructure.rt.RtTraceHelper
AT THROW
IF SomeLogger.LOGGER.someExceptionFactoryCall($this.value)
DO
    onException($^);
ENDRULE
```

Das ist falsch, weil `IF` eine boolean expression erwartet. Ein Exception-Factory-Ausdruck ist keine sichere boolean condition.

Erwartetes Muster:

```text
RULE ... : throw some.Class#method#method
CLASS some.Class
METHOD method
HELPER de.burger.forensics.infrastructure.rt.RtTraceHelper
AT THROW
IF true
DO
    onException($^);
ENDRULE
```

Optional darf später ein Exception-Type-Filter ergänzt werden, aber nicht in dieser Aufgabe.

---

### Problem 2 — mehrfach erzeugte `RETURN`-Regeln mit identischem `AT EXIT`

Aktuelles problematisches Muster:

```text
RULE ... : return some.Class#method#method
CLASS some.Class
METHOD method
HELPER de.burger.forensics.infrastructure.rt.RtTraceHelper
AT EXIT
IF true
DO
    onExit(..., $!);
ENDRULE
```

Wenn eine Methode mehrere `return`-Statements enthält, werden aktuell mehrere `RETURN`-Regeln erzeugt. Alle hängen aber an `AT EXIT`. Dadurch entstehen redundante Exit-Regeln pro Methode.

Erwartung:

* Pro Methode darf es maximal eine generische `AT EXIT`-Regel geben.
* Wenn konkrete `return`-Statements später separat verfolgt werden sollen, muss das über `AT LINE <line>` oder ein separates Eventmodell erfolgen.
* In dieser Aufgabe soll die Redundanz entfernt werden.

---

### Problem 3 — Methodenzeilen ohne Signatur

Aktuelles Muster:

```text
METHOD deploy
METHOD execute
METHOD getValue
```

Für überladene Methoden ist das riskant.

Ziel:

* Die Methodensignatur soll im internen Modell vorhanden sein.
* Der Renderer soll optional signierte Methodenzeilen erzeugen können.
* Default darf weiterhin kompatibel bleiben, wenn bestehende Tests und Byteman-Kompatibilität das verlangen.

Beispiel Zieloption:

```text
METHOD execute(org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)
```

Wenn die vollständige Signatur aus dem vorhandenen AST-Modell nicht risikofrei ableitbar ist, muss der Agent sauber dokumentieren, was fehlt, und mindestens das Modell vorbereiten.

---

## Verbindliche Architekturregel

Der Maven-Adapter darf keine Fachlogik enthalten.

Erlaubt:

```text
Maven Mojo
  -> builds request
  -> calls application use case
  -> writes configured output
```

Nicht erlaubt:

```text
Maven Mojo
  -> scans Java files directly
  -> renders Byteman manually
  -> duplicates Gradle task logic
```

Die Fachlogik muss im bestehenden Application-/Domain-/Renderer-Bereich bleiben.

---

## Relevante Klassen / Suchanker

Prüfe mindestens diese Klassen und Pakete:

```text
src/main/java/de/burger/forensics/adapters/javaparser/JavaParserScanner.java
src/main/java/de/burger/forensics/adapters/javaparser/MethodEventExtractor.java
src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java
src/main/java/de/burger/forensics/application/service/GenerationRequest.java
src/main/java/de/burger/forensics/application/service/RuleGenerationResult.java
src/main/java/de/burger/forensics/plugin/btmgen/render/api/RuleParams.java
src/main/java/de/burger/forensics/plugin/btmgen/render/strategy/ThrowRuleStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/strategy/ReturnRuleStrategy.java
src/main/java/de/burger/forensics/plugin/btmgen/render/BytemanRuleRenderer.java
src/main/java/de/burger/forensics/plugin/btmgen/gradle/GenerateBtmTask.java
```

Falls vorhanden, zusätzlich:

```text
src/main/java/de/burger/forensics/plugin/btmgen/maven/GenerateBtmMojo.java
```

Falls der Maven-Pfad fehlt, nicht blind umfangreich umbauen. Dann nur vorbereiten oder klar berichten, dass der Maven-Adapter separat ergänzt werden muss.

---

## Phase 1 — Repository-Inspektion

Führe zuerst aus:

```bash
git status --short
find src/main/java -type f | sort
find src/test/java -type f | sort
```

Prüfe danach:

```bash
./gradlew --version
./gradlew tasks --all
```

Wenn Maven-Dateien vorhanden sind:

```bash
find . -name "pom.xml" -print
find . -path "*maven*" -type f -print
```

Erwartung:

* Keine Änderung beginnen, bevor der aktuelle Projektzustand verstanden wurde.
* Keine fremden Änderungen überschreiben.
* Keine großen Architekturumbauten ohne Notwendigkeit.

---

## Phase 2 — Aktuellen Fehler reproduzieren

Suche Tests für:

```text
ThrowRuleStrategy
ReturnRuleStrategy
BytemanRuleRenderer
MethodEventExtractor
GenerateRulesUseCase
GenerateBtmTask
```

Führe relevante Tests aus:

```bash
./gradlew test
```

Wenn es spezielle Testtasks gibt, ebenfalls ausführen.

Wenn möglich, erzeuge eine kleine Testquelle mit:

```java
package com.example;

final class ExampleService {

    String map(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative");
        }
        if (value == 0) {
            return "zero";
        }
        return "positive";
    }
}
```

Erwartete neue Generator-Eigenschaften:

* Eine Throw-Regel mit `AT THROW` und `IF true`.
* Maximal eine generische `AT EXIT`-Regel für `map`.
* Keine mehrfachen identischen `return ... AT EXIT`-Regeln.

---

## Phase 3 — Fix für `THROW`-Regeln

### Ziel

`THROW`-Regeln dürfen keine Exception-Factory-Ausdrücke als `IF`-Condition verwenden.

### Umsetzung

Prüfe die Klasse:

```text
ThrowRuleStrategy.java
```

Korrigiere die Regelgenerierung so, dass `AT THROW` standardmäßig erzeugt:

```text
AT THROW
IF true
DO
    onException($^);
ENDRULE
```

### Verboten

Nicht erzeugen:

```text
IF SomeLogger.LOGGER.someExceptionFactoryCall(...)
```

Nicht erzeugen:

```text
IF new IllegalArgumentException(...)
```

Nicht erzeugen:

```text
IF throwExpression
```

### Testanforderung

Ergänze oder aktualisiere einen JUnit-5-Test, der sicherstellt:

```text
- rendered rule contains "AT THROW"
- rendered rule contains "IF true"
- rendered rule contains "onException($^)"
- rendered rule does not contain the original throw expression in the IF line
```

Testname-Vorschlag:

```java
throwRuleShouldUseBooleanConditionInsteadOfThrowExpression()
```

---

## Phase 4 — Fix für redundante `RETURN`-/`AT EXIT`-Regeln

### Ziel

Eine Methode mit mehreren `return`-Statements darf nicht mehrere generische `AT EXIT`-Regeln erhalten.

### Analyse

Prüfe, wo `RETURN`-Events entstehen:

```text
MethodEventExtractor.java
JavaParserScanner.java
GenerateRulesUseCase.java
```

Prüfe, wo sie gerendert werden:

```text
ReturnRuleStrategy.java
BytemanRuleRenderer.java
```

### Bevorzugte Lösung

Die sauberste Lösung ist:

* `METHOD_EXIT` bleibt der generische Exit-Hook.
* `RETURN` darf nicht zusätzlich als generischer `AT EXIT`-Hook gerendert werden, wenn dadurch mehrere identische Exit-Regeln entstehen.
* Entweder:

    * `RETURN`-Events werden dedupliziert auf eine Regel pro Methode, oder
    * `RETURN`-Events werden künftig nur als zeilenbezogene Events modelliert, aber noch nicht gerendert, wenn keine sichere Line-Strategie vorhanden ist.

### Minimal akzeptabler Fix

Wenn der bestehende Renderer `RETURN` zwingend erwartet:

* Gruppiere `RETURN`-Events pro Klasse + Methode + Signatur.
* Erzeuge maximal eine `RETURN`-Regel pro Methode.
* Verwende stabilen Rule-Namen ohne zufällige Kollisionen.

### Testanforderung

Ergänze einen Test mit einer Methode, die zwei oder drei `return`-Statements enthält.

Erwartung:

```text
- generated rules contain at most one RETURN/AT EXIT rule for that method
- generated rules may contain one METHOD_EXIT/AT EXIT rule
- no duplicate AT EXIT return rules exist for the same method
```

Testname-Vorschlag:

```java
methodWithMultipleReturnsShouldNotGenerateMultipleGenericExitReturnRules()
```

---

## Phase 5 — Methodensignaturen vorbereiten oder aktivieren

### Ziel

Das interne Modell soll Methodensignaturen tragen können.

Prüfe, ob das Eventmodell bereits Felder für Parameter oder Signaturen hat.

Falls nicht vorhanden, ergänze vorsichtig ein Feld wie:

```java
String methodSignature
```

oder strukturierter:

```java
List<String> parameterTypeNames
```

### Anforderungen

* Keine Signatur über unsichere String-Bastelei erzeugen, wenn der AST-Typ nicht zuverlässig auflösbar ist.
* Keine JavaParser Symbol Solver Integration erzwingen, wenn sie noch nicht im Projekt vorhanden ist.
* Ohne Type Solver darf eine syntaktische Signatur aus AST-Typen erzeugt werden, wenn sie stabil ist.

Beispiel:

```java
void execute(ModelNode operation, ModelNode model)
```

kann ohne Imports nur liefern:

```text
execute(ModelNode, ModelNode)
```

Mit vollqualifizierter Auflösung wäre möglich:

```text
execute(org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)
```

### Akzeptanz

Mindestens eines der folgenden Ergebnisse muss erreicht werden:

1. Renderer kann optional signierte `METHOD`-Zeilen erzeugen.
2. Eventmodell enthält Signaturinformationen, Renderer bleibt aus Kompatibilitätsgründen zunächst unsigniert.
3. Agent dokumentiert nachvollziehbar, warum vollständige Signaturen ohne Type Solver nicht sicher möglich sind, und ergänzt Tests für das vorbereitete Modell.

### Testanforderung

Testname-Vorschlag:

```java
scannerShouldCaptureMethodSignatureFromAstParameters()
```

---

## Phase 6 — Scanbereich härten

### Ziel

Der Generator darf bei Maven-/Gradle-Projekten nicht versehentlich das komplette Repository inklusive Testquellen, Build-Ausgaben und `.git` scannen.

### Pflicht-Ausschlüsse

Folgende Verzeichnisse müssen ausgeschlossen werden:

```text
.git
.gradle
.idea
build
target
out
src/test/java
src/integrationTest/java
```

### Default-Verhalten

Für Projektadapter soll gelten:

```text
Default source root = src/main/java
```

Nicht:

```text
Default source root = project root
```

### Gradle-Adapter

Prüfe:

```text
GenerateBtmTask.java
```

Erwartung:

* Default zeigt auf `project.layout.projectDirectory.dir("src/main/java")` oder äquivalent.
* Der Nutzer kann den Pfad explizit überschreiben.
* Excludes werden auch bei explizitem Root berücksichtigt, sofern sinnvoll.

### Maven-Adapter

Falls vorhanden:

```text
GenerateBtmMojo.java
```

Erwartung:

```java
@Parameter(defaultValue = "${project.basedir}/src/main/java")
private File sourceRoot;
```

Zusätzlich sollten Excludes berücksichtigt werden.

### Testanforderung

Ein Test muss sicherstellen:

```text
- src/main/java wird analysiert
- src/test/java wird nicht analysiert
- target wird nicht analysiert
- build wird nicht analysiert
- .git wird nicht analysiert
```

Testname-Vorschlag:

```java
scannerShouldIgnoreBuildOutputAndTestSourcesByDefault()
```

---

## Phase 7 — Maven-Konfiguration prüfen oder ergänzen

### Ziel

Wenn der Maven-Adapter existiert, soll er korrekt über Maven ausführbar sein.

Prüfe auf:

```text
src/main/java/de/burger/forensics/plugin/btmgen/maven/GenerateBtmMojo.java
pom.xml
META-INF/maven/plugin.xml generation
```

Ein echter Maven-Plugin-Adapter braucht:

```xml
<packaging>maven-plugin</packaging>
```

und:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-plugin-plugin</artifactId>
    <configuration>
        <goalPrefix>forensics-btmgen</goalPrefix>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>descriptor</goal>
                <goal>helpmojo</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Die Mojo-Klasse braucht sinngemäß:

```java
@Mojo(
        name = "generate-btm-rules",
        defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES,
        threadSafe = true,
        requiresProject = true
)
public final class GenerateBtmMojo extends AbstractMojo {
    // adapter only
}
```

### Wichtig

Wenn das Projekt weiterhin primär Gradle-basiert ist, darf diese Aufgabe nicht ungefragt die gesamte Buildstruktur in ein Maven-Multi-Modul-Projekt umbauen.

Wenn Maven noch nicht vollständig vorhanden ist, dokumentiere klar:

```text
Maven adapter source exists: yes/no
Maven plugin descriptor generation exists: yes/no
Maven execution tested: yes/no
Remaining blocker: ...
```

---

## Phase 8 — Tests und Quality Gate

Führe aus:

```bash
./gradlew clean test
```

Falls vorhanden:

```bash
./gradlew jacocoTestReport jacocoTestCoverageVerification
```

Falls `check` sauber verdrahtet ist:

```bash
./gradlew clean check
```

Wenn Maven-Adapter vorhanden und baubar:

```bash
mvn clean package
```

oder, falls Maven nur in einem Untermodul liegt:

```bash
mvn -f plugin/maven/pom.xml clean package
```

Danach prüfen, ob ein Maven-Plugin-Descriptor im JAR liegt:

```bash
jar tf target/*.jar | grep "META-INF/maven/plugin.xml"
```

Wenn der Befehl wegen Gradle-Struktur nicht passt, passenden tatsächlichen JAR-Pfad verwenden.

---

## Phase 9 — Mini-Regressionsanalyse mit erzeugter BTM-Datei

Erzeuge, falls möglich, eine kleine `forensics.btm` gegen Testquellen oder ein kleines Beispielprojekt.

Prüfe automatisch oder manuell:

```text
No THROW rule has a non-boolean throw expression as IF condition.
No method has multiple generic RETURN AT EXIT rules.
No rules are generated for src/test/java when default sourceRoot is used.
No rules are generated for target/build/.git directories.
Rule ids remain unique.
Rule blocks remain syntactically complete.
```

Nützliche Prüfkommandos:

```bash
grep -n "AT THROW" target/forensics/forensics.btm | head

grep -n "IF " target/forensics/forensics.btm | head

grep -n "AT EXIT" target/forensics/forensics.btm | head
```

Wenn ein Skript ergänzt wird, dann nur als Test- oder Verification-Helfer, nicht als Produktionsumgehung.

---

## Phase 10 — Diff prüfen

Vor dem Abschluss:

```bash
git status --short
git diff --stat
git diff
```

Prüfe besonders:

```text
- keine generierten Großdateien committed
- keine forensics.btm committed, außer sie ist bewusst eine kleine Testfixture
- keine WildFly-Analyse-Artefakte committed
- keine IDE-Dateien committed
- keine target/build-Dateien committed
```

---

## Akzeptanzkriterien

Die Aufgabe ist erfüllt, wenn:

```text
1. THROW rules use a boolean condition, normally IF true.
2. THROW rules call onException($^).
3. RETURN rules no longer create multiple generic AT EXIT rules per method.
4. The scanner or adapter does not scan src/test/java by default.
5. Build output directories are excluded from scanning.
6. Existing Gradle behavior remains intact.
7. Maven adapter status is clearly verified or blocker is documented.
8. JUnit tests cover the fixed behavior.
9. ./gradlew clean test passes.
10. ./gradlew clean check passes, unless an existing unrelated blocker is documented.
```

---

## Nicht-Ziele

Nicht in dieser Aufgabe erledigen:

```text
- Full Control Flow Graph implementation
- Data Flow Graph or Program Dependence Graph implementation
- JavaParser Symbol Solver full integration, unless already present and low-risk
- Graph database integration
- UML renderer implementation
- Massive Maven/Gradle build restructuring
- WildFly-specific hardcoding
- Lowering coverage thresholds
- Disabling failing tests
- Removing quality gates
```

---

## Abschlussbericht

Am Ende muss Codex berichten:

```text
Summary:
- What changed
- Why it changed
- Which files were affected

Verification:
- Commands run
- Results

BTM behavior:
- THROW rule behavior after fix
- RETURN rule behavior after fix
- Scan scope behavior after fix

Maven status:
- Maven adapter present: yes/no
- Maven plugin descriptor generation present: yes/no
- Maven execution tested: yes/no
- Remaining Maven blocker, if any

Risks / Follow-ups:
- Remaining limitations
- Especially method signature limitations if full FQNs are not available
```

---

## Commit Message Vorlage

```text
fix: harden BTM rule generation for Maven and Gradle analysis

Fix Byteman rule generation issues discovered during large-project analysis.

Changes:
- Use a boolean condition for THROW rules instead of rendering throw expressions as IF conditions.
- Prevent redundant generic RETURN AT EXIT rules for methods with multiple return statements.
- Prepare or propagate method signature information where safely available.
- Restrict default scan scope to production Java sources and exclude build/test/output directories.
- Keep Maven adapter behavior aligned with the shared application use case.

Why:
- Byteman IF clauses require boolean expressions.
- Multiple RETURN rules bound to the same AT EXIT location create redundant instrumentation.
- Large projects such as WildFly expose scan-scope and overload-resolution weaknesses.

Verification:
- ./gradlew clean test
- ./gradlew clean check
- Maven plugin verification if applicable

Notes:
- No generated large BTM files are committed.
```
