package paxel.dedup.repo.domain;

import org.apache.commons.lang3.time.DurationFormatUtils;
import paxel.dedup.model.RepoFile;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.utils.FileObserver;
import paxel.dedup.terminal.StatisticPrinter;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static paxel.dedup.repo.domain.BetterPrediction.COUNT;

class UpdateProgressPrinter implements FileObserver {
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss (dd.MM.yyyy)");

    private final BetterPrediction betterPrediction = new BetterPrediction();
    private final Map<Path, RepoFile> remainingPaths;
    private final StatisticPrinter progressPrinter;
    private final AtomicLong files = new AtomicLong();
    private final RepoManager repoManager;
    private final Statistics statistics;
    private final AtomicLong finishedDirs = new AtomicLong();
    private final AtomicLong allDirs = new AtomicLong();
    private final AtomicLong news = new AtomicLong();
    private final AtomicLong hash = new AtomicLong();
    private final AtomicLong unchanged = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final Instant start = Instant.now();


    public UpdateProgressPrinter(Map<Path, RepoFile> remainingPaths, StatisticPrinter progressPrinter,
                                 RepoManager repoManager, Statistics statistics) {
        this.remainingPaths = remainingPaths;
        this.progressPrinter = progressPrinter;
        this.repoManager = repoManager;
        this.statistics = statistics;
    }

    @Override
    public void file(Path absolutePath) {
        remainingPaths.remove(absolutePath);
        progressPrinter.put("files", files.incrementAndGet() + " last: " + absolutePath);
        progressPrinter.put("deleted", "" + remainingPaths.size());
        repoManager.addPath(absolutePath).thenApply(add -> {
            betterPrediction.trigger();
            if (add.isSuccess()) if (add.value() == Boolean.TRUE) {
                statistics.inc("added");
                hash.incrementAndGet();
                logHash(progressPrinter, hash, files, unchanged);
                calcUpdate(start, progressPrinter, betterPrediction, files.get(), hash.get() + unchanged.get());
            } else {
                unchanged.incrementAndGet();
                statistics.inc("unchanged");
                logHash(progressPrinter, hash, files, unchanged);
                calcUpdate(start, progressPrinter, betterPrediction, files.get(), hash.get() + unchanged.get());
            }
            return null;
        });
        news.incrementAndGet();
    }

    private void logHash(StatisticPrinter progressPrinter, AtomicLong hash, AtomicLong files, AtomicLong unchanged) {
        progressPrinter.put("hashed", hash + " / " + (files.get() - unchanged.get()));
        progressPrinter.put("unchanged", unchanged + " / " + (files.get() - hash.get()));
    }

    @Override
    public void addDir(Path f) {
        allDirs.incrementAndGet();
        progressPrinter.put("directories", finishedDirs + " / " + allDirs);
    }

    @Override
    public void finishedDir(Path f) {
        finishedDirs.incrementAndGet();
        progressPrinter.put("directories", finishedDirs + " / " + allDirs);
    }

    @Override
    public void fail(Path root, Throwable e) {
        progressPrinter.put("errors", errors.incrementAndGet() + " last:" + e.getMessage());
    }

    @Override
    public void close() {
        progressPrinter.put("files", files.incrementAndGet() + " scan finished");
        progressPrinter.put("deleted", remainingPaths.size() + " scan finished");
        progressPrinter.put("directories", finishedDirs + " scan finished");
        progressPrinter.put("progress", "finished");
        statistics.set("deleted", remainingPaths.size());
    }

    private void calcUpdate(Instant start, StatisticPrinter progressPrinter, BetterPrediction betterPrediction, long total, long processed) {
        try {


            if (total != 0) {
                double percent = (processed * 100.0) / total;
                Duration estimation = Duration.between(start, Instant.now());

                if (estimation.minusMillis(30000).isPositive()) {
                    estimation = getBetterDuration(betterPrediction, estimation.multipliedBy((long) (100 / percent)), total);
                    ZonedDateTime eta = ZonedDateTime.now().plus(estimation);
                    progressPrinter.put("progress", "%.2f %% estimated duration: %s ETA: %s".formatted(percent,
                            DurationFormatUtils.formatDurationWords(estimation.toMillis(), true, true),
                            dateTimeFormatter.format(eta)));
                }
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private Duration getBetterDuration(BetterPrediction betterPrediction, Duration estimation, long total) {
        Duration duration = betterPrediction.get();
        if (duration != null) {
            double tenFilesPercent = (COUNT * 100.0) / total;
            Duration recentEta = duration.multipliedBy((long) (100 / tenFilesPercent));
            if (recentEta.minus(estimation).isPositive()) return recentEta;
        }
        return estimation;
    }
}
