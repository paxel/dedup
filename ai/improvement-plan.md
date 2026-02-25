# 6-Schritte-Verbesserungsplan für dedup

Dieser Plan basiert auf einer umfassenden Analyse des Projekts und konzentriert sich auf Fehlerbehebung, Sicherheit, Benutzererfahrung (UX) und Code-Qualität.

## 1. Ressourcen-Management & Memory Leaks (Bugs / Performance)
**Dateien:** `Sha1Hasher.java`, `UpdateReposProcess.java`, `MimetypeProvider.java`
**Problem:** Der `Sha1Hasher` schließt seinen `ExecutorService` erst nach einem kompletten Scan. Bei Fehlern in `UpdateReposProcess` (z.B. in `updateRepo`) kann der `close()`-Aufruf im `finally`-Block zwar die Beendigung erzwingen, aber die Struktur ist nicht ideal für vorzeitige Abbrüche. Schwerwiegender ist, dass `MimetypeProvider` für **jede einzelne Datei** eine neue `Tika`-Instanz erzeugt. Tika lädt beim Instanziieren viele Detektoren und Konfigurationen, was bei tausenden Dateien zu massivem Overhead und Speicherverbrauch führt.
**Lösung:** 
- Implementierung von `AutoCloseable` für Prozess-Klassen, wo sinnvoll.
- `Tika` muss als Singleton oder wiederverwendbare Instanz im `MimetypeProvider` genutzt werden.

## 2. Sicherheit: Pfad-Validierung (Sicherheit)
**Dateien:** `DefaultDedupConfig.java`, `RepoCommand.java`
**Problem:** Bei der Erstellung von Repositories (`create`) oder beim Umbenennen wird der Name nicht ausreichend validiert. Ein Name wie `../../etc/passwd` könnte theoretisch dazu führen, dass Dateien außerhalb des vorgesehenen Konfigurationsverzeichnisses manipuliert werden.
**Lösung:** Implementierung einer strikten Validierung für Repository-Namen (z.B. nur alphanumerisch, keine Pfad-Separatoren).

## 3. Korrektur der Fortschrittsberechnung (UX)
**Dateien:** `UpdateProgressPrinter.java`, `BetterPrediction.java`
**Problem:** Die aktuelle ETA-Berechnung (`calcUpdate`) startet erst, wenn der Scan (`scanFinished`) abgeschlossen ist. Bei großen Repositorien sieht der Nutzer während des oft langwierigen Scans keine Zeitschätzung.
**Lösung:** Einführung einer "Scan-Fortschrittsanzeige" (Anzahl gefundener Dateien/Ordner) während des Scans. Die ETA-Berechnung wird erst gestartet, wenn eine solide Datenbasis vorhanden ist, um irreführende "Springende ETAs" zu vermeiden.

## 4. Konsistente Fehlerbehandlung (Error Handling)
**Dateien:** `DuplicateRepoProcess.java`, `PruneReposProcess.java`
**Problem:** Trotz der vorhandenen `Result<T, E>`-Klasse wird an vielen Stellen (z.B. in `dupe` oder `pruneAll`) direkt auf `System.out` geloggt oder `printStackTrace()` verwendet, während ein magischer Integer-exit-Code zurückgegeben wird. Dies vermischt Domänen-Logik mit Infrastruktur-Details (CLI-Output).
**Lösung:** Umstellung aller Kern-Prozesse auf die konsequente Rückgabe von `Result`. Zentrale Behandlung der Fehler in den `Command`-Klassen unter Nutzung des `ConsoleLogger`.

## 5. Refactoring "Primitive Obsession" & Filter (Bad Code)
**Dateien:** `FilterFactory.java`
**Problem:** Filterausdrücke (z.B. `mime:image/jpeg`) werden ad-hoc durch String-Parsing verarbeitet. Dies entspricht nicht den Regeln in `clanky.md` (Vermeidung von Primitive Obsession).
**Lösung:** Einführung eines `Filter`-Interfaces und dedizierter Implementierungen (z.B. `MimeFilter`, `SizeFilter`) anstatt der verschachtelten `if-else`-Logik in der Factory.

## 6. Modernisierung des manuellen Codecs (Code Quality)
**Dateien:** `MessagePackRepoFileCodec.java`, `RepoFile.java`
**Problem:** Der manuelle Codec für MessagePack wurde eingeführt, um Reflexion zu vermeiden. Aktuell fehlt jedoch die Serialisierung für das Feld `fingerprint`, was zu Datenverlust bei Bild-Fingerprints führt.
**Lösung:** Ergänzung des `MessagePackRepoFileCodec` um das `fingerprint`-Feld (Key "f"), analog zur JSON-Implementierung.
