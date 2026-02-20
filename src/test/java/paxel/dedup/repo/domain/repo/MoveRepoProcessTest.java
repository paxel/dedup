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

class MoveRepoProcessTest {

    private static class StubConfig implements DedupConfig {
        Result<Boolean, RenameRepoError> toReturn;

        @Override public Result<java.util.List<Repo>, OpenRepoError> getRepos() { return Result.ok(List.of()); }
        @Override public Result<Repo, OpenRepoError> getRepo(String name) { return Result.err(null); }
        @Override public Result<Repo, CreateRepoError> createRepo(String name, Path path, int indices) { return Result.err(null); }
        @Override public Result<Repo, ModifyRepoError> changePath(String name, Path path) { return Result.err(null); }
        @Override public Result<Boolean, DeleteRepoError> deleteRepo(String name) { return Result.ok(false); }
        @Override public Path getRepoDir() { return Path.of("/tmp/config"); }
        @Override public Result<Boolean, RenameRepoError> renameRepo(String oldName, String newName) { return toReturn; }
    }

    @Test
    void move_success_verbose_prints_messages_and_returns_zero() {
        StubConfig cfg = new StubConfig();
        cfg.toReturn = Result.ok(true);

        CliParameter params = new CliParameter();
        params.setVerbose(true);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outBuf));
        try {
            int code = new MoveRepoProcess(params, "oldName", "newName", cfg).move();

            assertThat(code).isEqualTo(0);
            String stdout = outBuf.toString();
            assertThat(stdout)
                    .contains("Renaming repo oldName to newName")
                    .contains("Renamed repo oldName to newName");
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void move_failure_prints_error_and_returns_minus90() {
        StubConfig cfg = new StubConfig();
        java.io.IOException io = new java.io.IOException("boom");
        RenameRepoError err = RenameRepoError.ioError(Path.of("/tmp/config/newName"), io);
        cfg.toReturn = Result.err(err);

        CliParameter params = new CliParameter();
        params.setVerbose(false);

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(errBuf));
        try {
            int code = new MoveRepoProcess(params, "oldName", "newName", cfg).move();

            assertThat(code).isEqualTo(-90);
            String stderr = errBuf.toString();
            assertThat(stderr)
                    .contains("Renaming repo oldName to newName has failed:")
                    .contains("/tmp/config/newName");
        } finally {
            System.setErr(oldErr);
        }
    }
}
