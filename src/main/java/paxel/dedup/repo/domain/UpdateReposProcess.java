package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.Repo;
import paxel.dedup.model.RepoFile;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.*;
import paxel.dedup.parameter.CliParameter;
import paxel.dedup.terminal.StatisticPrinter;
import paxel.dedup.terminal.TerminalProgress;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class UpdateReposProcess {
    private TerminalProgress terminalProgress;
    private CliParameter cliParameter;

    public int update(List<String> names, boolean all, CliParameter cliParameter) {
        this.cliParameter = cliParameter;

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
                return -3;
            }
            for (Repo repo : lsResult.value()) {
                updateRepo(new RepoManager(repo, dedupConfig, objectMapper, cliParameter));
            }
            return 0;
        }

        for (String name : names) {
            Result<Repo, OpenRepoError> getRepoResult = dedupConfig.getRepo(name);
            if (getRepoResult.isSuccess()) {
                Result<Statistics, UpdateRepoError> statisticsUpdateRepoErrorResult = updateRepo(new RepoManager(getRepoResult.value(), dedupConfig, objectMapper, cliParameter));
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
        try {
            progressPrinter.put(repo.getRepo().name(), repo.getRepo().absolutePath());
            Statistics statistics = new Statistics(repo.getRepo().absolutePath());
            AtomicLong dirs = new AtomicLong();
            AtomicLong files = new AtomicLong();
            AtomicLong news = new AtomicLong();
            AtomicLong errors = new AtomicLong();
            new ResilientFileWalker(new FileObserver() {

                @Override
                public void file(Path absolutePath) {
                    remainingPaths.remove(absolutePath);
                    progressPrinter.put("files", files.incrementAndGet() + " last: " + absolutePath);
                    progressPrinter.put("remaining", "" + remainingPaths.size());
                    Result<Boolean, WriteError> add = repo.addPath(absolutePath);
                    if (add.isSuccess() && add.value() == Boolean.TRUE) {
                        statistics.inc("added");
                        progressPrinter.put("new/modified", news.incrementAndGet() + " last: " + absolutePath);
                    }
                }

                @Override
                public void dir(Path f) {
                    progressPrinter.put("directories", dirs.incrementAndGet() + " last: " + f);
                }

                @Override
                public void fail(Path root, Exception e) {
                    progressPrinter.put("errors", errors.incrementAndGet() + " last:" + e.getMessage());
                }
            }).walk(Paths.get(repo.getRepo().absolutePath()));
            statistics.set("deleted", remainingPaths.size());
            progressPrinter.put("deleted", "" + remainingPaths.size());
            for (RepoFile value : remainingPaths.values()) {
                repo.addRepoFile(value.withMissing(true));
            }
            return Result.ok(statistics);
        } finally {
            terminalProgress.deactivate();
        }
    }
}
