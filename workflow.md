# workplan.md — AST Context Propagation Fix

## Ziel

Die aktuell beobachteten Warnungen

```text
Suspicious unresolved type references: 947 occurrences, 252 unique names.
```

sollen nicht durch pauschale Symbol-Unterdrückung kaschiert werden. Stattdessen muss der AST-Kontext, der beim Scannen bereits vorhanden ist, sauber bis zur Bedingungs-Erzeugung und zur Validierung durchgereicht werden.

Das Ziel ist:

```text
AST scan context
  -> condition rendering with type context
  -> ScanEvent with traceable metadata
  -> rule generation
  -> BTM rule with resolvable IF expression
  -> grouped validation report
```

Die BTM-Regeln dürfen keine vermeidbaren unqualifizierten Source-Level-Typreferenzen in `IF`-Ausdrücken enthalten, wenn diese Typen über JavaParser Symbol Solver, explizite Imports oder deterministische Scanner-Kontexte auflösbar sind.

---

## Rahmenbedingungen

* Gradle-Version bleibt unverändert bei der im Projekt verwendeten Version.
* Java-Version bleibt unverändert bei der im Projekt verwendeten Toolchain.
* Keine Umstellung auf Spring, Maven oder andere Frameworks.
* Keine globale Deduplizierung nach Symbolname.
* Keine pauschale Suppression von Warnungen.
* Source-Code-Kommentare müssen auf Englisch formuliert werden.
* Antworten, Dokumentation und Workplan dürfen deutsch sein.
* Bestehende öffentliche APIs dürfen nur bewusst und testgestützt erweitert werden.
* Existing behavior must be preserved unless a regression test proves the previous behavior was wrong.

---

## Problemzusammenfassung

Der Scanner erzeugt aus JavaParser-AST-Daten BTM-Regeln. Während des Scans existiert bereits ein `MethodScanContext`, der Teile des AST-Kontexts kennt:

* `MethodDeclaration`
* Parameter-Indizes
* lokale Variablennamen
* explizite nicht-statische Type-Imports
* explizite statische Member-Imports

Der aktuelle kritische Punkt ist:

```text
MethodEventExtractor
  -> renderingStrategy.renderCondition(condition, context)
  -> String renderedCondition
  -> ScanEvent.conditionText()
  -> GenerateRulesUseCase
  -> ConditionStrategy
  -> Byteman renderer
  -> IF <plain string>
```

Damit wird der AST-Kontext zu früh auf einen String reduziert. Nach der Erzeugung von `ScanEvent.conditionText()` sind wichtige Kontextinformationen nicht mehr verfügbar.

Verloren oder nicht modelliert sind insbesondere:

* Wildcard-Type-Imports
* Wildcard-Static-Imports
* Same-Package-Type-Kandidaten
* Nested-Type-Kontext
* resolved symbol metadata
* Source-Import-Tabelle
* aufgelöste Fully-Qualified-Type-Namen
* Herkunftsinformationen für unresolved warnings

Dadurch können unqualifizierte Typreferenzen wie diese in finale BTM-Regeln gelangen:

```text
IF eval("...", "!DeploymentTypeMarker.isType(DeploymentType.EAR, $deploymentUnit)", !DeploymentTypeMarker.isType(DeploymentType.EAR, $deploymentUnit))
```

Das ist verhaltensrelevant, weil Byteman diese einfachen Namen beim Laden oder Ausführen der Rule möglicherweise nicht auflösen kann.

---

## Architekturentscheidung

Der Fix erfolgt nicht im BTM-Renderer und nicht durch nachträgliches Parsen fertiger Rules.

Der Fix muss vor oder während der Erstellung von `ScanEvent` erfolgen.

Richtig ist:

```text
AST expression + MethodScanContext + SymbolSolver
  -> rendered condition with qualified type references
  -> structured diagnostics
  -> ScanEvent
```

Falsch wäre:

```text
BTM rule string
  -> nachträglich analysieren
  -> Typen erraten
```

Begründung:

Die BTM-Rule ist ein abgeleitetes Artefakt. Die Quelle der Wahrheit ist der AST-Kontext während des Scans.

---

## Zielmodell

### 1. Scanner-Kontext erweitern

`MethodScanContext` soll nicht nur explizite Imports tragen, sondern einen vollständiger nutzbaren Source-Kontext.

Zielstruktur, sinngemäß:

```java
public record SourceScanContext(
        String packageName,
        String sourceFilePath,
        String fullyQualifiedClassName,
        String simpleClassName,
        String methodName,
        String methodSignature,
        ImportTable importTable,
        TypeResolutionContext typeResolutionContext
) {
}
```

Hinweis: Die konkrete Benennung muss sich an der bestehenden Projektstruktur orientieren. Nicht blind neue Klassen erzeugen, wenn es bereits passende Modellklassen gibt.

### 2. Import-Tabelle modellieren

Die Import-Daten sollen explizit modelliert werden.

Zielstruktur, sinngemäß:

```java
public record ImportTable(
        Map<String, String> explicitTypeImports,
        Set<String> wildcardTypeImports,
        Map<String, String> explicitStaticMemberImports,
        Set<String> wildcardStaticImports
) {
}
```

Dabei gilt:

* Explizite Imports sind deterministisch auflösbar.
* Wildcard-Imports sind Kandidaten, aber nicht automatisch eindeutig.
* Same-Package-Kandidaten dürfen nur verwendet werden, wenn sie sicher bestimmt werden können.
* Ambige Fälle bleiben sichtbar und werden nicht still geraten.

### 3. Condition Rendering typbewusst machen

Die Bedingung darf nicht mehr nur als plain AST-to-string Rendering entstehen.

Ziel:

```text
NameExpr / FieldAccessExpr / MethodCallExpr
  -> type candidate detection
  -> symbol solver resolution if possible
  -> deterministic import fallback if possible
  -> qualified condition expression
  -> unresolved diagnostic if not possible
```

Beispiel vorher:

```java
DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)
```

Beispiel nachher:

```java
org.jboss.as.server.deployment.DeploymentTypeMarker.isType(org.jboss.as.server.deployment.DeploymentType.EAR, deploymentUnit)
```

Der exakte Fully-Qualified-Name ist aus dem Projekt/SymbolSolver zu bestimmen, nicht aus diesem Workplan zu übernehmen.

---

## Geplante Umsetzung in Slices

## Slice 1 — Regression Tests für aktuelle Fehlerklasse

### Ziel

Vor jeder Änderung müssen Tests zeigen, dass die aktuellen Fälle wirklich abgedeckt werden.

### Neue oder erweiterte Tests

Tests für Condition Rendering mit:

1. explizitem Type-Import
2. explizitem Static-Import
3. Wildcard-Type-Import
4. Wildcard-Static-Import
5. Same-Package-Type
6. Nested-Type
7. unresolved type reference
8. ambigem Typnamen

### Erwartung

Explizit auflösbare Typen werden qualifiziert.

Nicht sicher auflösbare Typen werden nicht geraten, sondern als Diagnostic erhalten.

### Beispiel-Testfälle

#### Expliziter Type-Import

Input:

```java
import com.example.DeploymentType;

class Sample {
    boolean test(Unit unit) {
        return DeploymentType.EAR != null;
    }
}
```

Expected:

```text
com.example.DeploymentType.EAR
```

#### Same-Package-Type

Input:

```java
package com.example;

class Sample {
    boolean test() {
        return LocalType.enabled();
    }
}
```

Expected, wenn `com.example.LocalType` im Scan-Kontext sicher bekannt ist:

```text
com.example.LocalType.enabled()
```

#### Unresolved

Input:

```java
class Sample {
    boolean test() {
        return UnknownType.enabled();
    }
}
```

