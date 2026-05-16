Migration Epic: Auslagern der Analyse in den Dienst forensic_analytics
Hintergrund
Das bisherige forensics_tracing‑Plugin enthält Scanner‑, Analyse‑ und Rule‑Generation‑Logik in einem monolithischen Gradle‑/Maven‑Plugin. Der Build‑Task generateBtmRules durchsucht lokal den Quellcode, extrahiert Informationen über Kontrollflüsse und generiert Byteman‑Regeln sowie einen Analyse‑Store (H2‑Datenbank). Diese Funktionalität soll in den neuen Dienst forensic_analytics ausgelagert werden. Die Entscheidung dient der Entkopplung, einer besseren Skalierbarkeit und der Möglichkeit, Analysen zentral zu orchestrieren. Gleichzeitig soll das Runtime‑Logging (z. B. via RtTrace) in near‑realtime an den Server gestreamt werden, damit dieser die Ereignisse sofort auswerten kann.
Zielsetzung und Scope
Dieses Epic beschreibt die Migration der Scan‑ und Analyse‑Komponenten aus dem Plugin in den Dienst forensic_analytics und definiert die Schnittstellen zwischen beiden Systemen.
Kernziele:
    1. Remote‑Analyse: Der Server übernimmt das komplette Scannen des Quellcodes, die semantische Analyse und die Generierung der .btm‑Dateien. Das Plugin übermittelt nur Repository‑Informationen und Konfigurationsparameter.
    2. Asynchrone Ergebnisauslieferung: Die generierten Byteman‑Skripte werden als Datenstrom über gRPC vom Server zum Plugin zurückgesendet. Eine gRPC‑Server‑Streaming‑Methode liefert mehrere BtmFileChunk‑Nachrichten, bis alle Artefakte übertragen sind; der gRPC‑Standard definiert einen solchen serverseitigen Stream mit dem Schlüsselwort stream vor dem Rückgabetyp[1].
    3. Near‑Realtime‑Logging: Das Runtime‑Logging (JSON‑Zeilen aus RtTrace bzw. Tracer) wird über eine gRPC‑Client‑Streaming‑Verbindung an den Dienst gesendet, sodass dieser aktuelle Systemereignisse beobachten kann.
    4. REST‑Fassade für Steuerbefehle: Die Interaktion zur Initiierung der Analyse und zur Abfrage des Job‑Status erfolgt über REST‑Endpunkte; REST kontaktiert intern die gRPC‑Services, wie im Open‑Liberty‑Beispiel beschrieben[2].
Kommunikationsschnittstellen
REST‑Endpunkte
HTTP Verb
Endpoint
Beschreibung
POST
/analysis/start
Startet einen Analysejob. Der Request‑Body enthält das Repository (URL, Branch, Commit‑SHA) sowie Scanner‑Parameter (z. B. Include/Exclude‑Listen, minimale Branch‑Anzahl). Die Antwort liefert eine jobId.
GET
/analysis/status/{jobId}
Liefert den aktuellen Status (IN_PROGRESS, FAILED, FINISHED) und optional Fortschritt.
GET
/analysis/result/{jobId}
Optionaler REST‑Download der Ergebnisse (z. B. manifest.json); primär erfolgt die Übermittlung per gRPC.
gRPC‑Service (Protobuf‑Auszug)
syntax = "proto3";
package analytics.v1;

message StartAnalysisRequest {
  string repo_url = 1;
  string branch   = 2;
  map<string,string> settings = 3;
}

message StartAnalysisResponse { string job_id = 1; }

message JobStatusRequest { string job_id = 1; }
message JobStatusResponse {
  enum Status { UNKNOWN = 0; IN_PROGRESS = 1; FINISHED = 2; FAILED = 3; }
  Status status = 1;
  string message = 2;
}

message JobRequest { string job_id = 1; }
message BtmFileChunk {
  string file_name = 1;
  bytes data      = 2;
  bool last_chunk = 3;
}

message LogEntry {
  string job_id    = 1;
  string timestamp = 2;
  string level     = 3;
  string message   = 4;
}

