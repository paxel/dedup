package paxel.dedup.repo.domain.repo;

import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListReposProcessTest {

    private static class StubConfig implements DedupConfig {
        Result<List<Repo>, DedupError> toReturn;

        @Override
        public Result<List<Repo>, DedupError> getRepos() {
            return toReturn;
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
    void list_success_non_verbose_sorted_and_without_indices() {
        // Arrange
        StubConfig cfg = new StubConfig();
        Repo a = new Repo("a", "/data/a", 3);
        Repo b = new Repo("b", "/data/b", 1);
        // Intentionally unsorted input to verify sort-by-name
        cfg.toReturn = Result.ok(List.of(b, a));

        CliParameter params = new CliParameter();
        params.setVerbose(false);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outBuf));
        try {
            int code = new ListReposProcess(params, cfg).list().value();
            assertThat(code).isEqualTo(0);
            String[] lines = outBuf.toString().trim().split("\n");
            assertThat(lines).containsExactly(
                    "a: /data/a",
                    "b: /data/b"
            );
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void list_success_verbose_includes_indices() {
        // Arrange
        StubConfig cfg = new StubConfig();
        Repo a = new Repo("a", "/data/a", 2);
        cfg.toReturn = Result.ok(List.of(a));

        CliParameter params = new CliParameter();
        params.setVerbose(true);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outBuf));
        try {
            int code = new ListReposProcess(params, cfg).list().value();
            assertThat(code).isEqualTo(0);
            String stdout = outBuf.toString().trim();
            assertThat(stdout).isEqualTo("a: /data/a index files: 2");
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void list_failure_with_ioerror_prints_error_and_returns_minus20() {
        // Arrange
        StubConfig cfg = new StubConfig();
        IOException ioEx = new IOException("bad");
        Path errPath = Path.of("/tmp/repos.yml");
        cfg.toReturn = Result.err(DedupError.of(ErrorType.OPEN_REPO, errPath + " Invalid", ioEx));

        CliParameter params = new CliParameter();
        params.setVerbose(false);

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(errBuf));
        try {
            Result<Integer, DedupError> result = new ListReposProcess(params, cfg).list();
            assertThat(result.hasFailed()).isTrue();
            assertThat(result.error().description()).contains(errPath.toString()).contains("Invalid");
        } finally {
            System.setErr(oldErr);
        }
    }
}
