package paxel.dedup;

import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.repo.domain.repo.DuplicateRepoProcess;
import paxel.dedup.repo.domain.repo.UpdateReposProcess;
import paxel.lib.Result;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MockedFileProcessingTest {

    static class StubFileSystem implements FileSystem {
        @Override
        public boolean exists(Path path) {
            return true;
        }

        @Override
        public Stream<Path> list(Path dir) {
            return Stream.empty();
        }

        @Override
        public Stream<Path> walk(Path start) {
            return Stream.empty();
        }

        @Override
        public boolean isRegularFile(Path path) {
            return false;
        }

        @Override
        public boolean isDirectory(Path path) {
            return true;
        }

        @Override
        public boolean isSymbolicLink(Path path) {
            return false;
        }

        @Override
        public long size(Path path) {
            return 0;
        }

        @Override
        public FileTime getLastModifiedTime(Path path) {
            return FileTime.fromMillis(0);
        }

        @Override
        public InputStream newInputStream(Path path, StandardOpenOption... options) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream newOutputStream(Path path, StandardOpenOption... options) {
            return new java.io.ByteArrayOutputStream();
        }

        @Override
        public BufferedReader newBufferedReader(Path path) {
            return new BufferedReader(new StringReader(""));
        }

        @Override
        public byte[] readAllBytes(Path path) {
            return new byte[0];
        }

        @Override
        public void write(Path path, byte[] bytes, StandardOpenOption... options) {
        }

        @Override
        public void delete(Path path) {
        }

        @Override
        public boolean deleteIfExists(Path path) {
            return false;
        }

        @Override
        public void createDirectories(Path path) {
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) {
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) {
        }
    }

    static class StubDedupConfig implements DedupConfig {
        @Override
        public Result<List<Repo>, DedupError> getRepos() {
            return Result.ok(List.of(new Repo("testRepo", "/mock", 1)));
        }

        @Override
        public Result<Repo, DedupError> getRepo(String name) {
            return Result.ok(new Repo(name, "/mock", 1));
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
            return Paths.get("/mock/config");
        }

        @Override
        public Result<Boolean, DedupError> renameRepo(String oldName, String newName) {
            return Result.ok(false);
        }

        @Override
        public Result<Repo, DedupError> setRepoConfig(String name, Repo.Codec codec) {
            return Result.err(paxel.dedup.domain.model.errors.DedupError.of(paxel.dedup.domain.model.errors.ErrorType.MODIFY_REPO, "not implemented"));
        }
    }

    @Test
    void testUpdateProcessWithStubbedFS() {
        UpdateReposProcess process = new UpdateReposProcess(
                new CliParameter(),
                List.of("testRepo"),
                true, // all
                1,    // threads
                new StubDedupConfig(),
                false, // progress
                false  // refreshFingerprints
        );

        int result = process.update();
        assertThat(result).isEqualTo(0);
    }

    @Test
    void testDuplicateProcessWithStubbedConfig() {
        DuplicateRepoProcess process = new DuplicateRepoProcess(
                new CliParameter(),
                List.of("testRepo"),
                true,
                new StubDedupConfig(),
                null,
                DuplicateRepoProcess.DupePrintMode.PRINT,
                new StubFileSystem()
        );
        Result<Integer, DedupError> result = process.dupes();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(0);
    }
}
