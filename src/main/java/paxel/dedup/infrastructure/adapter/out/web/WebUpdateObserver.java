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
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
public class WebUpdateObserver implements UpdateObserver {

    private final String repoName;
    private final String absolutePath;
    private final EventBus eventBus;
    private final Instant startTime = Instant.now();
    private final AtomicLong lastPercentUpdate = new AtomicLong(0);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void onDiscovery(Path path, long totalFiles, long totalDirs) {
        publish(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .scanningActive(true)
                .filesDiscovered(totalFiles)
                .directoriesDiscovered(totalDirs)
                .status("Scanning...")
                .duration(formatDuration(Duration.between(startTime, Instant.now())))
                .build());
    }

    @Override
    public void onScanFinished(long totalFiles, long totalDirs) {
        publish(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .scanningActive(false)
                .filesTotal(totalFiles)
                .directoriesTotal(totalDirs)
                .status("Scan finished. Starting processing...")
                .duration(formatDuration(Duration.between(startTime, Instant.now())))
                .build());
    }

    @Override
    public void onHashing(Path path, long processed, long total) {
        updateProgress(path, processed, total, "Hashing");
    }

    @Override
    public void onUnchanged(Path path, long processed, long total) {
        updateProgress(path, processed, total, "Unchanged");
    }

    @Override
    public void onDeleted(Path path, long processed, long total) {
        publish(ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .currentFile(path.getFileName().toString())
                .deletedProcessed(processed)
                .deletedTotal(total)
                .status("Deleting")
                .duration(formatDuration(Duration.between(startTime, Instant.now())))
                .build());
    }

    @Override
    public void onFinished(Statistics stats) {
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

    private void updateProgress(Path path, long processed, long total, String status) {
        Instant now = Instant.now();
        Duration elapsed = Duration.between(startTime, now);

        ProgressUpdate.ProgressUpdateBuilder builder = ProgressUpdate.builder()
                .repo(repoName)
                .path(absolutePath)
                .currentFile(path.getFileName().toString())
                .filesProcessed(processed)
                .filesTotal(total)
                .status(status)
                .duration(formatDuration(elapsed));

        if (total > 0) {
            double percent = (double) processed / total * 100;

            // Limit percent update to once per second
            long nowSec = now.getEpochSecond();
            long lastSec = lastPercentUpdate.get();
            if (nowSec > lastSec) {
                if (lastPercentUpdate.compareAndSet(lastSec, nowSec)) {
                    builder.progressPercent(percent);
                }
            }

            if (processed > 0) {
                Duration remaining = elapsed.multipliedBy(total - processed).dividedBy(processed);
                builder.eta(formatDuration(remaining));

                ZonedDateTime end = now.plus(remaining).atZone(ZoneId.systemDefault());
                builder.endTime(TIME_FORMATTER.format(end));
                builder.status(status);
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
