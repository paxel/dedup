package paxel.dedup.repo.domain.repo;

import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PruneReposProcessTest {

    private static class StubConfig implements DedupConfig {
        Result<List<Repo>, DedupError> reposResult = Result.ok(List.of());
        Result<Repo, DedupError> repoByName = Result.err(null);

        @Override
        public Result<List<Repo>, DedupError> getRepos() {
            return reposResult;
        }

        @Override
        public Result<Repo, DedupError> getRepo(String name) {
            return repoByName;
        }

        @Override
        public Result<Repo, DedupError> createRepo(String name, Path path, int indices) {
            return Result.err(null);
        }

        @Override
        public Result<Repo, DedupError> changePath(String name, Path path) {
            return Result.err(null);
        }

        @Override
        public Result<Boolean, DedupError> deleteRepo(String name) {
            return Result.ok(false);
        }

        @Override
        public Path getRepoDir() {
            return Path.of("/tmp/config");
        }

        @Override
        public Result<Boolean, DedupError> renameRepo(String oldName, String newName) {
            return Result.ok(false);
        }
    }

    @Test
    void pruneAll_when_listing_repos_fails_returns_minus_30() {
        // Arrange
        CliParameter cli = new CliParameter();
        cli.setVerbose(false);
        StubConfig cfg = new StubConfig();
        IOException ioEx = new IOException("boom");
        Path errPath = Path.of("/tmp/bad");
        cfg.reposResult = Result.err(DedupError.of(ErrorType.OPEN_REPO, errPath + " Invalid", ioEx));

        // Act
        Result<Integer, DedupError> result = new PruneReposProcess(cli, List.of(), true, 2, cfg, false, null).prune();

        // Assert
        assertThat(result.hasFailed()).isTrue();
        assertThat(result.error().type()).isEqualTo(ErrorType.OPEN_REPO);
    }

    @Test
    void pruneByNames_returns_zero_even_if_repos_missing() {
        // Arrange
        CliParameter cli = new CliParameter();
        cli.setVerbose(false);
        StubConfig cfg = new StubConfig();
        cfg.repoByName = Result.err(DedupError.of(ErrorType.OPEN_REPO, Path.of("/x") + " Invalid", new IOException("nf")));

        // Act
        Result<Integer, DedupError> result = new PruneReposProcess(cli, new ArrayList<>(List.of("missing1", "missing2")), false, 2, cfg, false, null).prune();

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(0);
    }
}
