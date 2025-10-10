package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.Repo;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.model.errors.OpenRepoError;
import paxel.dedup.model.errors.UpdateRepoError;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

import java.io.IOException;
import java.util.List;

public class PruneReposProcess {
    public int prune(List<String> names, boolean all, CliParameter cliParameter, int indices) {
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
                return -30;
            }
            for (Repo repo : lsResult.value()) {
                pruneRepo(new RepoManager(repo, dedupConfig, objectMapper, cliParameter), indices);
            }
            return 0;
        }

        for (String name : names) {
            Result<Repo, OpenRepoError> getRepoResult = dedupConfig.getRepo(name);
            if (getRepoResult.isSuccess()) {
                Result<Statistics, UpdateRepoError> statisticsUpdateRepoErrorResult = pruneRepo(new RepoManager(getRepoResult.value(), dedupConfig, objectMapper, cliParameter), indices);
            }
        }
        return 0;
    }

    private Result<Statistics, UpdateRepoError> pruneRepo(RepoManager repoManager, int indices) {
        // create new Temporary Repo
        // Stream existing files into the repo
        // rename old repo
        // rename new repo
        //delete old repo

        return null;
    }
}
