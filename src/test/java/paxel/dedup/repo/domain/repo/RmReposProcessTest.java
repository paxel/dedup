package paxel.dedup.repo.domain.repo;

import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.*;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RmReposProcessTest {

    private static class StubConfig implements DedupConfig {
        Result<Boolean, DeleteRepoError> del;

        @Override public Result<List<Repo>, OpenRepoError> getRepos() { return Result.ok(List.of()); }
        @Override public Result<Repo, OpenRepoError> getRepo(String name) { return Result.err(null); }
        @Override public Result<Repo, CreateRepoError> createRepo(String name, Path path, int indices) { return Result.err(null); }
        @Override public Result<Repo, ModifyRepoError> changePath(String name, Path path) { return Result.err(null); }
        @Override public Result<Boolean, DeleteRepoError> deleteRepo(String name) { return del; }
        @Override public Path getRepoDir() { return Path.of("/tmp/config"); }
        @Override public Result<Boolean, RenameRepoError> renameRepo(String oldName, String newName) { return Result.ok(false); }
    }

    @Test
    void delete_success_verbose_prints_and_returns_zero() {
        // Arrange
        StubConfig cfg = new StubConfig();
        cfg.del = Result.ok(true);
        CliParameter params = new CliParameter();
        params.setVerbose(true);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outBuf));
        try {
            // Act
            int code = new RmReposProcess(params, "r1", cfg).delete();

            // Assert
            assertThat(code).isEqualTo(0);
            String stdout = outBuf.toString();
            assertThat(stdout).contains("Deleting r1 from /tmp/config").contains("Deleted r1");
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void delete_failure_prints_errors_and_returns_minus40() {
        // Arrange
        StubConfig cfg = new StubConfig();
        DeleteRepoError err = DeleteRepoError.ioErrors(Path.of("/tmp/some"), List.of(new IllegalStateException("x"), new RuntimeException("y")));
        cfg.del = Result.err(err);
        CliParameter params = new CliParameter();
        params.setVerbose(false);

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(errBuf));
        try {
            // Act
            int code = new RmReposProcess(params, "r1", cfg).delete();

            // Assert
            assertThat(code).isEqualTo(-40);
            String stderr = errBuf.toString();
            assertThat(stderr).contains("While deleting r1 2 exceptions happened");
        } finally {
            System.setErr(oldErr);
        }
    }
}
