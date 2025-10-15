package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.model.Repo;
import paxel.dedup.model.RepoFile;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.LoadError;
import paxel.dedup.model.errors.OpenRepoError;
import paxel.dedup.model.errors.UpdateRepoError;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;


@RequiredArgsConstructor
public class UpdateReposProcess {

    private final CliParameter cliParameter;
    private final List<String> names;
    private final boolean all;
    private final int threads;
    private final DedupConfig dedupConfig;
    private final ObjectMapper objectMapper;

    public int update() {

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
        Map<Path, RepoFile> remainingPaths = repoManager.stream()
                .filter(r -> !r.missing())
                .collect(Collectors.toMap(r -> Paths.get(repoManager.getRepo().absolutePath(), r.relativePath()), Function.identity(), (old, update) -> update));
        StatisticPrinter progressPrinter = new StatisticPrinter();
        TerminalProgress terminalProgress = TerminalProgress.init(progressPrinter);
        try {
            progressPrinter.put(repoManager.getRepo().name(), repoManager.getRepo().absolutePath());
            progressPrinter.put("progress", "...stand by... collecting info");
            Statistics statistics = new Statistics(repoManager.getRepo().absolutePath());
            new ResilientFileWalker(new UpdateProgressPrinter(remainingPaths, progressPrinter, repoManager, statistics))
                    .walk(Paths.get(repoManager.getRepo().absolutePath()));

            for (RepoFile value : remainingPaths.values()) {
                repoManager.addRepoFile(value.withMissing(true));
            }
            return Result.ok(statistics);
        } finally {
            repoManager.close();
            terminalProgress.deactivate();
        }
    }
}
