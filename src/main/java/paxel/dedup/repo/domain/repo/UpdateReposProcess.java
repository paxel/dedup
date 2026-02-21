package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.*;
import paxel.dedup.domain.model.errors.LoadError;
import paxel.dedup.domain.model.errors.OpenRepoError;
import paxel.dedup.domain.model.errors.UpdateRepoError;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.config.DedupConfig;
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
    private final boolean progress;

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
                updateRepo(RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter()));
            }
            return 0;
        }

        for (String name : names) {
            Result<Repo, OpenRepoError> getRepoResult = dedupConfig.getRepo(name);
            if (getRepoResult.isSuccess()) {
                Repo repo = getRepoResult.value();
                Result<Statistics, UpdateRepoError> statisticsUpdateRepoErrorResult = updateRepo(RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter()));
            }
        }
        return 0;
    }


    private Result<Statistics, UpdateRepoError> updateRepo(RepoManager repoManager) {
        Result<Statistics, LoadError> load = repoManager.load();
        if (load.hasFailed()) {
            return load.mapError(f -> UpdateRepoError.ioException(repoManager.getRepoDir(), load.error().ioException()));
        }
        Map<Path, RepoFile> remainingPaths = repoManager.stream().filter(r -> !r.missing()).collect(Collectors.toMap(r -> Paths.get(repoManager.getRepo().absolutePath(), r.relativePath()), Function.identity(), (old, update) -> update));
        StatisticPrinter progressPrinter = new StatisticPrinter();
        TerminalProgress terminalProgress = prepProgress(progressPrinter);
        Sha1Hasher sha1Hasher = new Sha1Hasher(new HexFormatter(), Executors.newFixedThreadPool(threads));
        try {
            progressPrinter.set(repoManager.getRepo().name(), repoManager.getRepo().absolutePath());
            progressPrinter.setProgress("...stand by... collecting info");
            Statistics statistics = new Statistics(repoManager.getRepo().absolutePath());
            new ResilientFileWalker(new UpdateProgressPrinter(remainingPaths, progressPrinter, repoManager, statistics, sha1Hasher), new NioFileSystemAdapter()).walk(Paths.get(repoManager.getRepo().absolutePath()));

            for (RepoFile value : remainingPaths.values()) {
                repoManager.addRepoFile(value.withMissing(true));
            }
            return Result.ok(statistics);
        } finally {
            sha1Hasher.close();
            terminalProgress.deactivate();
        }
    }

    private TerminalProgress prepProgress(StatisticPrinter progressPrinter) {
        if (progress) {
            return TerminalProgress.initLanterna(progressPrinter);
        }
        return TerminalProgress.initDummy(progressPrinter);
    }
}