service AnalysisControlService {
  rpc Start(StartAnalysisRequest) returns (StartAnalysisResponse);
  rpc Status(JobStatusRequest)   returns (JobStatusResponse);
}

service AnalysisResultService {
  // Server‑seitiger Stream: Client sendet Job‑Id, Server liefert viele Chunk‑Nachrichten[1].
  rpc GetBtmFiles(JobRequest) returns (stream BtmFileChunk);
  // Client‑seitiger Stream für near‑realtime‑Logs.
  rpc SendLogs(stream LogEntry) returns (google.protobuf.Empty);
}
Die REST‑Controller von forensic_analytics delegieren die Anfragen an diese gRPC‑Services. Wie in der Open‑Liberty‑Referenz gezeigt, können unterschiedliche Streaming‑Arten (unary, server streaming, client streaming, bidirectional) hinter HTTP‑Endpunkten versteckt werden[2].
Ablauf aus Sicht des Build‑Plugins
    1. Konfiguration: Der Anwender trägt seine Scanner‑Parameter (Quellordner, Include/Exclude, minimale Branch‑Anzahl etc.) in der gewohnten Gradle‑Extension btmGen oder Maven‑Konfiguration ein. Diese Parameter werden in ein JSON‑Request‑Objekt umgewandelt.
    2. Analyse starten: Der neue Task remoteGenerateBtmRules sendet einen HTTP‑POST an /analysis/start mit Repository‑URL (z. B. Git‑Remote), Branch‑Name und Konfiguration. Die Antwort enthält jobId.
    3. Status polling: Während der Analyse ruft das Plugin regelmäßig /analysis/status/{jobId} auf, um Fortschritt und Fehler abzufragen. Bei aktivierter Debug‑Option initialisiert das Plugin zusätzlich einen gRPC‑Client‑Stream (SendLogs) und übergibt den gRPC‑Stub an die Laufzeitbibliothek RtTrace/Tracer. Diese senden Log‑Einträge in Echtzeit.
    4. Ergebnisse empfangen: Sobald der Server FINISHED meldet, ruft das Plugin die gRPC‑Methode GetBtmFiles auf. Ein StreamObserver<BtmFileChunk> sammelt die Byte‑Chunks und schreibt sie in lokale Dateien (forensics.btm, manifest.json, checksums.sha256 usw.). Der Stream endet, wenn last_chunk = true.
    5. Task abschließen: Nach erfolgreichem Download werden die generierten Regeln im Build‑Verzeichnis bereitgestellt und der Task beendet sich erfolgreich. Bei Fehlern (HTTP‑4xx/5xx, gRPC‑Fehler) schlägt der Task fehl.
Implementierungsaufgaben
    1. Anpassung des Plugins
    2. Implementierung eines REST‑Clients (z. B. mithilfe von OkHttp) zur Kommunikation mit /analysis/start und /analysis/status.
    3. Erzeugung eines gRPC‑Kanals und eines stubs (AnalysisResultServiceStub) zur Nutzung von GetBtmFiles und SendLogs.
    4. Aktualisierung der Build‑Scripts: neuer Task remoteGenerateBtmRules, Übergabe der Plugin‑Konfiguration im Request‑Body, Ausgabeort für heruntergeladene Dateien.
    5. Server‑Implementierung
    6. Erweiterung von forensic_analytics um die gRPC‑Services aus dem oben stehenden Protobuf.
    7. Implementierung eines REST‑Controllers, der die gRPC‑Services aufruft. Der Controller handelt die gängigen HTTP‑Response‑Codes (202 bei laufenden Jobs, 500 bei Fehlern).
    8. Integration eines Job‑Schedulers, der das Repository auscheckt, die Scanner‑Logik aus forensics_tracing aufruft und die Ergebnisse in .btm‑Dateien serialisiert.
    9. Bereitstellung einer Streaming‑Schnittstelle für Log‑Einträge, die intern in die Analyse‑Datenbank geschrieben oder visualisiert werden.
    10. Tests und Migration
    11. Erstellung von Integrationstests für REST‑Endpunkte und gRPC‑Streams (z. B. mithilfe von grpc-java-testing).
    12. Backwards‑Kompatibilität: Für einen Übergangszeitraum kann das ursprüngliche lokale Analyse‑Verhalten via Flag reaktiviert werden.
    13. Dokumentation und Beispielanwendungen (Updates der README und AGENTS.md).
