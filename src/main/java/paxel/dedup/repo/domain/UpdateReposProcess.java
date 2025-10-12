package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.time.DurationFormatUtils;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.Repo;
import paxel.dedup.model.RepoFile;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.model.errors.LoadError;
import paxel.dedup.model.errors.OpenRepoError;
import paxel.dedup.model.errors.UpdateRepoError;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;


public class UpdateReposProcess {
    private TerminalProgress terminalProgress;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss (dd.MM.yyyy)");

    public int update(List<String> names, boolean all, CliParameter cliParameter) {

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
                updateRepo(new RepoManager(repo, dedupConfig, objectMapper, cliParameter, new Sha1Hasher(new HexFormatter())));
            }
            return 0;
        }

        for (String name : names) {
            Result<Repo, OpenRepoError> getRepoResult = dedupConfig.getRepo(name);
            if (getRepoResult.isSuccess()) {
                Result<Statistics, UpdateRepoError> statisticsUpdateRepoErrorResult = updateRepo(new RepoManager(getRepoResult.value(), dedupConfig, objectMapper, cliParameter, new Sha1Hasher(new HexFormatter())));
            }
        }
        return 0;
    }


    private Result<Statistics, UpdateRepoError> updateRepo(RepoManager repo) {
        Result<Statistics, LoadError> load = repo.load();
        if (load.hasFailed()) {
            return load.mapError(f -> new UpdateRepoError(repo.getRepoDir(), load.error().ioException()));
        }
        Map<Path, RepoFile> remainingPaths = repo.stream()
                .filter(r -> !r.missing())
                .collect(Collectors.toMap(r -> Paths.get(repo.getRepo().absolutePath(),
                                r.relativePath()), Function.identity(),
                        (old, update) -> update));
        StatisticPrinter progressPrinter = new StatisticPrinter();
        terminalProgress = TerminalProgress.init(progressPrinter);
        BetterPrediction betterPrediction = new BetterPrediction();
        try {
            progressPrinter.put(repo.getRepo().name(), repo.getRepo().absolutePath());
            Statistics statistics = new Statistics(repo.getRepo().absolutePath());
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
                    repo.addPath(absolutePath).thenApply(add -> {
                        if (add.isSuccess())
                            if (add.value() == Boolean.TRUE) {
                                statistics.inc("added");
                                hash.incrementAndGet();
                                progressPrinter.put("hashed", hash + " / " + (files.get() - unchanged.get()));
                                calcUpdate(allDirs, finishedDirs, start, progressPrinter, betterPrediction);
                            } else {
                                unchanged.incrementAndGet();
                                statistics.inc("unchanged");
                                progressPrinter.put("unchanged", unchanged + " / " + (files.get() - hash.get()));
                                calcUpdate(allDirs, finishedDirs, start, progressPrinter, betterPrediction);
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
                    betterPrediction.trigger();
                    progressPrinter.put("directories", finishedDirs + " / " + allDirs);
                    calcUpdate(allDirs, finishedDirs, start, progressPrinter, betterPrediction);
                }

                @Override
                public void fail(Path root, Throwable e) {
                    progressPrinter.put("errors", errors.incrementAndGet() + " last:" + e.getMessage());
                }
            }).walk(Paths.get(repo.getRepo().absolutePath()));
            statistics.set("deleted", remainingPaths.size());
            for (RepoFile value : remainingPaths.values()) {
                repo.addRepoFile(value.withMissing(true));
            }
            return Result.ok(statistics);
        } finally {
            terminalProgress.deactivate();
        }
    }

    private void calcUpdate(AtomicLong dirs, AtomicLong finishedDirs, Instant now, StatisticPrinter progressPrinter, BetterPrediction betterPrediction) {
        long allDirs = dirs.get();
        if (allDirs != 0) {
            long percent = Math.clamp((finishedDirs.get() * 100) / allDirs, 1, 100);
            Duration estimation = Duration.between(now, Instant.now());

            if (estimation.minusMillis(30000).isPositive()) {
                estimation = getBetterDuration(betterPrediction, estimation.multipliedBy(100).dividedBy(percent), allDirs);
                ZonedDateTime eta = ZonedDateTime.now().plus(estimation);
                progressPrinter.put("progress", "%d %% estimated duration: %s ETA: %s".formatted(percent,
                        DurationFormatUtils.formatDurationWords(estimation.toMillis(), true, true),
                        dateTimeFormatter.format(eta)));
            }
        }
    }

    private static Duration getBetterDuration(BetterPrediction betterPrediction, Duration estimation, long allDirs) {
        Duration duration = betterPrediction.get();
        if (duration != null) {
            long tenFilesPercent = Math.clamp((1000) / allDirs, 1, 100);
            Duration recentEta = duration.multipliedBy(100).dividedBy(tenFilesPercent);
            if (recentEta.minus(estimation).isPositive())
                return recentEta;
        }
        return estimation;
    }


}
