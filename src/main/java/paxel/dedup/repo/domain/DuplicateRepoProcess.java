package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.model.Repo;
import paxel.dedup.model.errors.OpenRepoError;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

import java.util.List;

@RequiredArgsConstructor
public class DuplicateRepoProcess {
    private final CliParameter cliParameter;
    private final List<String> names;
    private final boolean all;
    private final DedupConfig dedupConfig;
    private ObjectMapper objectMapper = new ObjectMapper();


    public int dupes() {
        if (all) {
            Result<List<Repo>, OpenRepoError> repos = dedupConfig.getRepos();
            if (repos.hasFailed()) {
                return -80;
            }
            dupe(repos.value());
        }
        return 0;
    }

    private void dupe(List<Repo> repos) {
        for (Repo repo : repos) {
            RepoManager r = new RepoManager(repo, dedupConfig, objectMapper);
        }

    }
}