Expected:

```text
UnknownType.enabled()
```

plus Diagnostic:

```text
symbol=UnknownType
resolutionStatus=UNRESOLVED
```

### Akzeptanzkriterien

* Tests laufen vor dem Fix mindestens teilweise rot.
* Tests laufen nach dem Fix grün.
* Keine Tests werden durch globale Symbol-Suppression grün gemacht.

---

## Slice 2 — Source-Kontext sauber modellieren

### Ziel

Der Scanner muss den Kontext, aus dem die BTM-Rule ohnehin entsteht, als wiederverwendbares Modell bereitstellen.

### Aufgaben

1. Bestehende Klassen prüfen:

    * `MethodScanContext`
    * `MethodEventExtractor`
    * vorhandene Import-/Resolver-Hilfsklassen
    * vorhandene Finding-/Diagnostic-Modelle

2. Entscheiden, ob `MethodScanContext` erweitert oder durch ein ergänzendes Modell gekapselt wird.

3. Zusätzliche Kontextdaten ergänzen:

    * Package-Name
    * Source-Datei
    * Fully-Qualified-Class-Name
    * Simple-Class-Name
    * Method-Name
    * Method-Signature
    * Import-Tabelle inklusive Wildcard-Imports

4. Sicherstellen, dass Nested Classes korrekt abgebildet werden:

```text
Outer.Inner in Java source
Outer$Inner in BTM CLASS target
```

### Akzeptanzkriterien

* BTM-Rule-Erzeugung bleibt unverändert funktionsfähig.
* Bestehende Tests bleiben grün.
* Neue Kontextdaten sind im Scanner verfügbar, bevor `conditionText` erzeugt wird.

---

## Slice 3 — ImportTable einführen oder vervollständigen

### Ziel

Imports dürfen nicht länger nur teilweise und gefiltert als Map existieren.

Aktuelles Problem:

```java
.filter(importDeclaration -> !importDeclaration.isAsterisk())
```

Wildcard-Imports werden dadurch aktiv aus dem Kontext entfernt.

### Aufgaben

1. Import-Erfassung so umbauen, dass folgende Gruppen separat erhalten bleiben:

```text
explicit type imports
wildcard type imports
explicit static member imports
wildcard static imports
```

2. Keine automatische Auflösung von Wildcard-Imports ohne eindeutige Kandidaten.

3. Explizite Imports weiterhin deterministisch verwenden.

4. Tests ergänzen für:

    * `import com.example.TypeName;`
    * `import com.example.*;`
    * `import static com.example.TypeName.MEMBER;`
    * `import static com.example.TypeName.*;`

### Akzeptanzkriterien

* Wildcard-Imports werden im Kontext sichtbar.
* Explizite Imports funktionieren wie bisher oder besser.
* Keine ambigen Wildcard-Kandidaten werden stillschweigend falsch qualifiziert.

---

## Slice 4 — Type Reference Qualification im Condition Rendering

### Ziel

Der Condition Renderer soll einfache Typnamen qualifizieren, wenn dies sicher möglich ist.

### Aufgaben

1. Bestehende Renderer prüfen:

    * `DefaultConditionRenderingStrategy`
    * `InstanceFieldNormalizer`
    * `StaticFieldQualifier`
    * weitere Hilfsklassen für Ausdrucksnormalisierung

2. Einen dedizierten Service einführen oder vorhandene Logik erweitern:

```java
public interface TypeReferenceQualifier {
    QualifiedExpression qualify(Expression expression, MethodScanContext context);
}
```

3. Priorität der Auflösung:

```text
1. JavaParser Symbol Solver
2. explicit type imports
3. same-package known types, only if indexed and unique
4. nested type context
5. explicit static member imports
6. wildcard imports as candidates only if unique
7. unresolved diagnostic
```

4. Niemals blind qualifizieren, wenn mehrere Kandidaten möglich sind.

