package paxel.dedup.infrastructure.adapter.out.terminal;

import lombok.RequiredArgsConstructor;
import paxel.dedup.domain.model.ProgressUpdate;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.UpdateObserver;
import paxel.dedup.terminal.StatisticPrinter;

import java.nio.file.Path;

@RequiredArgsConstructor
public class TerminalUpdateObserver implements UpdateObserver {

    private final String repoName;
    private final String absolutePath;
    private final StatisticPrinter progressPrinter;

    private long filesTotal = 0;
    private long directoriesTotal = 0;
    private long filesProcessed = 0;
    private boolean scanFinished = false;

    @Override
    public void onDiscovery(Path path, long totalFiles, long totalDirs) {
        this.filesTotal = totalFiles;
        this.directoriesTotal = totalDirs;
        progressPrinter.update(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .scanningActive(true)
                .hashingActive(false)
                .filesDiscovered(totalFiles)
                .directoriesDiscovered(totalDirs)
                .filesProcessed(totalFiles) // For CLI we often show total as processed during scan
                .filesTotal(totalFiles)
                .directoriesProcessed(0L)
                .directoriesTotal(totalDirs)
                .build());
    }

    @Override
    public void onScanFinished(long totalFiles, long totalDirs) {
        this.scanFinished = true;
        this.filesTotal = totalFiles;
        this.directoriesTotal = totalDirs;
        progressPrinter.update(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .scanningActive(false)
                .hashingActive(true)
                .filesProcessed(filesProcessed)
                .filesTotal(totalFiles)
                .directoriesProcessed(totalDirs)
                .directoriesTotal(totalDirs)
                .build());
    }

    @Override
    public void onHashing(Path path, long processed, long total, boolean scanningActive) {
        this.filesProcessed = processed;
        this.filesTotal = total;
        updateProgress(path, processed, total, scanningActive);
    }

    @Override
    public void onUnchanged(Path path, long processed, long total, boolean scanningActive) {
        this.filesProcessed = processed;
        this.filesTotal = total;
        updateProgress(path, processed, total, scanningActive);
    }

    @Override
    public void onDeleted(Path path, long processed, long total) {
        progressPrinter.update(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .deletedProcessed(processed)
                .deletedTotal(total)
                .scanningActive(false)
                .hashingActive(false)
                .build());
    }

    @Override
    public void onFinished(Statistics stats) {
        progressPrinter.update(ProgressUpdate.builder()
                .repo(repoName)
                .scanningActive(false)
                .hashingActive(false)
                .build());
        progressPrinter.finish();
    }

    @Override
    public void onError(Path path, Throwable e) {
        progressPrinter.setErrors(e.getMessage());
    }

    private void updateProgress(Path path, long processed, long total, boolean scanningActive) {
        if (scanningActive || total > this.filesTotal) {
            this.filesTotal = total;
        }
        long currentTotal = this.filesTotal;
        double percent = currentTotal > 0 ? (double) processed / currentTotal * 100 : 0;
        if (scanningActive && percent >= 100.0) {
            percent = 99.0;
        }
        progressPrinter.update(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .filesProcessed(processed)
                .filesTotal(currentTotal)
                .directoriesProcessed(directoriesTotal)
                .directoriesTotal(directoriesTotal)
                .scanningActive(scanningActive)
                .hashingActive(processed < currentTotal)
                .progressPercent(percent)
                .build());
    }
}
