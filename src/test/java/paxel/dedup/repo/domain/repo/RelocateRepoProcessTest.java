package paxel.dedup.repo.domain.repo;

import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelocateRepoProcessTest {

    private static class StubConfig implements DedupConfig {
        Result<Repo, DedupError> toReturn;

        @Override
        public Result<List<Repo>, DedupError> getRepos() {
            return Result.ok(List.of());
        }

        @Override
        public Result<Repo, DedupError> getRepo(String name) {
            return Result.err(null);
        }

        @Override
        public Result<Repo, DedupError> createRepo(String name, Path path, int indices) {
            return Result.err(null);
        }

        @Override
        public Result<Repo, DedupError> changePath(String name, Path path) {
            return toReturn;
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
    void relocate_success_verbose_prints_messages_and_returns_zero() {
        StubConfig cfg = new StubConfig();
        cfg.toReturn = Result.ok(new Repo("r1", "/data/r1", 2));

        CliParameter params = new CliParameter();
        params.setVerbose(true);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outBuf));
        try {
            int code = new RelocateRepoProcess(params, "r1", "/new/path", cfg).move().value();

            assertThat(code).isEqualTo(0);
            String stdout = outBuf.toString();
            assertThat(stdout)
                    .contains("Relocating r1 path to /new/path")
                    .contains("Relocated r1 path to /new/path");
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void relocate_failure_prints_error_and_returns_minus70() {
        StubConfig cfg = new StubConfig();
        java.io.IOException io = new java.io.IOException("boom");
        cfg.toReturn = Result.err(DedupError.of(ErrorType.MODIFY_REPO, "/new/path modify failed", io));

        CliParameter params = new CliParameter();
        params.setVerbose(false);

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(errBuf));
        try {
            Result<Integer, DedupError> result = new RelocateRepoProcess(params, "r1", "/new/path", cfg).move();

            assertThat(result.hasFailed()).isTrue();
            assertThat(result.error().description()).contains("/new/path");
        } finally {
            System.setErr(oldErr);
        }
    }
}