5. Bei unresolved Fällen die ursprüngliche Expression erhalten.

### Akzeptanzkriterien

* Auflösbare Typreferenzen in IF-Bedingungen werden vollqualifiziert.
* Nicht auflösbare Typreferenzen bleiben unverändert, erzeugen aber strukturierte Diagnostics.
* Keine pauschale Symbol-Unterdrückung.
* Keine nachträgliche String-Heuristik im BTM-Renderer.

---

## Slice 5 — ScanEvent um Diagnostics erweitern

### Ziel

`ScanEvent` darf weiterhin `conditionText` enthalten, aber zusätzlich müssen Diagnoseinformationen mitgeführt werden.

### Zielstruktur, sinngemäß

```java
public record ScanEvent(
        ...,
        String conditionText,
        List<ConditionDiagnostic> conditionDiagnostics
) {
}
```

Beispiel Diagnostic:

```java
public record ConditionDiagnostic(
        String symbol,
        String expressionPreview,
        String resolutionStatus,
        String reason,
        SourceLocation location,
        SourceContext sourceContext
) {
}
```

Statuswerte, sinngemäß:

```text
RESOLVED_BY_SYMBOL_SOLVER
RESOLVED_BY_EXPLICIT_IMPORT
RESOLVED_BY_SAME_PACKAGE
RESOLVED_BY_NESTED_TYPE
UNRESOLVED
AMBIGUOUS
UNSUPPORTED
```

### Migrationsregel

Wenn `ScanEvent` aktuell ein Domain-Record ist, die Änderung minimal halten:

* Konstruktoren/Factory-Methoden anpassen
* bestehende Aufrufer aktualisieren
* Default `List.of()` für Events ohne Diagnostics verwenden

### Akzeptanzkriterien

* `ScanEvent.conditionText()` bleibt verfügbar.
* Diagnostics werden nicht im BTM-Renderer benötigt.
* Validierung/Reporting kann Diagnostics verwenden.
* Bestehende Rule-Erzeugung bleibt stabil.

---

## Slice 6 — Validierungsreport gruppieren

### Ziel

Der Report soll Rauschen reduzieren, ohne Rohdaten zu verlieren.

Nicht mehr nur flach:

```text
947 occurrences, 252 unique names
```

Sondern zusätzlich gruppiert:

```text
symbol
 └── package
     └── class
         └── method
             └── locations
```

### Zielausgabe

Beispiel:

```text
Suspicious unresolved type references: 947 occurrences, 252 unique names.

Symbol: DeploymentTypeMarker
Total occurrences: 12
Packages: 2
Classes: 3
Methods: 5

  org.jboss.as.server.deployment
    DeploymentProcessor
      deploy()
        - DeploymentProcessor.java:184
        - DeploymentProcessor.java:195
```

### Zählweise

```text
Total findings              = alle relevanten Fundstellen
Unique symbols              = unterschiedliche Symbolnamen
Technical duplicates removed = echte technische Dubletten
Suppressed by allowlist      = bewusst unterdrückte False Positives
Reported symbol groups       = Anzahl gruppierter Symbole
```

### Wichtige Regel

```text
Grouping is presentation only.
It must not change raw findings.
```

### Akzeptanzkriterien

* Raw Findings bleiben vollständig erhalten.
* Report ist nach Symbol, Package, Class, Method und Location gruppiert.
* Derselbe Symbolname an mehreren Fundstellen bleibt sichtbar.
* Exakte technische Duplikate werden weiterhin entfernt.

---

## Slice 7 — Exakte technische Duplikate absichern

### Ziel

Doppelte Findings sollen nur entfernt werden, wenn sie wirklich technisch identisch sind.

### Duplicate Key

Ein technisches Duplikat liegt nur vor bei gleicher Kombination aus:

```text
normalized location
symbol
expressionPreview or expression hash
source context
```

