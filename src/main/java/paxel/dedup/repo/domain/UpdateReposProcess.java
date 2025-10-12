package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.time.DurationFormatUtils;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.Repo;
import paxel.dedup.model.RepoFile;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.*;
import paxel.dedup.model.utils.FileObserver;
import paxel.dedup.model.utils.HexFormatter;
import paxel.dedup.model.utils.ResilientFileWalker;
import paxel.dedup.model.utils.Sha1Hasher;
import paxel.dedup.parameter.CliParameter;
import paxel.dedup.terminal.StatisticPrinter;
import paxel.dedup.terminal.TerminalProgress;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static paxel.dedup.repo.domain.BetterPrediction.COUNT;


public class UpdateReposProcess {
    private TerminalProgress terminalProgress;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss (dd.MM.yyyy)");

    public int update(List<String> names, boolean all, CliParameter cliParameter, int threads) {

        Result<DedupConfig, CreateConfigError> configResult = DedupConfigFactory.create();
        if (configResult.hasFailed()) {
            new DedupConfigErrorHandler().dump(configResult.error());
            return -1;
        }
        ObjectMapper objectMapper = new ObjectMapper();

        DedupConfig dedupConfig = configResult.value();
        if (all) {
            Result<List<Repo>, OpenRepoError> lsResult = dedupConfig.getRepos();
            if (lsResult.hasFailed()) {
                IOException ioException = lsResult.error().ioException();
                if (ioException != null) {
                    ioException.printStackTrace();
                }
                return -50;
            }
            for (Repo repo : lsResult.value()) {
                updateRepo(new RepoManager(repo, dedupConfig, objectMapper, cliParameter, new Sha1Hasher(new HexFormatter(), Executors.newFixedThreadPool(threads))));
            }
            return 0;
        }

        for (String name : names) {
            Result<Repo, OpenRepoError> getRepoResult = dedupConfig.getRepo(name);
            if (getRepoResult.isSuccess()) {
                Result<Statistics, UpdateRepoError> statisticsUpdateRepoErrorResult = updateRepo(new RepoManager(getRepoResult.value(), dedupConfig, objectMapper, cliParameter,
                        new Sha1Hasher(new HexFormatter(), Executors.newFixedThreadPool(threads))));
            }
        }
        return 0;
    }


    private Result<Statistics, UpdateRepoError> updateRepo(RepoManager repoManager) {
        Result<Statistics, LoadError> load = repoManager.load();
        if (load.hasFailed()) {
            return load.mapError(f -> new UpdateRepoError(repoManager.getRepoDir(), load.error().ioException()));
        }
        Map<Path, RepoFile> remainingPaths = repoManager.stream().filter(r -> !r.missing()).collect(Collectors.toMap(r -> Paths.get(repoManager.getRepo().absolutePath(), r.relativePath()), Function.identity(), (old, update) -> update));
        StatisticPrinter progressPrinter = new StatisticPrinter();
        terminalProgress = TerminalProgress.init(progressPrinter);
        BetterPrediction betterPrediction = new BetterPrediction();
        try {
            progressPrinter.put(repoManager.getRepo().name(), repoManager.getRepo().absolutePath());
            progressPrinter.put("progress", "...stand by... collecting info");
            Statistics statistics = new Statistics(repoManager.getRepo().absolutePath());
            AtomicLong allDirs = new AtomicLong();
            AtomicLong finishedDirs = new AtomicLong();
            AtomicLong files = new AtomicLong();
            AtomicLong news = new AtomicLong();
            AtomicLong hash = new AtomicLong();
            AtomicLong unchanged = new AtomicLong();
            AtomicLong errors = new AtomicLong();
            Instant start = Instant.now();
            new ResilientFileWalker(new FileObserver() {

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
            }).walk(Paths.get(repoManager.getRepo().absolutePath()));
            progressPrinter.put("files", files.incrementAndGet() + " scan finished");
            progressPrinter.put("deleted", remainingPaths.size() + " scan finished");
            progressPrinter.put("directories", finishedDirs + " scan finished");
            statistics.set("deleted", remainingPaths.size());
            for (RepoFile value : remainingPaths.values()) {
                repoManager.addRepoFile(value.withMissing(true));
            }
            return Result.ok(statistics);
        } finally {
            repoManager.close();
            terminalProgress.deactivate();
        }
    }

    private static void logHash(StatisticPrinter progressPrinter, AtomicLong hash, AtomicLong files, AtomicLong unchanged) {
        progressPrinter.put("hashed", hash + " / " + (files.get() - unchanged.get()));
        progressPrinter.put("unchanged", unchanged + " / " + (files.get() - hash.get()));
    }

    private void calcUpdate(Instant start, StatisticPrinter progressPrinter, BetterPrediction betterPrediction, long total, long processed) {
        try {


            if (total != 0) {
                double percent = (processed * 100.0) / total;
                Duration estimation = Duration.between(start, Instant.now());

                if (estimation.minusMillis(30000).isPositive()) {
                    estimation = getBetterDuration(betterPrediction, estimation.multipliedBy((long) (100 / percent)), total);
                    ZonedDateTime eta = ZonedDateTime.now().plus(estimation);
                    progressPrinter.put("progress", "%.2f %% estimated duration: %s ETA: %s".formatted(percent, DurationFormatUtils.formatDurationWords(estimation.toMillis(), true, true), dateTimeFormatter.format(eta)));
                }
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private static Duration getBetterDuration(BetterPrediction betterPrediction, Duration estimation, long total) {
        Duration duration = betterPrediction.get();
        if (duration != null) {
            double tenFilesPercent = (COUNT * 100.0) / total;
            Duration recentEta = duration.multipliedBy((long) (100 / tenFilesPercent));
            if (recentEta.minus(estimation).isPositive()) return recentEta;
        }
        return estimation;
    }


}
