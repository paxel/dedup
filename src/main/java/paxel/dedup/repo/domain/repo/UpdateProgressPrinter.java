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
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter fullDateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss (dd.MM.yyyy)");

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
        remainingPaths.remove(absolutePath);
        long currentFiles = files.incrementAndGet();

        long currentHashed = hash.get();
        long currentUnchanged = unchanged.get();
        long processed = currentHashed + currentUnchanged;

        ProgressUpdate.ProgressUpdateBuilder updateBuilder = ProgressUpdate.builder()
                .repo(repoManager.getRepo().name())
                .path(repoManager.getRepo().absolutePath())
                .currentFile(absolutePath.getFileName().toString())
                .filesProcessed(processed)
                .filesTotal(currentFiles)
                .hashedProcessed(currentHashed)
                .hashedTotal(currentFiles - currentUnchanged)
                .unchangedProcessed(currentUnchanged)
                .unchangedTotal(currentFiles - currentHashed)
                .deletedProcessed((long) remainingPaths.size())
                .duration(DurationFormatUtils.formatDurationWords(Duration.between(start, clock.instant()).toMillis(), true, true));

        if (!scanFinished.get()) {
            updateBuilder.status("Scanning... Found " + currentFiles + " files and " + allDirs.get() + " directories");
        } else {
            updateBuilder.status(calculateStatus(currentFiles, processed));
            updateBuilder.eta(calculateEta(currentFiles, processed));
            updateBuilder.progressPercent((double) processed / currentFiles * 100);
        }

        progressPrinter.update(updateBuilder.build());

        boolean forceUpdate = false;
        if (refreshFingerprints) {
            RepoFile existing = repoManager.getByPath(repoManager.getRepoDir().relativize(absolutePath).toString());
            if (existing != null && existing.mimeType() != null && existing.mimeType().startsWith("image/")) {
                if (existing.fingerprint() == null) {
                    forceUpdate = true;
                }
            }
        }

        CompletableFuture<Result<RepoFile, DedupError>> future = repoManager.addPath(absolutePath, fileHasher, new MimetypeProvider());
        futures.add(future);
        future.thenAccept(add -> {
            betterPrediction.trigger();
            if (add.isSuccess()) {
                if (add.value() != null) {
                    statistics.inc("added");
                    long v = statistics.inc(add.value().mimeType());
                    hash.incrementAndGet();

                    ProgressUpdate pu = ProgressUpdate.builder()
                            .hashedProcessed(hash.get())
                            .mimeDistribution(Map.of(add.value().mimeType(), v))
                            .build();
                    progressPrinter.update(pu);
                } else {
                    unchanged.incrementAndGet();
                    statistics.inc("unchanged");
                    progressPrinter.update(ProgressUpdate.builder().unchangedProcessed(unchanged.get()).build());
                }
                // Refresh status and progress bar
                long total = files.get();
                long done = hash.get() + unchanged.get();
                progressPrinter.update(ProgressUpdate.builder()
                        .filesProcessed(done)
                        .filesTotal(total)
                        .status(calculateStatus(total, done))
                        .eta(calculateEta(total, done))
                        .progressPercent((double) done / total * 100)
                        .build());
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
    }

    private String calculateEta(long total, long processed) {
        if (total == 0 || !scanFinished.get() || processed == 0) return null;

        Duration globalEstimation = Duration.between(start, clock.instant());
        if (globalEstimation.toMillis() < 5000) return "Calculating...";

        long remaining = total - processed;
        Duration estimation = globalEstimation.multipliedBy(remaining).dividedBy(processed);

        Duration recentEstimation = getBetterDuration(betterPrediction, remaining);
        if (recentEstimation != null) {
            int recentCount = betterPrediction.getCount();
            double recentWeight = (double) recentCount / BetterPrediction.COUNT;
            long blendedMillis = (long) (recentEstimation.toMillis() * recentWeight + estimation.toMillis() * (1.0 - recentWeight));
            estimation = Duration.ofMillis(blendedMillis);
            if (recentEstimation.compareTo(estimation) > 0) {
                estimation = recentEstimation;
            }
        }

        ZonedDateTime eta = ZonedDateTime.now(clock).plus(estimation);
        LocalDate today = LocalDate.now(clock);
        if (eta.toLocalDate().equals(today)) {
            return timeFormatter.withZone(ZoneId.systemDefault()).format(eta);
        }
        return fullDateTimeFormatter.withZone(ZoneId.systemDefault()).format(eta);
    }

    private String calculateStatus(long total, long processed) {
        if (total == 0 || !scanFinished.get()) return "...standing by...";

        Duration globalEstimation = Duration.between(start, clock.instant());
        if (globalEstimation.toMillis() < 5000) return "Calculating ETA...";

        long remaining = total - processed;
        Duration estimation = globalEstimation.multipliedBy(remaining).dividedBy(Math.max(1, processed));

        Duration recentEstimation = getBetterDuration(betterPrediction, remaining);
        if (recentEstimation != null) {
            int recentCount = betterPrediction.getCount();
            double recentWeight = (double) recentCount / BetterPrediction.COUNT;
            long blendedMillis = (long) (recentEstimation.toMillis() * recentWeight + estimation.toMillis() * (1.0 - recentWeight));
            estimation = Duration.ofMillis(blendedMillis);
            if (recentEstimation.compareTo(estimation) > 0) {
                estimation = recentEstimation;
            }
        }

        return "remaining: %s".formatted(DurationFormatUtils.formatDurationWords(estimation.toMillis(), true, true));
    }

    @Override
    public void addDir(Path f) {
        allDirs.incrementAndGet();
        progressPrinter.update(ProgressUpdate.builder()
                .directoriesProcessed(finishedDirs.get())
                .directoriesTotal(allDirs.get())
                .build());
        if (!scanFinished.get()) {
            progressPrinter.update(ProgressUpdate.builder()
                    .status("Scanning... Found " + files.get() + " files and " + allDirs.get() + " directories")
                    .build());
        }
    }

    @Override
    public void finishedDir(Path f) {
        finishedDirs.incrementAndGet();
        progressPrinter.update(ProgressUpdate.builder()
                .directoriesProcessed(finishedDirs.get())
                .directoriesTotal(allDirs.get())
                .build());
    }

    @Override
    public void scanFinished() {
        scanFinished.set(true);
        String durationStr = DurationFormatUtils.formatDurationWords(Duration.between(start, clock.instant()).toMillis(), true, true);
        progressPrinter.update(ProgressUpdate.builder()
                .status("Scan finished after " + durationStr + ". Calculating ETA...")
                .build());
    }

    @Override
    public void fail(Path root, Throwable e) {
        firstError.compareAndSet(null, e);
        progressPrinter.update(ProgressUpdate.builder()
                .errors(errors.incrementAndGet() + " last:" + e.getMessage())
                .build());
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
        long total = files.get();
        progressPrinter.update(ProgressUpdate.builder()
                .filesProcessed(total)
                .filesTotal(total)
                .deletedProcessed((long) remainingPaths.size())
                .deletedTotal((long) remainingPaths.size())
                .build());
        statistics.set("deleted", remainingPaths.size());
        progressPrinter.finish();
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