Offene Punkte
    • Authentifizierung & Sicherheit: Die REST‑ und gRPC‑Schnittstellen müssen abgesichert werden (z. B. mittels Bearer‑Token oder Mutual TLS). Die Übertragung von Quellcode sollte verschlüsselt erfolgen.
    • Skalierung: Der Server muss mehrere parallele Jobs verarbeiten können; ggf. Bedarf an Queueing (z. B. RabbitMQ) und Horizontal‑Scaling.
    • Fehlerbehandlung: Bei Verbindungsabbrüchen sollte der gRPC‑Stream fortsetzbar sein (Resumption). REST‑Antworten sollten klare Fehlermeldungen liefern.
    • Semantische Graphen: Optionales Feature, um Joern‑Graphen zu generieren und an den Server zu übertragen. Dies muss ebenfalls in die API integriert werden.

Dieser Entwurf bildet die Grundlage für die weitere Ausarbeitung und Umsetzung der Migration. Die beschriebenen Kommunikationspfade stützen sich auf Standard‑gRPC‑ und REST‑Konzepte. Serverseitige Streaming‑RPCs liefern eine Folge von Nachrichten an den Client[1], während REST‑Controller diese Streams hinter HTTP‑Endpunkten kapseln können[2]. Weitere Iterationen sollen die Details (z. B. Fehlercodes, Authentifizierung, konkrete Datenmodelle) präzisieren.

[1] Basics tutorial | Java | gRPC
https://grpc.io/docs/languages/java/basics/
[2]  Streaming messages between client and server services using gRPC remote procedure calls 
https://openliberty.io/guides/grpc-intro.htmlMigration Epic: Auslagern der Analyse in den Dienst forensic_analytics
Hintergrund
Das bisherige forensics_tracing‑Plugin enthält Scanner‑, Analyse‑ und Rule‑Generation‑Logik in einem monolithischen Gradle‑/Maven‑Plugin. Der Build‑Task generateBtmRules durchsucht lokal den Quellcode, extrahiert Informationen über Kontrollflüsse und generiert Byteman‑Regeln sowie einen Analyse‑Store (H2‑Datenbank). Diese Funktionalität soll in den neuen Dienst forensic_analytics ausgelagert werden. Die Entscheidung dient der Entkopplung, einer besseren Skalierbarkeit und der Möglichkeit, Analysen zentral zu orchestrieren. Gleichzeitig soll das Runtime‑Logging (z. B. via RtTrace) in near‑realtime an den Server gestreamt werden, damit dieser die Ereignisse sofort auswerten kann.
Zielsetzung und Scope
Dieses Epic beschreibt die Migration der Scan‑ und Analyse‑Komponenten aus dem Plugin in den Dienst forensic_analytics und definiert die Schnittstellen zwischen beiden Systemen.
Kernziele:
    1. Remote‑Analyse: Der Server übernimmt das komplette Scannen des Quellcodes, die semantische Analyse und die Generierung der .btm‑Dateien. Das Plugin übermittelt nur Repository‑Informationen und Konfigurationsparameter.
    2. Asynchrone Ergebnisauslieferung: Die generierten Byteman‑Skripte werden als Datenstrom über gRPC vom Server zum Plugin zurückgesendet. Eine gRPC‑Server‑Streaming‑Methode liefert mehrere BtmFileChunk‑Nachrichten, bis alle Artefakte übertragen sind; der gRPC‑Standard definiert einen solchen serverseitigen Stream mit dem Schlüsselwort stream vor dem Rückgabetyp[1].
    3. Near‑Realtime‑Logging: Das Runtime‑Logging (JSON‑Zeilen aus RtTrace bzw. Tracer) wird über eine gRPC‑Client‑Streaming‑Verbindung an den Dienst gesendet, sodass dieser aktuelle Systemereignisse beobachten kann.
    4. REST‑Fassade für Steuerbefehle: Die Interaktion zur Initiierung der Analyse und zur Abfrage des Job‑Status erfolgt über REST‑Endpunkte; REST kontaktiert intern die gRPC‑Services, wie im Open‑Liberty‑Beispiel beschrieben[2].
