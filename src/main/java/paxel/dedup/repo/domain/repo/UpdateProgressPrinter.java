package paxel.dedup.repo.domain.repo;

import org.apache.commons.lang3.time.DurationFormatUtils;
import paxel.dedup.domain.model.*;
import paxel.dedup.terminal.StatisticPrinter;

import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static paxel.dedup.domain.model.BetterPrediction.COUNT;

class UpdateProgressPrinter implements FileObserver {
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss (dd.MM.yyyy)");

    private final BetterPrediction betterPrediction;
    private final Map<Path, RepoFile> remainingPaths;
    private final StatisticPrinter progressPrinter;
    private final AtomicLong files = new AtomicLong();
    private final RepoManager repoManager;
    private final Statistics statistics;
    private final FileHasher fileHasher;
    private final AtomicLong finishedDirs = new AtomicLong();
    private final AtomicLong allDirs = new AtomicLong();
    private final AtomicLong news = new AtomicLong();
    private final AtomicLong hash = new AtomicLong();
    private final AtomicLong unchanged = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final Instant start;
    private final AtomicBoolean scanFinished = new AtomicBoolean();
    private final Clock clock;
    private final boolean refreshFingerprints;

    public UpdateProgressPrinter(Map<Path, RepoFile> remainingPaths, StatisticPrinter progressPrinter,
                                 RepoManager repoManager, Statistics statistics, FileHasher fileHasher,
                                 boolean refreshFingerprints) {
        this(remainingPaths, progressPrinter, repoManager, statistics, fileHasher, Clock.systemUTC(), refreshFingerprints);
    }

    public UpdateProgressPrinter(Map<Path, RepoFile> remainingPaths, StatisticPrinter progressPrinter,
                                 RepoManager repoManager, Statistics statistics, FileHasher fileHasher,
                                 Clock clock, boolean refreshFingerprints) {
        this.remainingPaths = remainingPaths;
        this.progressPrinter = progressPrinter;
        this.repoManager = repoManager;
        this.statistics = statistics;
        this.fileHasher = fileHasher;
        this.clock = clock;
        this.start = clock.instant();
        this.betterPrediction = new BetterPrediction(clock);
        this.refreshFingerprints = refreshFingerprints;
    }

    @Override
    public void file(Path absolutePath) {
        RepoFile existing = remainingPaths.remove(absolutePath);
        progressPrinter.setFiles(files.incrementAndGet() + " last: " + absolutePath);
        progressPrinter.setDeleted("" + remainingPaths.size());

        if (!scanFinished.get()) {
            progressPrinter.setProgress("Scanning... Found " + files.get() + " files and " + allDirs.get() + " directories");
        }

        boolean forceUpdate = false;
        if (refreshFingerprints && existing != null) {
            if (existing.mimeType() != null && existing.mimeType().startsWith("image/")) {
                if (existing.fingerprint() == null) {
                    forceUpdate = true;
                }
            }
        }

        if (forceUpdate) {
            // Effectively ignore that we had it, to force re-indexing
            existing = null;
        }

        if (existing == null) {
            repoManager.addPath(absolutePath, fileHasher, new MimetypeProvider()).thenApply(add -> {
                betterPrediction.trigger();
                if (add.isSuccess())
                    if (add.value() != null) {
                        statistics.inc("added");
                        long v = statistics.inc(add.value().mimeType());
                        progressPrinter.addMimeType(add.value().mimeType(), v);
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
        } else {
            // Already handled via remainingPaths.remove(absolutePath) but we should count it as unchanged/processed
            unchanged.incrementAndGet();
            statistics.inc("unchanged");
            logHash(progressPrinter, hash, files, unchanged);
            calcUpdate(start, progressPrinter, betterPrediction, files.get(), hash.get() + unchanged.get());
        }
    }

    private void logHash(StatisticPrinter progressPrinter, AtomicLong hash, AtomicLong files, AtomicLong unchanged) {
        progressPrinter.setHashed(hash + " / " + (files.get() - unchanged.get()));
        progressPrinter.setUnchanged(unchanged + " / " + (files.get() - hash.get()));
        progressPrinter.setDuration(DurationFormatUtils.formatDurationWords(Duration.between(start, clock.instant()).toMillis(), true, true));
    }

    @Override
    public void addDir(Path f) {
        allDirs.incrementAndGet();
        progressPrinter.setDirectories(finishedDirs + " / " + allDirs);
        if (!scanFinished.get()) {
            progressPrinter.setProgress("Scanning... Found " + files.get() + " files and " + allDirs.get() + " directories");
        }
    }

    @Override
    public void finishedDir(Path f) {
        finishedDirs.incrementAndGet();
        progressPrinter.setDirectories(finishedDirs + " / " + allDirs);
    }

    @Override
    public void scanFinished() {
        scanFinished.set(true);
        progressPrinter.setDirectories(finishedDirs + ". scan finished after " + DurationFormatUtils.formatDurationWords(Duration.between(start, clock.instant()).toMillis(), true, true));
        progressPrinter.setProgress("Scan finished. Calculating ETA...");
    }

    @Override
    public void fail(Path root, Throwable e) {
        progressPrinter.setErrors(errors.incrementAndGet() + " last:" + e.getMessage());
    }

    @Override
    public void close() {
        progressPrinter.setFiles(files.incrementAndGet() + " finished");
        progressPrinter.setDeleted(remainingPaths.size() + " finished");
        statistics.set("deleted", remainingPaths.size());
    }

    private void calcUpdate(Instant start, StatisticPrinter progressPrinter, BetterPrediction betterPrediction, long total, long processed) {
        try {
            if (total != 0 && scanFinished.get()) {
                long remaining = total - processed;
                double remainingPercent = (double) remaining / total;
                Duration estimation = Duration.between(start, clock.instant());

                if (estimation.minusMillis(30000).isPositive()) {
                    // Scale baseline proportionally to remaining work without truncation to zero
                    estimation = estimation.multipliedBy(remaining).dividedBy(total);
                    estimation = getBetterDuration(betterPrediction, estimation, total, remaining);
                    ZonedDateTime eta = ZonedDateTime.now(clock).plus(estimation);
                    progressPrinter.setProgress("%.2f %% estimated remaining duration: %s ETA: %s".formatted((1.0 - remainingPercent) * 100,
                            DurationFormatUtils.formatDurationWords(estimation.toMillis(), true, true),
                            dateTimeFormatter.withZone(ZoneId.systemDefault()).format(eta)));
                }
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private Duration getBetterDuration(BetterPrediction betterPrediction, Duration estimation, long total, long remaining) {
        Duration duartionLast1000 = betterPrediction.get();
        if (duartionLast1000 != null) {
            // Use proportional scaling avoiding integer truncation to zero
            Duration recentEta = duartionLast1000.multipliedBy(remaining).dividedBy(COUNT);
            if (recentEta.minus(estimation).isPositive()) return recentEta;
        }
        return estimation;
    }
}
