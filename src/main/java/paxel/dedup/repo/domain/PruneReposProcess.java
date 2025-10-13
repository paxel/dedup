package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.time.DurationFormatUtils;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.model.Repo;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.*;
import paxel.dedup.model.utils.DummyHasher;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

@RequiredArgsConstructor
public class PruneReposProcess {
    private final CliParameter cliParameter;
    private final List<String> names;
    private final boolean all;
    private final int indices;
    private final DedupConfig dedupConfig;
    private final ObjectMapper objectMapper;

    public int prune() {
        if (all) {
            return pruneAll();
        }

        return pruneByNames();
    }

    private int pruneByNames() {
        for (String name : names) {
            Result<Repo, OpenRepoError> repoResult = dedupConfig.getRepo(name);
            if (repoResult.isSuccess()) {
                pruneRepo(repoResult.value());
            }
        }
        return 0;
    }

    private int pruneAll() {
        Result<List<Repo>, OpenRepoError> lsResult = dedupConfig.getRepos();
        if (lsResult.hasFailed()) {
            IOException ioException = lsResult.error().ioException();
            if (ioException != null) {
                ioException.printStackTrace();
            }
            return -30;
        }
        for (Repo repo : lsResult.value()) {
            pruneRepo(repo);
        }
        return 0;
    }

    private void pruneRepo(Repo repo) {
        if (cliParameter.isVerbose()) {
            System.out.println("Pruning " + repo.name());
        }

        Result<Statistics, UpdateRepoError> result = pruneRepo(new RepoManager(repo, dedupConfig, objectMapper, cliParameter, new DummyHasher()), indices);

        if (result.hasFailed()) {
            System.err.println("Could not prune " + repo.name() + " " + result.error());
        } else if (cliParameter.isVerbose()) {
            result.value().forCounter((a, b) -> System.out.println(a + ": " + b));
            result.value().forTimer((a, b) -> System.out.println(a + ": " + DurationFormatUtils.formatDurationWords(b.toMillis(), true, true)));
        }
    }

    private Result<Statistics, UpdateRepoError> pruneRepo(RepoManager repoManager, int indices) {
        // create new Temporary Repo
        Repo oldRepo = repoManager.getRepo();
        String name = oldRepo.name();
        String newName = name + "_temp";
        Statistics statistics = new Statistics(newName);
        Result<Statistics, LoadError> load = repoManager.load();
        if (load.hasFailed()) {
            return load.mapError(f -> new UpdateRepoError(repoManager.getRepoDir(), load.error().ioException()));
        }
        Result<Repo, CreateRepoError> repo = dedupConfig.createRepo(newName, Paths.get(oldRepo.absolutePath()), indices);
        // Stream existing files into the repo
        if (repo.isSuccess()) {
            Result<Statistics, UpdateRepoError> loadNew = streamRepo(repoManager, statistics, repo.value());
            if (loadNew != null) {
                return loadNew;
            }
        }
        // rename old repo
        dedupConfig.renameRepo(name, name + "_del");
        // rename new repo
        dedupConfig.renameRepo(newName, name);
        //delete old repo
        dedupConfig.deleteRepo(name + "_del");

        return Result.ok(statistics);
    }

    private Result<Statistics, UpdateRepoError> streamRepo(RepoManager repoManager, Statistics statistics, Repo newRepo) {
        RepoManager temp = new RepoManager(newRepo, dedupConfig, new ObjectMapper(), cliParameter, new DummyHasher());
        Result<Statistics, LoadError> loadNew = temp.load();
        if (loadNew.hasFailed()) {
            return loadNew.mapError(f -> new UpdateRepoError(repoManager.getRepoDir(), loadNew.error().ioException()));
        }
        repoManager.stream().filter(f -> !f.missing()).forEach(repoFile -> {
            Result<Boolean, WriteError> booleanWriteErrorResult = temp.addRepoFile(repoFile);
            if (booleanWriteErrorResult.isSuccess()) {
                if (booleanWriteErrorResult.value())
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
