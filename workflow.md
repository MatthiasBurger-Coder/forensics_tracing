# workflow.md — Fix für StackOverflowError im JavaParser Symbol Solver

## Ausgangslage

Der aktuelle WildFly-Lauf bricht beim Maven-Ziel ab:

```text
./mvnw.cmd -N generate-test-resources -Dforensics "-Dforensics.sourceRoot=$root" "-Dforensics.excludePackages=org.jboss.as.test,org.wildfly.test" -DskipTests
```

Fehlerbild aus `log.log`:

```text
[INFO] --- forensics:0.0.3-SNAPSHOT:btmgen (generate-forensics-btm-rules) @ wildfly-parent ---
[INFO] Scanning sources in D:\Projects\wildfly
[INFO] Starting rule generation at 2026-05-03T22:48:02.133278500Z for D:\Projects\wildfly
[INFO] BUILD FAILURE
Exception in thread "main" java.lang.StackOverflowError
    at com.github.javaparser.ast.expr.Name.asString(Name.java:117)
    at com.github.javaparser.ast.body.TypeDeclaration.getFullyQualifiedName(TypeDeclaration.java:221)
    at com.github.javaparser.symbolsolver.javaparsermodel.contexts.CompilationUnitContext.solveType(...)
    at com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration.getSuperClass(...)
    at com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration.getAncestors(...)
    at com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration.getAllFields(...)
    ... repeated ...
```

Der Build stirbt während des Source-Scans, bevor die BTM-Erzeugung sauber abgeschlossen werden kann.

---

## Analyse

### Direkt sichtbarer Fehler

Der Stacktrace zeigt eine endlose Rekursion im JavaParser Symbol Solver:

```text
JavaParserClassDeclaration.getAllFields()
 -> getAncestors()
 -> getSuperClass()
 -> solveType()
 -> getFullyQualifiedName()
 -> Name.asString()
 -> wieder getAllFields()
```

Das ist kein Byteman-Renderfehler. Der Fehler entsteht vorher beim AST-Scan beziehungsweise beim condition rendering.

### Wahrscheinlicher Auslöser im aktuellen Source-Stand

Im aktuellen Stand existieren zwei Stellen, die JavaParser Symbol Resolution direkt auf `NameExpr` ausführen:

```text
src/main/java/de/burger/forensics/adaptersupport/javaparser/InstanceFieldNormalizer.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/StaticFieldQualifier.java
```

Kritische Methoden:

```java
boolean resolvesToInstanceField(NameExpr name) {
   try {
      var resolved = name.resolve();
      return resolved.isField() && !resolved.asField().isStatic();
   } catch (RuntimeException ignored) {
      return false;
   }
}
```

und:

```java
boolean resolvesToStaticField(NameExpr name) {
    try {
        var resolved = name.resolve();
        return resolved.isField() && resolved.asField().isStatic();
    } catch (RuntimeException ignored) {
        return false;
    }
}
```

Das Problem: `StackOverflowError` ist kein `RuntimeException`, sondern ein `Error`.

Damit wird der Fehler nicht abgefangen und beendet den Maven-Prozess.

### Verstärkender Faktor

Die aktuelle Reihenfolge in beiden Normalizern ist ungünstig:

```java
if (resolvesToStaticField(name) || isLikelyStaticField(name, name.getNameAsString(), localVariables)) {
    ...
}
```

und sinngemäß auch für Instance Fields.

Damit wird der teure und potenziell instabile Symbol Solver zuerst aufgerufen, obwohl viele Fälle lokal und AST-basiert entschieden werden könnten.

Für ein großes Projekt wie WildFly ist das riskant, weil `name.resolve()` durch komplexe Typ- und Vererbungshierarchien laufen kann.

### Warum der Fehler durch den aktuellen Umbau sichtbar wird

Der aktuelle Umbau versucht, Bedingungen besser für Byteman zu normalisieren:

```text
INSTANCE == null
 -> $CLASS.INSTANCE == null
```

Dafür wurde `StaticFieldQualifier` ergänzt. Diese Klasse ruft jetzt bei jedem passenden `NameExpr` ebenfalls `name.resolve()` auf.

Dadurch erhöht sich die Anzahl der Symbol-Solver-Aufrufe deutlich. In kleinen Unit Tests funktioniert das, bei WildFly triggert es eine rekursive JavaParser-Solver-Kante.

---

## Architekturentscheidung

