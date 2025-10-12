package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.Repo;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.*;
import paxel.dedup.model.utils.HexFormatter;
import paxel.dedup.model.utils.Sha1Hasher;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class PruneReposProcess {
    private DedupConfig dedupConfig;
    private CliParameter cliParameter;

    public int prune(List<String> names, boolean all, CliParameter cliParameter, int indices) {
        this.cliParameter = cliParameter;
        Result<DedupConfig, CreateConfigError> configResult = DedupConfigFactory.create();
        if (configResult.hasFailed()) {
            new DedupConfigErrorHandler().dump(configResult.error());
            return -1;
        }
        ObjectMapper objectMapper = new ObjectMapper();

        dedupConfig = configResult.value();
        if (all) {
            Result<List<Repo>, OpenRepoError> lsResult = dedupConfig.getRepos();
            if (lsResult.hasFailed()) {
                IOException ioException = lsResult.error().ioException();
                if (ioException != null) {
                    ioException.printStackTrace();
                }
                return -30;
            }
            for (Repo repo : lsResult.value()) {
                Result<Statistics, UpdateRepoError> result = pruneRepo(new RepoManager(repo, dedupConfig, objectMapper, cliParameter, new Sha1Hasher(new HexFormatter())), indices);
                if (result.hasFailed()) {
                    System.err.println("Could not prune " + repo.name() + " " + result.error());
                }
            }
            return 0;
        }

        for (String name : names) {
            Result<Repo, OpenRepoError> repoResult = dedupConfig.getRepo(name);
            if (repoResult.isSuccess()) {
                Result<Statistics, UpdateRepoError> result = pruneRepo(new RepoManager(repoResult.value(), dedupConfig, objectMapper, cliParameter, new Sha1Hasher(new HexFormatter())), indices);
                if (result.hasFailed()) {
                    System.err.println("Could not prune " + repoResult.value().name() + " " + result.error());
                }
            }
        }
        return 0;
    }

    private Result<Statistics, UpdateRepoError> pruneRepo(RepoManager repoManager, int indices) {
        // create new Temporary Repo
        Repo oldRepo = repoManager.getRepo();
        String name = oldRepo.name();
        String newName = name + "_temp";
        Statistics value = new Statistics(newName);
        Result<Statistics, LoadError> load = repoManager.load();
        if (load.hasFailed()) {
            return load.mapError(f -> new UpdateRepoError(repoManager.getRepoDir(), load.error().ioException()));
        }
        Result<Repo, CreateRepoError> repo = dedupConfig.createRepo(newName, Paths.get(oldRepo.absolutePath()), indices);
        // Stream existing files into the repo
        if (repo.isSuccess()) {
            RepoManager temp = new RepoManager(repo.value(), dedupConfig, new ObjectMapper(), cliParameter, new Sha1Hasher(new HexFormatter()));
            Result<Statistics, LoadError> loadNew = temp.load();
            if (loadNew.hasFailed()) {
                return loadNew.mapError(f -> new UpdateRepoError(repoManager.getRepoDir(), loadNew.error().ioException()));
            }
            repoManager.stream().filter(f -> !f.missing()).forEach(repoFile -> {
                Result<Boolean, WriteError> booleanWriteErrorResult = temp.addRepoFile(repoFile);
                if (booleanWriteErrorResult.isSuccess()) {
                    if (booleanWriteErrorResult.value())
                        value.inc("added");
                    else {
                        value.inc("pruned");
                    }
                } else {
                    value.inc("failed");
                }
            });
        }
        // rename old repo
        dedupConfig.renameRepo(name, name + "_del");
        // rename new repo
        dedupConfig.renameRepo(newName, name);
        //delete old repo
        dedupConfig.deleteRepo(name + "_del");
        value.forCounter((a, b) -> {
            System.out.println(a + ": " + b);
        });
        return Result.ok(value);
    }
}
