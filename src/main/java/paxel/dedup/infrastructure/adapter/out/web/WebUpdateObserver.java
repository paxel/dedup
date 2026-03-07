package paxel.dedup.infrastructure.adapter.out.web;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.time.DurationFormatUtils;
import paxel.dedup.domain.model.ProgressUpdate;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.UpdateObserver;
import paxel.dedup.domain.service.EventBus;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
public class WebUpdateObserver implements UpdateObserver {

    private final String repoName;
    private final String absolutePath;
    private final EventBus eventBus;
    private final Instant startTime = Instant.now();
    private final AtomicLong filesDiscovered = new AtomicLong(0);
    private final AtomicLong directoriesDiscovered = new AtomicLong(0);
    private final AtomicLong filesTotal = new AtomicLong(0);
    private final AtomicLong directoriesTotal = new AtomicLong(0);
    private final AtomicLong filesProcessed = new AtomicLong(0);
    private final AtomicLong deletedProcessed = new AtomicLong(0);
    private final AtomicLong deletedTotal = new AtomicLong(0);
    private final AtomicBoolean scanFinished = new AtomicBoolean(false);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void onDiscovery(Path path, long totalFiles, long totalDirs) {
        this.filesDiscovered.set(totalFiles);
        this.directoriesDiscovered.set(totalDirs);
        publish(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .scanningActive(true)
                .filesDiscovered(totalFiles)
                .directoriesDiscovered(totalDirs)
                .duration(formatDuration(Duration.between(startTime, Instant.now())))
                .build());
    }

    @Override
    public void onScanFinished(long totalFiles, long totalDirs) {
        this.scanFinished.set(true);
        this.filesTotal.set(totalFiles);
        this.directoriesTotal.set(totalDirs);
        publish(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .scanningActive(false)
                .hashingActive(true) // Scanning is done, processing starts
                .filesDiscovered(filesDiscovered.get())
                .directoriesDiscovered(directoriesDiscovered.get())
                .filesTotal(totalFiles)
                .directoriesTotal(totalDirs)
                .duration(formatDuration(Duration.between(startTime, Instant.now())))
                .build());
    }

    @Override
    public void onHashing(Path path, long processed, long total, boolean scanningActive) {
        updateProgress(path, processed, total, scanningActive);
    }

    @Override
    public void onUnchanged(Path path, long processed, long total, boolean scanningActive) {
        updateProgress(path, processed, total, scanningActive);
    }

    @Override
    public void onDeleted(Path path, long processed, long total) {
        this.deletedProcessed.set(processed);
        this.deletedTotal.set(total);
        publish(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .currentFile(path.getFileName().toString())
                .filesDiscovered(filesDiscovered.get())
                .directoriesDiscovered(directoriesDiscovered.get())
                .filesTotal(filesTotal.get())
                .directoriesTotal(directoriesTotal.get())
                .filesProcessed(filesProcessed.get())
                .deletedProcessed(processed)
                .deletedTotal(total)
                .scanningActive(false)
                .hashingActive(false)
                .duration(formatDuration(Duration.between(startTime, Instant.now())))
                .build());
    }

    @Override
    public void onFinished(Statistics stats) {
        publish(ProgressUpdate.builder()
                .repo(repoName)
                .scanningActive(false)
                .hashingActive(false)
                .build());
        eventBus.publish("finished", Map.of("repo", repoName));
    }

    @Override
    public void onError(Path path, Throwable e) {
        publish(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .errors(e.getMessage())
                .build());
    }

    private void updateProgress(Path path, long processed, long total, boolean scanningActive) {
        this.filesProcessed.set(processed);
        if (scanningActive || total > this.filesTotal.get()) {
            this.filesTotal.set(total);
        }
        long currentTotal = this.filesTotal.get();
        Instant now = Instant.now();
        Duration elapsed = Duration.between(startTime, now);

        ProgressUpdate.ProgressUpdateBuilder builder = ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .currentFile(path.getFileName().toString())
                .filesDiscovered(filesDiscovered.get())
                .directoriesDiscovered(directoriesDiscovered.get())
                .filesProcessed(processed)
                .filesTotal(currentTotal)
                .scanningActive(scanningActive)
                .hashingActive(true)
                .duration(formatDuration(elapsed));

        if (currentTotal > 0) {
            double percent = (double) processed / currentTotal * 100;
            if (scanningActive && percent >= 100.0) {
                percent = 99.0;
            }
            builder.progressPercent(percent);

            if (processed > 0) {
                Duration remaining = elapsed.multipliedBy(currentTotal - processed).dividedBy(processed);
                builder.eta(formatDuration(remaining));

                ZonedDateTime end = now.plus(remaining).atZone(ZoneId.systemDefault());
                builder.endTime(TIME_FORMATTER.format(end));
            }
        }

        publish(builder.build());
    }

    private void publish(ProgressUpdate update) {
        eventBus.publish("progress", update);
    }

    private String formatDuration(Duration duration) {
        return DurationFormatUtils.formatDuration(duration.toMillis(), "HH:mm:ss", true);
    }
}