Der Scanner muss resilient gegenüber JavaParser Symbol-Solver-Fehlern sein.

Eine einzelne nicht auflösbare oder solver-instabile Expression darf niemals den gesamten BTM-Generierungslauf abbrechen.

Richtige Regel:

```text
JavaParser symbol resolution is optional enrichment.
AST scanning must continue when symbol resolution fails.
```

Das bedeutet:

```text
symbol solver success
  -> use resolved information

symbol solver failure / StackOverflowError
  -> fall back to deterministic AST-local heuristics
  -> optionally record diagnostic
  -> continue scan
```

Nicht akzeptabel:

```text
name.resolve()
  -> StackOverflowError
  -> Maven build dies
```

---

## Sofort-Fix vor AST Context Propagation

Dieser Fehler muss vor der weiteren AST-Context-Propagation stabilisiert werden.

Grund:

Die geplante Context Propagation wird tendenziell noch mehr Resolver-Informationen nutzen. Ohne robuste Resolver-Grenzen würde der Umbau weitere WildFly-Abbrüche erzeugen.

---

# Umsetzung in Slices

## Slice 1 — Regressionstest für Solver-Fehler hinzufügen

### Ziel

Der Fehler muss testbar werden: Ein Symbol-Solver-Fehler darf den Scanner oder Renderer nicht abbrechen.

### Testfälle

#### 1. StaticFieldQualifier fängt StackOverflowError ab

Ziel:

```text
resolvesToStaticField(NameExpr)
 -> resolver throws StackOverflowError
 -> method returns false
 -> no crash
```

Wenn ein direkter Mock von `NameExpr.resolve()` schwer ist, dann den Test über einen kleinen synthetischen Source-Scan bauen.

#### 2. InstanceFieldNormalizer fängt StackOverflowError ab

Ziel:

```text
resolvesToInstanceField(NameExpr)
 -> resolver throws StackOverflowError
 -> method returns false
 -> no crash
```

#### 3. JavaParserScanner bleibt resilient

Mini-Projekt mit problematischer oder zyklischer Typstruktur:

```java
package example;

class A extends B {
    int a;
}

class B extends A {
    int b;
}

class Sample extends A {
    void run() {
        if (a > 0) {
        }
    }
}
```

Erwartung:

```java
assertDoesNotThrow(() -> scanner.scan(tempDir).toList());
```

Der Test muss nicht beweisen, dass jede Rule erzeugt wird. Er muss beweisen, dass der Scanner nicht den Build beendet.

### Akzeptanzkriterien

* Mindestens ein Test bildet `StackOverflowError` oder eine solver-rekursive Struktur ab.
* Der Test läuft vor dem Fix rot oder würde ohne den Fix den Lauf abbrechen.
* Nach dem Fix läuft der Test grün.

---

## Slice 2 — Symbol-Solver-Aufrufe absichern

### Ziel

`name.resolve()` darf nicht mehr ungeschützt aufgerufen werden.

### Änderung in `StaticFieldQualifier`

Aktuell:

```java
boolean resolvesToStaticField(NameExpr name) {
    try {
        var resolved = name.resolve();
        return resolved.isField() && resolved.asField().isStatic();
    } catch (RuntimeException ignored) {
        return false;
    }
}
```

Ziel:

```java
boolean resolvesToStaticField(NameExpr name) {
    try {
        var resolved = name.resolve();
        return resolved.isField() && resolved.asField().isStatic();
    } catch (StackOverflowError | RuntimeException ignored) {
        return false;
    }
}
```

### Änderung in `InstanceFieldNormalizer`

Aktuell:

```java
boolean resolvesToInstanceField(NameExpr name) {
    try {
        var resolved = name.resolve();
        return resolved.isField() && !resolved.asField().isStatic();
    } catch (RuntimeException ignored) {
        return false;
    }
}
```

Ziel:

```java
boolean resolvesToInstanceField(NameExpr name) {
    try {
        var resolved = name.resolve();
        return resolved.isField() && !resolved.asField().isStatic();
    } catch (StackOverflowError | RuntimeException ignored) {
        return false;
    }
}
```

### Wichtige Einschränkung

Nicht pauschal `Throwable` fangen.

Nicht fangen:

```text
OutOfMemoryError
ThreadDeath
VirtualMachineError allgemein
```

