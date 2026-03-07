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

    @Override
    public void onDiscovery(Path path, long totalFiles, long totalDirs) {
        this.filesTotal = totalFiles;
        this.directoriesTotal = totalDirs;
        progressPrinter.update(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .scanningActive(true)
                .filesDiscovered(totalFiles)
                .directoriesDiscovered(totalDirs)
                .filesProcessed(totalFiles) // For CLI we often show total as processed during scan
                .filesTotal(totalFiles)
                .directoriesProcessed(0L)
                .directoriesTotal(totalDirs)
                .status("Scanning...")
                .build());
    }

    @Override
    public void onScanFinished(long totalFiles, long totalDirs) {
        this.filesTotal = totalFiles;
        this.directoriesTotal = totalDirs;
        progressPrinter.update(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .scanningActive(false)
                .filesProcessed(filesProcessed)
                .filesTotal(totalFiles)
                .directoriesProcessed(totalDirs)
                .directoriesTotal(totalDirs)
                .status("Scan finished.")
                .build());
    }

    @Override
    public void onHashing(Path path, long processed, long total) {
        this.filesProcessed = processed;
        this.filesTotal = total;
        updateProgress(path, processed, total, "Hashing");
    }

    @Override
    public void onUnchanged(Path path, long processed, long total) {
        this.filesProcessed = processed;
        this.filesTotal = total;
        updateProgress(path, processed, total, "Unchanged");
    }

    @Override
    public void onDeleted(Path path, long processed, long total) {
        progressPrinter.update(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .deletedProcessed(processed)
                .deletedTotal(total)
                .status("Deleting")
                .build());
    }

    @Override
    public void onFinished(Statistics stats) {
        progressPrinter.finish();
    }

    @Override
    public void onError(Path path, Throwable e) {
        progressPrinter.setErrors(e.getMessage());
    }

    private void updateProgress(Path path, long processed, long total, String status) {
        progressPrinter.update(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .filesProcessed(processed)
                .filesTotal(total)
                .directoriesProcessed(directoriesTotal)
                .directoriesTotal(directoriesTotal)
                .status(status)
                .progressPercent(total > 0 ? (double) processed / total * 100 : 0)
                .build());
    }
}
