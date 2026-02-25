package paxel.dedup.repo.domain.repo;

import org.apache.commons.lang3.time.DurationFormatUtils;
import paxel.dedup.domain.model.*;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.terminal.StatisticPrinter;
import paxel.lib.Result;

import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicReference<Throwable> firstError = new AtomicReference<>();
    private final Instant start;
    private final AtomicBoolean scanFinished = new AtomicBoolean();
    private final Clock clock;
    private final boolean refreshFingerprints;
    private final List<CompletableFuture<?>> futures = Collections.synchronizedList(new ArrayList<>());

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
        long currentFiles = files.incrementAndGet();
        progressPrinter.setFiles(currentFiles + " last: " + absolutePath);
        progressPrinter.setDeleted("" + remainingPaths.size());

        if (!scanFinished.get()) {
            progressPrinter.setProgress("Scanning... Found " + currentFiles + " files and " + allDirs.get() + " directories");
        } else {
            // Already finished scanning, we can show progress for hashing/processing
            calcUpdate(start, progressPrinter, betterPrediction, files.get(), hash.get() + unchanged.get());
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
            CompletableFuture<Result<RepoFile, DedupError>> future = repoManager.addPath(absolutePath, fileHasher, new MimetypeProvider());
            futures.add(future);
            future.thenAccept(add -> {
                betterPrediction.trigger();
                if (add.isSuccess()) {
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
                } else {
                    fail(absolutePath, add.error().exception());
                }
            }).whenComplete((r, e) -> {
                if (e != null) {
                    fail(absolutePath, e);
                }
                futures.remove(future);
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
        firstError.compareAndSet(null, e);
        progressPrinter.setErrors(errors.incrementAndGet() + " last:" + e.getMessage());
    }

    public long getErrors() {
        return errors.get();
    }

    public Throwable getFirstError() {
        return firstError.get();
    }

    public long getFiles() {
        return files.get();
    }

    public long getAllDirs() {
        return allDirs.get();
    }

    @Override
    public void close() {
        while (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            } catch (Exception e) {
                fail(null, e);
            }
        }
        progressPrinter.setFiles(files.get() + " finished");
        progressPrinter.setDeleted(remainingPaths.size() + " finished");
        statistics.set("deleted", remainingPaths.size());
    }

    private void calcUpdate(Instant start, StatisticPrinter progressPrinter, BetterPrediction betterPrediction, long total, long processed) {
        try {
            if (total != 0 && scanFinished.get()) {
                long remaining = total - processed;
                double progressPercent = (double) processed / total;
                Duration globalEstimation = Duration.between(start, clock.instant());

                if (globalEstimation.minusMillis(5000).isPositive()) {
                    // Global average estimation
                    Duration estimation = globalEstimation.multipliedBy(remaining).dividedBy(Math.max(1, processed));

                    // Recent average estimation
                    Duration recentEstimation = getBetterDuration(betterPrediction, remaining);
                    if (recentEstimation != null) {
                        // Blend global and recent estimation. 
                        // If we have COUNT samples, we trust recent more.
                        // If we just started, global might be more stable.
                        int recentCount = betterPrediction.getCount();
                        double recentWeight = (double) recentCount / BetterPrediction.COUNT;
                        // But actually, the user wants it to be reactive.
                        // Let's use the more pessimistic one if we want to be safe, 
                        // or just use recent if we have enough samples.

                        // Weighted average:
                        long blendedMillis = (long) (recentEstimation.toMillis() * recentWeight + estimation.toMillis() * (1.0 - recentWeight));
                        estimation = Duration.ofMillis(blendedMillis);

                        // If recent is much slower than global, favor recent even more to catch the "slow hash" case.
                        if (recentEstimation.compareTo(estimation) > 0) {
                            estimation = recentEstimation;
                        }
                    }

                    ZonedDateTime eta = ZonedDateTime.now(clock).plus(estimation);
                    progressPrinter.setProgress("%.2f %% estimated remaining: %s ETA: %s".formatted(progressPercent * 100,
                            DurationFormatUtils.formatDurationWords(estimation.toMillis(), true, true),
                            dateTimeFormatter.withZone(ZoneId.systemDefault()).format(eta)));
                }
            }
        } catch (RuntimeException e) {
            fail(null, e);
        }
    }

    private Duration getBetterDuration(BetterPrediction betterPrediction, long remaining) {
        Duration durationRecent = betterPrediction.get();
        int count = betterPrediction.getCount();
        if (durationRecent != null && count > 0) {
            return durationRecent.multipliedBy(remaining).dividedBy(count);
        }
        return null;
    }
}
