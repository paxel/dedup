package paxel.dedup.config;

import lombok.extern.slf4j.Slf4j;
import paxel.lib.Result;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record DefaultDedupConfig(Path repoRootPath) implements DedupConfig {

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

    @Override
    public Path getRepoDir() {
        return repoRootPath;
    }
}