`StackOverflowError` wird gezielt gefangen, weil JavaParser bei komplexen Typgraphen rekursiv scheitern kann und der Scanner trotzdem weiterlaufen muss.

### Akzeptanzkriterien

* `StackOverflowError` aus JavaParser Symbol Resolution beendet den Scan nicht mehr.
* Resolver-Ausfall führt nur dazu, dass der konkrete Name nicht per Symbol Solver klassifiziert wird.
* Bestehende Unit Tests bleiben grün.

---

## Slice 3 — AST-lokale Heuristik vor Symbol Solver ausführen

### Ziel

Der Symbol Solver soll nicht mehr der erste Pfad sein.

Aktuell:

```java
if (resolvesToStaticField(name) || isLikelyStaticField(name, name.getNameAsString(), localVariables)) {
    ...
}
```

Ziel:

```java
if (isLikelyStaticField(name, name.getNameAsString(), localVariables) || resolvesToStaticField(name)) {
    ...
}
```

Analog für Instance Fields:

```java
if (isLikelyInstanceField(name, name.getNameAsString(), localVariables) || resolvesToInstanceField(name)) {
    ...
}
```

### Begründung

AST-lokale Informationen sind:

* deterministischer,
* schneller,
* frei von Classpath-Problemen,
* frei von rekursiver Type-Solver-Auflösung.

Der Symbol Solver bleibt nur Ergänzung für Fälle, die lokal nicht erkennbar sind.

### Akzeptanzkriterien

* Lokale Felder werden weiterhin erkannt.
* Statische Felder werden weiterhin erkannt.
* Resolver-Aufrufe sinken deutlich.
* WildFly-Scan hat weniger Risiko für rekursive Solver-Pfade.

---

## Slice 4 — Scanner-Level Safety Net ergänzen

### Ziel

Auch wenn später an anderer Stelle wieder ein `StackOverflowError` aus JavaParser entsteht, darf nicht der komplette Build sterben.

Aktuelle Stelle:

```java
try {
    CompilationUnit cu = StaticJavaParser.parse(file);
    ...
} catch (IOException | RuntimeException ignored) {
    // Ignore parsing issues to keep scanning resilient.
}
```

Ziel:

```java
try {
    CompilationUnit cu = StaticJavaParser.parse(file);
    ...
} catch (IOException | RuntimeException | StackOverflowError ignored) {
    // Ignore parsing and symbol-resolution issues to keep scanning resilient.
}
```

### Wichtig

Dieser Catch ist nur ein Safety Net. Der eigentliche Fix gehört an die Resolver-Grenze in `InstanceFieldNormalizer` und `StaticFieldQualifier`.

### Akzeptanzkriterien

* Eine einzelne kaputte Datei oder eine JavaParser-Solver-Rekursion bricht nicht mehr den gesamten Scan ab.
* Der Scanner überspringt problematische Dateien und fährt fort.
* Optional: später Diagnostic/Warnung erfassen, aber nicht in diesem Sofort-Fix erzwingen.

---

## Slice 5 — Optionalen Resolver Guard extrahieren

### Ziel

Wenn die Resolver-Sicherheit an mehreren Stellen benötigt wird, nicht überall eigene try/catch-Blöcke kopieren.

Mögliche Klasse:

```java
final class JavaParserResolutionGuard {

   private JavaParserResolutionGuard() {
   }

   static Optional<ResolvedValueDeclaration> resolveValue(NameExpr name) {
      try {
         return Optional.of(name.resolve());
      } catch (StackOverflowError | RuntimeException ignored) {
         return Optional.empty();
      }
   }
}
```

Hinweis:

Den konkreten Rückgabetyp an die tatsächlich verwendete JavaParser API anpassen.

### Akzeptanzkriterien

* `InstanceFieldNormalizer` und `StaticFieldQualifier` verwenden dieselbe Resolver-Grenze.
* Kommentare im Source-Code sind auf Englisch.
* Kein Catch von `Throwable`.
* Keine fachliche Logik im Guard, nur Schutz der externen Solver-API.

---

## Slice 6 — Performance-/Stabilitätsprüfung auf WildFly

### Ziel

Der Fix muss am realen Problemprojekt geprüft werden.

### Reproduktionskommando

```powershell
PS D:\Projects\wildfly> .\mvnw.cmd -N generate-test-resources -Dforensics "-Dforensics.sourceRoot=$root" "-Dforensics.excludePackages=org.jboss.as.test,org.wildfly.test" -DskipTests
```

