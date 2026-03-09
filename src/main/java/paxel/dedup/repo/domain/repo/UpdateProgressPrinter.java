package paxel.dedup.repo.domain.repo;

import paxel.dedup.domain.model.*;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.lib.Result;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class UpdateProgressPrinter implements FileObserver {

    private final BetterPrediction betterPrediction;
    private final Map<Path, RepoFile> remainingPaths;
    private final UpdateObserver observer;
    private final AtomicLong files = new AtomicLong();
    private final RepoManager repoManager;
    private final Statistics statistics;
    private final FileHasher fileHasher;
    private final AtomicLong finishedDirs = new AtomicLong();
    private final AtomicLong allDirs = new AtomicLong();
    private final AtomicLong hash = new AtomicLong();
    private final AtomicLong unchanged = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicReference<Throwable> firstError = new AtomicReference<>();
    private final Instant start;
    private final AtomicBoolean scanFinished = new AtomicBoolean();
    private final Clock clock;
    private final boolean refreshFingerprints;
    private final boolean verbose;
    private final List<CompletableFuture<?>> futures = Collections.synchronizedList(new ArrayList<>());
    private java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);

    public UpdateProgressPrinter(Map<Path, RepoFile> remainingPaths, UpdateObserver observer,
                                 RepoManager repoManager, Statistics statistics, FileHasher fileHasher,
                                 boolean refreshFingerprints, boolean verbose) {
        this(remainingPaths, observer, repoManager, statistics, fileHasher, Clock.systemUTC(), refreshFingerprints, verbose);
    }

    public UpdateProgressPrinter(Map<Path, RepoFile> remainingPaths, UpdateObserver observer,
                                 RepoManager repoManager, Statistics statistics, FileHasher fileHasher,
                                 Clock clock, boolean refreshFingerprints, boolean verbose) {
        this.remainingPaths = remainingPaths;
        this.observer = observer;
        this.repoManager = repoManager;
        this.statistics = statistics;
        this.fileHasher = fileHasher;
        this.clock = clock;
        this.start = clock.instant();
        this.betterPrediction = new BetterPrediction(clock);
        this.refreshFingerprints = refreshFingerprints;
        this.verbose = verbose;
    }

    @Override
    public void file(Path absolutePath) {
        if (cancelled.get()) {
            return;
        }
        remainingPaths.remove(absolutePath);
        long currentFiles = files.incrementAndGet();
        long currentDirs = allDirs.get();

        if (!scanFinished.get()) {
            observer.onDiscovery(absolutePath, currentFiles, currentDirs);
        }

        CompletableFuture<Result<RepoFile, DedupError>> future = repoManager.addPath(absolutePath, fileHasher, new MimetypeProvider());
        futures.add(future);
        future.thenAccept(add -> {
            betterPrediction.trigger();
            long currentTotal = files.get();
            boolean stillScanning = !scanFinished.get();
            if (add.isSuccess()) {
                if (add.value() != null) {
                    statistics.inc("added");
                    statistics.inc(add.value().mimeType());
                    hash.incrementAndGet();
                    long currentProcessed = processed.incrementAndGet();
                    observer.onHashing(absolutePath, currentProcessed, currentTotal, stillScanning);
                } else {
                    statistics.inc("unchanged");
                    unchanged.incrementAndGet();
                    long currentProcessed = processed.incrementAndGet();
                    observer.onUnchanged(absolutePath, currentProcessed, currentTotal, stillScanning);
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
    }

    @Override
    public void addDir(Path f) {
        allDirs.incrementAndGet();
        if (!scanFinished.get()) {
            observer.onDiscovery(f, files.get(), allDirs.get());
        }
    }

    @Override
    public void finishedDir(Path f) {
        finishedDirs.incrementAndGet();
    }

    @Override
    public void scanFinished() {
        scanFinished.set(true);
        observer.onScanFinished(files.get(), allDirs.get());
    }

    @Override
    public void fail(Path root, Throwable e) {
        firstError.compareAndSet(null, e);
        errors.incrementAndGet();
        observer.onError(root, e);
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

    public void setCancelled(java.util.concurrent.atomic.AtomicBoolean cancelled) {
        this.cancelled = cancelled;
    }

    public void cancelNow() {
        cancelled.set(true);
        // Shut down the hasher's executor to interrupt running threads
        if (fileHasher instanceof Sha1Hasher sha1Hasher) {
            sha1Hasher.shutdownNow();
        }
        CompletableFuture<?>[] snapshot;
        synchronized (futures) {
            snapshot = futures.toArray(new CompletableFuture[0]);
            futures.clear();
        }
        for (CompletableFuture<?> f : snapshot) {
            f.cancel(true);
        }
    }

    @Override
    public void close() {
        if (cancelled.get()) {
            CompletableFuture<?>[] snapshot;
            synchronized (futures) {
                snapshot = futures.toArray(new CompletableFuture[0]);
                futures.clear();
            }
            for (CompletableFuture<?> f : snapshot) {
                f.cancel(true);
            }
        } else {
            while (true) {
                CompletableFuture<?>[] array;
                synchronized (futures) {
                    if (futures.isEmpty()) {
                        break;
                    }
                    array = futures.toArray(new CompletableFuture[0]);
                }
                try {
                    CompletableFuture.allOf(array).get();
                } catch (Exception e) {
                    fail(null, e);
                }
            }
        }

        if (!cancelled.get()) {
            long totalDeleted = remainingPaths.size();
            long count = 0;
            for (Path p : remainingPaths.keySet()) {
                observer.onDeleted(p, ++count, totalDeleted);
            }
            statistics.set("deleted", remainingPaths.size());
        }
    }
}
