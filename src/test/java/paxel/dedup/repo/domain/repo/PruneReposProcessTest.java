package paxel.dedup.repo.domain.repo;

import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.*;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;
import paxel.dedup.infrastructure.adapter.out.serialization.JacksonLineCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PruneReposProcessTest {

    private static class StubConfig implements DedupConfig {
        Result<List<Repo>, OpenRepoError> reposResult = Result.ok(List.of());
        Result<Repo, OpenRepoError> repoByName = Result.err(null);

        @Override public Result<List<Repo>, OpenRepoError> getRepos() { return reposResult; }
        @Override public Result<Repo, OpenRepoError> getRepo(String name) { return repoByName; }
        @Override public Result<Repo, CreateRepoError> createRepo(String name, Path path, int indices) { return Result.err(null); }
        @Override public Result<Repo, ModifyRepoError> changePath(String name, Path path) { return Result.err(null); }
        @Override public Result<Boolean, DeleteRepoError> deleteRepo(String name) { return Result.ok(false); }
        @Override public Path getRepoDir() { return Path.of("/tmp/config"); }
        @Override public Result<Boolean, RenameRepoError> renameRepo(String oldName, String newName) { return Result.ok(false); }
    }

    @Test
    void pruneAll_when_listing_repos_fails_returns_minus_30() {
        // Arrange
        CliParameter cli = new CliParameter();
        cli.setVerbose(false);
        StubConfig cfg = new StubConfig();
        IOException ioEx = new IOException("boom");
        Path errPath = Path.of("/tmp/bad");
        cfg.reposResult = Result.err(OpenRepoError.ioError(errPath, ioEx));

        // Act
        int code = new PruneReposProcess(cli, List.of(), true, 2, cfg, new JacksonLineCodec<>(new com.fasterxml.jackson.databind.ObjectMapper(), paxel.dedup.domain.model.RepoFile.class)).prune();

        // Assert
        assertThat(code).isEqualTo(-30);
    }

    @Test
    void pruneByNames_returns_zero_even_if_repos_missing() {
        // Arrange
        CliParameter cli = new CliParameter();
        cli.setVerbose(false);
        StubConfig cfg = new StubConfig();
        cfg.repoByName = Result.err(OpenRepoError.ioError(Path.of("/x"), new IOException("nf")));

        // Act
        int code = new PruneReposProcess(cli, new ArrayList<>(List.of("missing1", "missing2")), false, 2, cfg, new JacksonLineCodec<>(new com.fasterxml.jackson.databind.ObjectMapper(), paxel.dedup.domain.model.RepoFile.class)).prune();

        // Assert
        assertThat(code).isEqualTo(0);
    }
}