Nicht ausreichend ist:

```text
symbol
```

### Aufgaben

1. Bestehende Deduplizierungslogik prüfen.
2. Test für identische Findings ergänzen.
3. Test für gleichen Symbolnamen an unterschiedlichen Locations ergänzen.
4. Report-Zählung prüfen.

### Akzeptanzkriterien

* Gleicher Symbolname an unterschiedlichen Stellen bleibt erhalten.
* Identische Kombination aus Location, Symbol und Expression wird kollabiert.
* Occurrence Count wird nicht künstlich verfälscht.

---

## Slice 8 — Optional: Explizite Allowlist vorbereiten

### Ziel

Bekannte False Positives sollen später bewusst unterdrückt werden können, ohne das aktuelle Problem zu verdecken.

### Wichtig

Diese Slice ist optional und darf erst umgesetzt werden, wenn die Context Propagation und Report-Gruppierung funktionieren.

### Beispielstruktur

```json
{
  "allowedUnresolvedSymbols": [
    {
      "symbol": "SomeEnum",
      "reason": "Known enum resolved through wildcard import in target runtime",
      "scope": "global"
    },
    {
      "symbol": "CustomerType",
      "reason": "Same-package type not available in scanner classpath",
      "scope": "package",
      "packageName": "com.example.customer"
    }
  ]
}
```

### Akzeptanzkriterien

* Jeder Allowlist-Eintrag benötigt einen Grund.
* Allowlist-Zählung erscheint separat im Report.
* Allowlist ersetzt keine technische Auflösung.

---

## Nicht-Ziele

Diese Dinge sind ausdrücklich nicht Teil dieses Fixes:

* globale Suppression nach Symbolname
* Entfernen von Warnungen ohne Ursache zu beheben
* nachträgliches Erraten von Typen aus fertigen BTM-Dateien
* Umbau des gesamten Scanners
* Einführung einer Datenbank
* Einführung einer Graphdatenbank
* Änderung des Byteman Runtime Helpers ohne konkrete Notwendigkeit
* Änderung des Rule-Formats ohne Rückwärtsprüfung

---

## Erwartete technische Leitplanken

### Keine String-Heuristik als Hauptlösung

String-Ersetzung wie diese ist nur als letzter, eng getesteter Fallback erlaubt:

```text
replace("DeploymentType.", "org.example.DeploymentType.")
```

Bevorzugt ist AST-basierte Transformation.

### Symbol Solver bevorzugen

Da das Projekt bereits konfiguriert:

```java
configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
```

soll diese Fähigkeit genutzt werden, wo sie zuverlässig funktioniert.

### Resolver-Fehler dürfen den Scan nicht abbrechen

Wenn JavaParser nicht auflösen kann:

```text
catch resolution failure
  -> keep original expression
  -> add diagnostic
  -> continue scan
```

### Keine stillen Annahmen

Bei Ambiguität:

```text
AMBIGUOUS diagnostic
```

Nicht:

```text
nimm den ersten Kandidaten
```

---

## Konkrete Prüfpunkte im vorhandenen Code

Die folgenden Dateien sind gezielt zu prüfen und wahrscheinlich anzupassen:

```text
src/main/java/de/burger/forensics/adaptersupport/javaparser/MethodEventExtractor.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/MethodScanContext.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/DefaultConditionRenderingStrategy.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/InstanceFieldNormalizer.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/StaticFieldQualifier.java
src/main/java/de/burger/forensics/domain/model/ScanEvent.java
src/main/java/de/burger/forensics/application/service/GenerateRulesUseCase.java
src/main/java/de/burger/forensics/plugin/btmgen/internal/BytemanRuleRenderAdapter.java
src/main/java/de/burger/forensics/plugin/btmgen/render/impl/AbstractIfRuleStrategy.java
```

Wichtig:

