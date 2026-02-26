package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.*;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.terminal.StatisticPrinter;
import paxel.dedup.terminal.TerminalProgress;
import paxel.lib.Result;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    private final boolean refreshFingerprints;
    private final FileSystem fileSystem;
    private paxel.dedup.domain.service.EventBus eventBus;

    public UpdateReposProcess(CliParameter cliParameter, List<String> names, boolean all, int threads, DedupConfig dedupConfig, boolean progress, boolean refreshFingerprints) {
        this(cliParameter, names, all, threads, dedupConfig, progress, refreshFingerprints, new NioFileSystemAdapter());
    }

    public UpdateReposProcess withEventBus(paxel.dedup.domain.service.EventBus eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    public Result<Integer, DedupError> update() {
        Result<List<Repo>, DedupError> reposToUpdate;
        if (all) {
            reposToUpdate = dedupConfig.getRepos();
            if (reposToUpdate.isSuccess() && reposToUpdate.value().isEmpty()) {
                return Result.err(DedupError.of(paxel.dedup.domain.model.errors.ErrorType.OPEN_REPO, "No repositories found to update. Use --all or specify repository names."));
            }
        } else {
            List<Repo> repos = new ArrayList<>();
            for (String name : names) {
                Result<Repo, DedupError> getRepoResult = dedupConfig.getRepo(name);
                if (getRepoResult.isSuccess()) {
                    repos.add(getRepoResult.value());
                } else {
                    return Result.err(getRepoResult.error());
                }
            }
            if (repos.isEmpty()) {
                return Result.err(DedupError.of(paxel.dedup.domain.model.errors.ErrorType.OPEN_REPO, "No repositories were specified."));
            }
            reposToUpdate = Result.ok(repos);
        }

        if (reposToUpdate.hasFailed()) {
            return Result.err(reposToUpdate.error());
        }

        for (Repo repo : reposToUpdate.value()) {
            Result<Statistics, DedupError> result = updateRepo(RepoManager.forRepo(repo, dedupConfig, fileSystem));
            if (result.hasFailed()) {
                return result.map(s -> -51, Function.identity());
            }
        }
        return Result.ok(0);
    }


    private Result<Statistics, DedupError> updateRepo(RepoManager repoManager) {
        Path root = Paths.get(repoManager.getRepo().absolutePath());
        if (!fileSystem.exists(root)) {
            return Result.err(DedupError.of(paxel.dedup.domain.model.errors.ErrorType.UPDATE_REPO, root + ": Repository directory does not exist."));
        }
        Result<Statistics, DedupError> load = repoManager.load();
        if (load.hasFailed()) {
            return load.mapError(f -> DedupError.of(paxel.dedup.domain.model.errors.ErrorType.UPDATE_REPO, repoManager.getRepoDir() + ": load failed", f.exception()));
        }
        Map<Path, RepoFile> remainingPaths = repoManager.stream().filter(r -> !r.missing()).collect(Collectors.toMap(r -> Paths.get(repoManager.getRepo().absolutePath(), r.relativePath()), Function.identity(), (old, update) -> update));
        StatisticPrinter progressPrinter = new StatisticPrinter();
        if (eventBus != null) {
            progressPrinter.setEventBus(eventBus);
        }
        TerminalProgress terminalProgress = prepProgress(progressPrinter);
        PrintStream originalErr = System.err;
        if (progress) {
            System.setErr(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                    // ignore
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    String msg = new String(b, off, len);
                    if (!msg.isBlank()) {
                        progressPrinter.setErrors(msg.trim());
                    }
                }
            }));
        }
        try (Sha1Hasher sha1Hasher = new Sha1Hasher(new HexFormatter(), Executors.newFixedThreadPool(threads))) {
            progressPrinter.set(repoManager.getRepo().name(), repoManager.getRepo().absolutePath());
            progressPrinter.setProgress("...stand by... collecting info");
            Statistics statistics = new Statistics(repoManager.getRepo().absolutePath());

            UpdateProgressPrinter observer = new UpdateProgressPrinter(remainingPaths, progressPrinter, repoManager, statistics, sha1Hasher, refreshFingerprints);
            new ResilientFileWalker(observer, fileSystem).walk(root);

            if (observer.getErrors() > 0 && observer.getAllDirs() <= 1 && observer.getFiles() == 0) {
                Throwable first = observer.getFirstError();
                Exception ex = first instanceof Exception ? (Exception) first : new Exception(first);
                return Result.err(DedupError.of(paxel.dedup.domain.model.errors.ErrorType.UPDATE_REPO, root + ": Repository walk failed: " + first.getMessage(), ex));
            }

            for (RepoFile value : remainingPaths.values()) {
                repoManager.addRepoFile(value.withMissing(true));
            }
            return Result.ok(statistics);
        } finally {
            if (progress) {
                System.setErr(originalErr);
            }
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
