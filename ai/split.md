# Trennung von Domain-Logik und UI-Ausgabe (UI vs CLI)

Dieses Dokument beschreibt den Plan zur sauberen Trennung der Domain-Logik von der Benutzeroberfläche (CLI oder Web-Frontend).

## 1. Problemstellung
Aktuell ist der `UpdateReposProcess` und dessen Hilfsklasse `UpdateProgressPrinter` eng mit UI-Modellen (`ProgressUpdate`) und dem `StatisticPrinter` (der ursprünglich für die CLI gedacht war) verknüpft. Dies führt dazu, dass Änderungen an der UI-Anzeige oft Änderungen im Domain-Code erfordern.

## 2. Zielsetzung
- **Domain-Reinheit**: Der Domain-Code (`paxel.dedup.repo.domain`) soll keine Klassen aus dem UI- oder Terminal-Package kennen.
- **Injektion von Observern**: Der Fortschritt von Prozessen wird über ein abstraktes Interface gemeldet. Die konkrete Implementierung (CLI-Ausgabe oder Web-Event-Bus) wird von außen injiziert.
- **Wiederverwendbarkeit**: CLI-Klassen sollen stabil bleiben und nicht für UI-Zwecke "missbraucht" oder angepasst werden müssen.

## 3. Architektur-Änderungen

### 3.1 Domain-Interfaces
Einführung eines neuen Interfaces `UpdateObserver` (oder ähnlich) im Domain-Bereich:
```java
public interface UpdateObserver {
    void onDiscovery(Path path, long totalFiles, long totalDirs);
    void onScanFinished(long totalFiles, long totalDirs);
    void onHashing(Path path, long processed, long total);
    void onUnchanged(Path path, long processed, long total);
    void onDeleted(Path path, long processed, long total);
    void onFinished(Statistics stats);
    void onError(Path path, Throwable e);
}
```

### 3.2 Refactoring bestehender Klassen
1.  **`UpdateProgressPrinter`**: Diese Klasse wird zu einer reinen Domain-Klasse, die das `UpdateObserver`-Interface nutzt, um Fortschritte zu melden. Sie berechnet die Statistiken (ETA, Raten), hält aber keine Referenz mehr auf `StatisticPrinter` oder `ProgressUpdate`.
2.  **`UpdateReposProcess`**: Akzeptiert nun ein `UpdateObserver` statt intern einen `StatisticPrinter` zu konfigurieren.

### 3.3 Infrastruktur-Adapter
1.  **`TerminalUpdateObserver`**: Implementiert `UpdateObserver` und schreibt die Daten in den `StatisticPrinter` (für die CLI).
2.  **`WebUpdateObserver`**: Implementiert `UpdateObserver` und sendet `ProgressUpdate`-Events über den `EventBus` an die Web-UI.

## 4. Implementierungsschritte
1.  **Interface Definition**: Erstellen von `UpdateObserver` in `paxel.dedup.domain.model`.
2.  **Domain-Update**: Umstellung von `UpdateProgressPrinter` auf das neue Interface.
3.  **Prozess-Update**: `UpdateReposProcess` nutzt das Interface zur Statusmeldung.
4.  **CLI-Adapter**: Erstellen einer Brücke zwischen `UpdateObserver` und `StatisticPrinter`.
5.  **Web-Adapter**: Erstellen einer Brücke zwischen `UpdateObserver` und `EventBus`.
6.  **Dependency Injection**: In `UiServer` wird der `WebUpdateObserver` injiziert, im CLI-Einstiegspunkt der `TerminalUpdateObserver`.

## 5. Vorteile
- CLI-Ausgabe bleibt unberührt bei UI-Änderungen.
- Einfaches Testen durch Mocking des `UpdateObserver`.
- Saubere Hexagonale Architektur.
