package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.time.DurationFormatUtils;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.logging.ConsoleLogger;
import paxel.lib.Result;

import java.nio.file.Paths;
import java.util.List;

@RequiredArgsConstructor
public class PruneReposProcess {
    private static final ConsoleLogger log = ConsoleLogger.getInstance();
    private final CliParameter cliParameter;
    private final List<String> names;
    private final boolean all;
    private final int indices;
    private final DedupConfig dedupConfig;
    private final boolean keepDeleted;
    private final Repo.Codec targetCodec;

    public int prune() {
        if (all) {
            return pruneAll();
        }

        return pruneByNames();
    }

    private int pruneByNames() {
        for (String name : names) {
            Result<Repo, DedupError> repoResult = dedupConfig.getRepo(name);
            if (repoResult.isSuccess()) {
                pruneRepo(repoResult.value());
            }
        }
        return 0;
    }

    private int pruneAll() {
        Result<List<Repo>, DedupError> lsResult = dedupConfig.getRepos();
        if (lsResult.hasFailed()) {
            DedupError err = lsResult.error();
            if (err.exception() != null) err.exception().printStackTrace();
            return -30;
        }
        for (Repo repo : lsResult.value()) {
            pruneRepo(repo);
        }
        return 0;
    }

    private void pruneRepo(Repo repo) {
        if (cliParameter.isVerbose()) {
            log.info("Pruning {}", repo.name());
        }

        Result<Statistics, DedupError> result = pruneRepo(RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter()), indices);

        if (result.hasFailed()) {
            log.error("Could not prune {} {}", repo.name(), result.error());
        } else if (cliParameter.isVerbose()) {
            result.value().forCounter((a, b) -> log.info("{}: {}", a, b));
            result.value().forTimer((a, b) -> log.info("{}: {}", a, DurationFormatUtils.formatDurationWords(b.toMillis(), true, true)));
        }
    }

    private Result<Statistics, DedupError> pruneRepo(RepoManager repoManager, int indices) {
        // create new Temporary Repo
        Repo oldRepo = repoManager.getRepo();
        String name = oldRepo.name();
        String newName = name + "_temp";
        Statistics statistics = new Statistics(newName);
        Result<Statistics, DedupError> load = repoManager.load();
        if (load.hasFailed()) {
            return load.mapError(f -> DedupError.of(ErrorType.UPDATE_REPO, repoManager.getRepoDir() + ": load failed", f.exception()));
        }
        Result<Repo, DedupError> repo = dedupConfig.createRepo(newName, Paths.get(oldRepo.absolutePath()), indices);
        // Stream existing files into the repo
        if (repo.isSuccess()) {
            if (targetCodec != null) {
                // Set codec on the temp repo before we start writing
                dedupConfig.setCodec(newName, targetCodec);
            }
            Result<Statistics, DedupError> loadNew = streamRepo(repoManager, statistics, repo.value());
            if (loadNew.hasFailed())
                return loadNew;
        }
        // rename old repo
        Result<Boolean, DedupError> result = dedupConfig.renameRepo(name, name + "_del");
        if (result.hasFailed())
            return result.mapError(f -> DedupError.of(ErrorType.UPDATE_REPO, f.describe(), f.exception()));
        if (!result.value()) {
            log.error("Could not rename {} to {}", name, name + "_del");
            return Result.err(DedupError.of(ErrorType.UPDATE_REPO, "Could not rename repo from " + name + " to " + name + "_del"));
        }
        // rename new repo
        Result<Boolean, DedupError> otherResult = dedupConfig.renameRepo(newName, name);
        if (otherResult.hasFailed())
            return otherResult.mapError(f -> DedupError.of(ErrorType.UPDATE_REPO, f.describe(), f.exception()));
        if (!otherResult.value()) {
            log.error("Could not rename {} to {}", newName, name);
            return Result.err(DedupError.of(ErrorType.UPDATE_REPO, "Could not rename repo from " + newName + " to " + name));
        }
        //delete old repo
        Result<Boolean, DedupError> deleteResult = dedupConfig.deleteRepo(name + "_del");
        if (deleteResult.hasFailed())
            return result.mapError(f -> DedupError.of(ErrorType.UPDATE_REPO, f.describe(), f.exception()));
        if (!deleteResult.value()) {
            log.error("Could not delete {}", name + "_del");
            return Result.err(DedupError.of(ErrorType.UPDATE_REPO, "Could not delete " + name + "_del"));
        }

        return Result.ok(statistics);
    }

    private Result<Statistics, DedupError> streamRepo(RepoManager repoManager, Statistics statistics, Repo newRepo) {
        RepoManager temp = RepoManager.forRepo(newRepo, dedupConfig, new NioFileSystemAdapter());
        Result<Statistics, DedupError> loadNew = temp.load();
        if (loadNew.hasFailed()) {
            return loadNew.mapError(f -> DedupError.of(ErrorType.UPDATE_REPO, repoManager.getRepoDir() + ": load failed", f.exception()));
        }
        repoManager.stream().filter(f -> keepDeleted || !f.missing()).forEach(repoFile -> {
            Result<RepoFile, DedupError> result = temp.addRepoFile(repoFile);
            if (result.isSuccess()) {
                if (result.value() != null)
                    statistics.inc("files");
                else {
                    statistics.inc("pruned");
                }
            } else {
                statistics.inc("failed");
            }
        });
        return Result.ok(statistics);
    }
}
