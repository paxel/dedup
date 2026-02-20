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

class CopyRepoProcessTest {

    private static class StubConfig implements DedupConfig {
        Result<Repo, ModifyRepoError> changePathResult = Result.ok(new Repo("dest", "/repo/dest", 2));

        @Override public Result<List<Repo>, OpenRepoError> getRepos() { return Result.ok(List.of()); }
        @Override public Result<Repo, OpenRepoError> getRepo(String name) { return Result.err(null); }
        @Override public Result<Repo, CreateRepoError> createRepo(String name, Path path, int indices) { return Result.err(null); }
        @Override public Result<Repo, ModifyRepoError> changePath(String name, Path path) { return changePathResult; }
        @Override public Result<Boolean, DeleteRepoError> deleteRepo(String name) { return Result.ok(false); }
        @Override public Path getRepoDir() { return Path.of("/tmp/config"); }
        @Override public Result<Boolean, RenameRepoError> renameRepo(String oldName, String newName) { return Result.ok(false); }
    }

    /**
     * When source and destination names are the same, process should abort early with -62
     * and not attempt any filesystem operations.
     */
    @Test
    void copy_same_source_and_destination_returns_minus_62() {
        CliParameter cli = new CliParameter();
        cli.setVerbose(false);
        StubConfig cfg = new StubConfig();

        int code = new CopyRepoProcess(cli, "same", "same", "/path", cfg).copy();

        assertThat(code).isEqualTo(-62);
    }

    /**
     * If copyDirectory reports IO errors, the process should return -61.
     * We override copyDirectory to avoid touching the real filesystem.
     */
    @Test
    void copy_with_copy_errors_returns_minus_61() {
        CliParameter cli = new CliParameter();
        cli.setVerbose(false);
        StubConfig cfg = new StubConfig();

        CopyRepoProcess proc = new CopyRepoProcess(cli, "src", "dst", "/p", cfg) {
            @Override public List<IOException> copyDirectory(Path from, Path to) {
                return List.of(new IOException("boom"));
            }
        };

        int code = proc.copy();
        assertThat(code).isEqualTo(-61);
    }

    /**
     * When copy succeeds but changing the destination path fails, return -60.
     */
    @Test
    void copy_with_changePath_failure_returns_minus_60() {
        CliParameter cli = new CliParameter();
        cli.setVerbose(false);
        StubConfig cfg = new StubConfig();
        IOException io = new IOException("bad path");
        cfg.changePathResult = Result.err(ModifyRepoError.ioError(Path.of("/bad"), io));

        CopyRepoProcess proc = new CopyRepoProcess(cli, "src", "dst", "/p", cfg) {
            @Override public List<IOException> copyDirectory(Path from, Path to) { return List.of(); }
        };

        int code = proc.copy();
        assertThat(code).isEqualTo(-60);
    }

    /**
     * Happy path: verbose prints cloning messages and returns 0.
     */
    @Test
    void copy_success_verbose_prints_and_returns_zero() {
        CliParameter cli = new CliParameter();
        cli.setVerbose(true);
        StubConfig cfg = new StubConfig();

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outBuf));
        try {
            CopyRepoProcess proc = new CopyRepoProcess(cli, "src", "dst", "/p", cfg) {
                @Override public List<IOException> copyDirectory(Path from, Path to) { return List.of(); }
            };

            int code = proc.copy();
            assertThat(code).isEqualTo(0);

            String out = outBuf.toString();
            assertThat(out).contains("cloning src to dst");
        } finally {
            System.setOut(oldOut);
        }
    }
}