Kommunikationsschnittstellen
REST‑Endpunkte
HTTP Verb
Endpoint
Beschreibung
POST
/analysis/start
Startet einen Analysejob. Der Request‑Body enthält das Repository (URL, Branch, Commit‑SHA) sowie Scanner‑Parameter (z. B. Include/Exclude‑Listen, minimale Branch‑Anzahl). Die Antwort liefert eine jobId.
GET
/analysis/status/{jobId}
Liefert den aktuellen Status (IN_PROGRESS, FAILED, FINISHED) und optional Fortschritt.
GET
/analysis/result/{jobId}
Optionaler REST‑Download der Ergebnisse (z. B. manifest.json); primär erfolgt die Übermittlung per gRPC.
gRPC‑Service (Protobuf‑Auszug)
syntax = "proto3";
package analytics.v1;

message StartAnalysisRequest {
  string repo_url = 1;
  string branch   = 2;
  map<string,string> settings = 3;
}

message StartAnalysisResponse { string job_id = 1; }

message JobStatusRequest { string job_id = 1; }
message JobStatusResponse {
  enum Status { UNKNOWN = 0; IN_PROGRESS = 1; FINISHED = 2; FAILED = 3; }
  Status status = 1;
  string message = 2;
}

message JobRequest { string job_id = 1; }
message BtmFileChunk {
  string file_name = 1;
  bytes data      = 2;
  bool last_chunk = 3;
}

message LogEntry {
  string job_id    = 1;
  string timestamp = 2;
  string level     = 3;
  string message   = 4;
}

service AnalysisControlService {
  rpc Start(StartAnalysisRequest) returns (StartAnalysisResponse);
  rpc Status(JobStatusRequest)   returns (JobStatusResponse);
}

service AnalysisResultService {
  // Server‑seitiger Stream: Client sendet Job‑Id, Server liefert viele Chunk‑Nachrichten[1].
  rpc GetBtmFiles(JobRequest) returns (stream BtmFileChunk);
  // Client‑seitiger Stream für near‑realtime‑Logs.
  rpc SendLogs(stream LogEntry) returns (google.protobuf.Empty);
}
Die REST‑Controller von forensic_analytics delegieren die Anfragen an diese gRPC‑Services. Wie in der Open‑Liberty‑Referenz gezeigt, können unterschiedliche Streaming‑Arten (unary, server streaming, client streaming, bidirectional) hinter HTTP‑Endpunkten versteckt werden[2].
Ablauf aus Sicht des Build‑Plugins
    1. Konfiguration: Der Anwender trägt seine Scanner‑Parameter (Quellordner, Include/Exclude, minimale Branch‑Anzahl etc.) in der gewohnten Gradle‑Extension btmGen oder Maven‑Konfiguration ein. Diese Parameter werden in ein JSON‑Request‑Objekt umgewandelt.
    2. Analyse starten: Der neue Task remoteGenerateBtmRules sendet einen HTTP‑POST an /analysis/start mit Repository‑URL (z. B. Git‑Remote), Branch‑Name und Konfiguration. Die Antwort enthält jobId.
    3. Status polling: Während der Analyse ruft das Plugin regelmäßig /analysis/status/{jobId} auf, um Fortschritt und Fehler abzufragen. Bei aktivierter Debug‑Option initialisiert das Plugin zusätzlich einen gRPC‑Client‑Stream (SendLogs) und übergibt den gRPC‑Stub an die Laufzeitbibliothek RtTrace/Tracer. Diese senden Log‑Einträge in Echtzeit.
    4. Ergebnisse empfangen: Sobald der Server FINISHED meldet, ruft das Plugin die gRPC‑Methode GetBtmFiles auf. Ein StreamObserver<BtmFileChunk> sammelt die Byte‑Chunks und schreibt sie in lokale Dateien (forensics.btm, manifest.json, checksums.sha256 usw.). Der Stream endet, wenn last_chunk = true.
    5. Task abschließen: Nach erfolgreichem Download werden die generierten Regeln im Build‑Verzeichnis bereitgestellt und der Task beendet sich erfolgreich. Bei Fehlern (HTTP‑4xx/5xx, gRPC‑Fehler) schlägt der Task fehl.