`BytemanRuleRenderAdapter` und `AbstractIfRuleStrategy` sollten möglichst nicht die neue Typauflösung übernehmen. Sie dienen nur als Nachweis, dass downstream aktuell nur noch ein String ankommt.

---

## Teststrategie

### Unit Tests

* `MethodEventExtractorTest`
* `DefaultConditionRenderingStrategyTest`
* Tests für neuen `TypeReferenceQualifier`
* Tests für `ImportTable`
* Tests für grouped report model
* Tests für duplicate key behavior

### Integration Tests

* Mini-Projekt mit expliziten Imports
* Mini-Projekt mit Wildcard-Imports
* Mini-Projekt mit Same-Package-Typen
* Mini-Projekt mit Nested Types
* Mini-Projekt mit bewusst unresolved Typen

### Golden Master / Snapshot Tests

Für BTM-Ausgabe:

```text
input Java source
  -> generated .btm
  -> assert IF expression contains qualified type names where expected
```

### Negative Tests

* Ambiger Typ wird nicht blind qualifiziert.
* Unbekannter Typ bleibt sichtbar.
* Gleicher Symbolname an mehreren Orten wird nicht global dedupliziert.

---

## Verifikation

Nach Umsetzung der Slices müssen mindestens diese Prüfungen laufen:

```bash
./gradlew clean test
./gradlew check
```

Wenn im Projekt vorhanden und relevant zusätzlich:

```bash
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification
./gradlew validatePlugins
```

Wenn Dependency Verification aktiv ist:

```bash
./gradlew check --dependency-verification strict
```

Die tatsächlich im Projekt dokumentierten Quality-Gate-Kommandos sind zu verwenden. Falls `QUALITY.md` davon abweicht, nicht im Rahmen dieses Fixes ändern, sondern als separates Dokumentationsproblem melden.

---

## Erwartetes Ergebnis

Nach erfolgreichem Fix sollte gelten:

1. Der Scanner trägt Kontextinformationen nicht nur bis zum Rule Target, sondern auch bis zur Condition-Erzeugung.
2. Auflösbare Type References werden vor `ScanEvent.conditionText()` qualifiziert.
3. Nicht auflösbare Type References werden strukturiert diagnostiziert.
4. Die Anzahl behavior-relevanter unresolved warnings sinkt durch echte Auflösung, nicht durch Unterdrückung.
5. Verbleibende Warnings sind gruppiert lesbar:

```text
symbol
 └── package
     └── class
         └── method
             └── locations
```

6. Gleiche Symbole an verschiedenen Fundstellen bleiben sichtbar.
7. Exakte technische Duplikate bleiben dedupliziert.
8. Finale BTM-Regeln enthalten keine vermeidbaren unqualifizierten Typreferenzen in `IF`-Ausdrücken.

---

## Codex-Arbeitsanweisung

Arbeite Slice für Slice.

Für jede Slice:

```text
1. Inspect current implementation.
2. Add or update regression tests first.
3. Implement the smallest production change needed.
4. Run the relevant tests.
5. Review the diff.
6. Do not continue to the next slice if tests fail.
```

Bei Unsicherheit:

```text
Stop and report.
Do not guess class names, package names, existing APIs, or resolver behavior.
Do not silently introduce fallback heuristics without tests.
```

Keine Dateien ändern, die nicht für diese Aufgabe notwendig sind.

---

## Abschlusskriterien

Der Workplan gilt als erfüllt, wenn:

* alle neuen Regression Tests grün sind,
* bestehende Tests grün bleiben,
* BTM-Output für bekannte Testfälle qualifizierte Typreferenzen enthält,
* unresolved warnings weiterhin sichtbar, aber gruppiert sind,
* keine globale Symbol-Suppression eingeführt wurde,
* der Fix vor der String-Reduktion auf `conditionText` ansetzt,
* der Byteman Renderer weiterhin nur rendert und keine AST-Kontext-Rekonstruktion übernimmt.
