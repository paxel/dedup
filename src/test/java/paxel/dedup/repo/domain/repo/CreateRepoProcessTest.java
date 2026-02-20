package paxel.dedup.repo.domain.repo;

import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.*;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateRepoProcessTest {

    private static class StubConfig implements DedupConfig {
        Result<Repo, CreateRepoError> toReturn;

        @Override public Result<List<Repo>, OpenRepoError> getRepos() { return Result.ok(List.of()); }
        @Override public Result<Repo, OpenRepoError> getRepo(String name) { return Result.err(null); }
        @Override public Result<Repo, CreateRepoError> createRepo(String name, Path path, int indices) { return toReturn; }
        @Override public Result<Repo, ModifyRepoError> changePath(String name, Path path) { return Result.err(null); }
        @Override public Result<Boolean, DeleteRepoError> deleteRepo(String name) { return Result.ok(false); }
        @Override public Path getRepoDir() { return Path.of("/tmp/config"); }
        @Override public Result<Boolean, RenameRepoError> renameRepo(String oldName, String newName) { return Result.ok(false); }
    }

    @Test
    void create_success_prints_when_verbose_and_returns_zero() {
        // Arrange
        StubConfig cfg = new StubConfig();
        cfg.toReturn = Result.ok(new Repo("repoA", "/data/repoA", 2));
        CliParameter params = new CliParameter();
        params.setVerbose(true);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outBuf));
        try {
            // Act
            int code = new CreateRepoProcess(params, "repoA", "/data/repoA", 2, cfg).create();

            // Assert
            assertThat(code).isEqualTo(0);
            String stdout = outBuf.toString();
            assertThat(stdout)
                    .contains("::Creating Repo at '/tmp/config'")
                    .contains("::Created Repo 'repoA: /data/repoA'");
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void create_failure_with_ioerror_prints_error_and_returns_minus10() {
        // Arrange
        StubConfig cfg = new StubConfig();
        IOException ioEx = new IOException("boom");
        Path errPath = Path.of("/tmp/bad");
        cfg.toReturn = Result.err(CreateRepoError.ioError(errPath, ioEx));

        CliParameter params = new CliParameter();
        params.setVerbose(false);

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(errBuf));
        try {
            // Act
            int code = new CreateRepoProcess(params, "repoA", "/data/repoA", 2, cfg).create();

            // Assert
            assertThat(code).isEqualTo(-10);
            String stderr = errBuf.toString();
            assertThat(stderr).contains(errPath.toString()).contains("not a valid repo relativePath");
        } finally {
            System.setErr(oldErr);
        }
    }
}