### Erwartung nach Fix

Nicht mehr:

```text
Exception in thread "main" java.lang.StackOverflowError
```

Sondern:

```text
[INFO] Starting rule generation ...
[INFO] Finished rule generation ...
```

oder ein kontrollierter Plugin-Fehler mit eigener Fehlermeldung, aber kein roher JVM-StackOverflow aus JavaParser.

### Zusatzprüfung

Die erzeugte BTM-Datei prüfen auf:

```text
.forensics/build/btm/*.btm
```

Insbesondere sicherstellen:

* Entry-/Exit-Rules werden weiterhin erzeugt.
* IF-Rules werden weiterhin erzeugt.
* `$this.`-Qualifizierung für Instance Fields bleibt erhalten.
* `$CLASS.`-Qualifizierung für static fields bleibt erhalten.
* Keine neue globale Symbol-Suppression wurde eingeführt.

---

# Minimaler Codex-Auftrag

````md
## Task

Fix the JavaParser Symbol Solver StackOverflowError during large project scans.

## Problem

WildFly scan fails with:

```text
Exception in thread "main" java.lang.StackOverflowError
    at com.github.javaparser.ast.expr.Name.asString(Name.java:117)
    at com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration.getAllFields(...)
````

The current scanner calls `NameExpr.resolve()` in:

```text
InstanceFieldNormalizer.resolvesToInstanceField
StaticFieldQualifier.resolvesToStaticField
```

Both methods catch only `RuntimeException`, but JavaParser may throw `StackOverflowError` while resolving complex type hierarchies.

## Requirements

1. Add regression tests proving that Symbol Solver failures do not crash rendering/scanning.
2. Catch `StackOverflowError` explicitly around JavaParser `name.resolve()` calls.
3. Do not catch `Throwable` broadly.
4. Reorder field detection so AST-local heuristics run before Symbol Solver resolution:

```java
isLikelyStaticField(...) || resolvesToStaticField(...)
isLikelyInstanceField(...) || resolvesToInstanceField(...)
```

5. Add scanner-level safety net in `JavaParserScanner.visitFile` for `StackOverflowError`.
6. Preserve existing behavior for `$this.` instance field qualification and `$CLASS.` static field qualification.
7. Do not introduce global symbol suppression.
8. Do not move type resolution into the Byteman renderer.
9. Source-code comments must be written in English.

## Files to inspect/change

```text
src/main/java/de/burger/forensics/adaptersupport/javaparser/InstanceFieldNormalizer.java
src/main/java/de/burger/forensics/adaptersupport/javaparser/StaticFieldQualifier.java
src/main/java/de/burger/forensics/adapters/javaparser/JavaParserScanner.java
src/test/java/de/burger/forensics/adaptersupport/javaparser/DefaultConditionRenderingStrategyTest.java
src/test/java/de/burger/forensics/adaptersupport/javaparser/InstanceFieldNormalizerTest.java
src/test/java/de/burger/forensics/adapters/javaparser/JavaParserScannerTest.java
```

## Verification

Run:

```bash
./gradlew clean test
./gradlew check
```

Then verify against WildFly with the Maven command that produced the StackOverflowError.

```

---

# Nicht-Ziele

Diese Punkte sind nicht Bestandteil dieses Fehlerfixes:

- vollständige AST Context Propagation
- ImportTable-Modellierung
- Wildcard-Import-Auflösung
- grouped unresolved-symbol report
- globale Allowlist
- globale Symbol-Deduplizierung
- Umstellung auf einen anderen Parser
- nachträgliches Reparieren fertiger BTM-Regeln

Diese Themen bleiben wichtig, aber zuerst muss der Scanner stabil werden.

---

# Abschlusskriterien

Der Fehler gilt als behoben, wenn:

1. `StackOverflowError` aus JavaParser Symbol Resolution den Build nicht mehr beendet.
2. Unit Tests den Resolver-Fehlerfall absichern.
3. Der WildFly-Lauf mindestens über die bisherige Abbruchstelle hinausläuft.
4. `$this.`- und `$CLASS.`-Normalisierung weiterhin funktionieren.
5. Keine pauschale Suppression nach Symbolname eingebaut wurde.
6. Der Fix vor der geplanten AST Context Propagation erfolgt.
7. Der BTM-Renderer weiterhin nur rendert und keine AST-Kontext-Rekonstruktion übernimmt.

```
