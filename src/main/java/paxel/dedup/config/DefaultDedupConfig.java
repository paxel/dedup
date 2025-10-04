package paxel.dedup.config;

import paxel.lib.Result;

import java.util.List;
import java.util.Optional;

public class DefaultDedupConfig implements DedupConfig {
    @Override
    public List<Repo> getRepos() {
        return List.of();
    }

    @Override
    public Optional<Repo> getRepo(String name) {
        return Optional.empty();
    }

    @Override
    public Result<Repo, CreateRepoError> createRepo(String name) {
        return Result.err(new CreateRepoError());
    }

    @Override
    public Result<Boolean, DeleteRepoError> deleteRepo(Repo repo) {
        return Result.err(new DeleteRepoError());
    }
}