Implementierungsaufgaben
    1. Anpassung des Plugins
    2. Implementierung eines REST‑Clients (z. B. mithilfe von OkHttp) zur Kommunikation mit /analysis/start und /analysis/status.
    3. Erzeugung eines gRPC‑Kanals und eines stubs (AnalysisResultServiceStub) zur Nutzung von GetBtmFiles und SendLogs.
    4. Aktualisierung der Build‑Scripts: neuer Task remoteGenerateBtmRules, Übergabe der Plugin‑Konfiguration im Request‑Body, Ausgabeort für heruntergeladene Dateien.
    5. Server‑Implementierung
    6. Erweiterung von forensic_analytics um die gRPC‑Services aus dem oben stehenden Protobuf.
    7. Implementierung eines REST‑Controllers, der die gRPC‑Services aufruft. Der Controller handelt die gängigen HTTP‑Response‑Codes (202 bei laufenden Jobs, 500 bei Fehlern).
    8. Integration eines Job‑Schedulers, der das Repository auscheckt, die Scanner‑Logik aus forensics_tracing aufruft und die Ergebnisse in .btm‑Dateien serialisiert.
    9. Bereitstellung einer Streaming‑Schnittstelle für Log‑Einträge, die intern in die Analyse‑Datenbank geschrieben oder visualisiert werden.
    10. Tests und Migration
    11. Erstellung von Integrationstests für REST‑Endpunkte und gRPC‑Streams (z. B. mithilfe von grpc-java-testing).
    12. Backwards‑Kompatibilität: Für einen Übergangszeitraum kann das ursprüngliche lokale Analyse‑Verhalten via Flag reaktiviert werden.
    13. Dokumentation und Beispielanwendungen (Updates der README und AGENTS.md).
Offene Punkte
    • Authentifizierung & Sicherheit: Die REST‑ und gRPC‑Schnittstellen müssen abgesichert werden (z. B. mittels Bearer‑Token oder Mutual TLS). Die Übertragung von Quellcode sollte verschlüsselt erfolgen.
    • Skalierung: Der Server muss mehrere parallele Jobs verarbeiten können; ggf. Bedarf an Queueing (z. B. RabbitMQ) und Horizontal‑Scaling.
    • Fehlerbehandlung: Bei Verbindungsabbrüchen sollte der gRPC‑Stream fortsetzbar sein (Resumption). REST‑Antworten sollten klare Fehlermeldungen liefern.
    • Semantische Graphen: Optionales Feature, um Joern‑Graphen zu generieren und an den Server zu übertragen. Dies muss ebenfalls in die API integriert werden.

Dieser Entwurf bildet die Grundlage für die weitere Ausarbeitung und Umsetzung der Migration. Die beschriebenen Kommunikationspfade stützen sich auf Standard‑gRPC‑ und REST‑Konzepte. Serverseitige Streaming‑RPCs liefern eine Folge von Nachrichten an den Client[1], während REST‑Controller diese Streams hinter HTTP‑Endpunkten kapseln können[2]. Weitere Iterationen sollen die Details (z. B. Fehlercodes, Authentifizierung, konkrete Datenmodelle) präzisieren.

[1] Basics tutorial | Java | gRPC
https://grpc.io/docs/languages/java/basics/
[2]  Streaming messages between client and server services using gRPC remote procedure calls 
https://openliberty.io/guides/grpc-intro.html
