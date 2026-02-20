package paxel.dedup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.repo.domain.repo.UpdateReposProcess;
import paxel.lib.Result;

import java.io.BufferedReader;
import java.io.IOException;
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
        @Override public boolean exists(Path path) { return true; }
        @Override public Stream<Path> list(Path dir) throws IOException { return Stream.empty(); }
        @Override public Stream<Path> walk(Path start) throws IOException { return Stream.empty(); }
        @Override public boolean isRegularFile(Path path) { return false; }
        @Override public boolean isDirectory(Path path) { return true; }
        @Override public boolean isSymbolicLink(Path path) { return false; }
        @Override public long size(Path path) throws IOException { return 0; }
        @Override public FileTime getLastModifiedTime(Path path) throws IOException { return FileTime.fromMillis(0); }
        @Override public InputStream newInputStream(Path path, StandardOpenOption... options) throws IOException { return null; }
        @Override public OutputStream newOutputStream(Path path, StandardOpenOption... options) throws IOException { return null; }
        @Override public BufferedReader newBufferedReader(Path path) throws IOException { return new BufferedReader(new StringReader("")); }
        @Override public byte[] readAllBytes(Path path) throws IOException { return new byte[0]; }
        @Override public void write(Path path, byte[] bytes, StandardOpenOption... options) throws IOException {}
        @Override public void delete(Path path) throws IOException {}
        @Override public boolean deleteIfExists(Path path) throws IOException { return false; }
        @Override public void createDirectories(Path path) throws IOException {}
        @Override public void copy(Path source, Path target, CopyOption... options) throws IOException {}
        @Override public void move(Path source, Path target, CopyOption... options) throws IOException {}
    }

    static class StubDedupConfig implements DedupConfig {
        @Override public Result<List<Repo>, paxel.dedup.domain.model.errors.OpenRepoError> getRepos() { 
            return Result.ok(List.of(new Repo("testRepo", "/mock", 1))); 
        }
        @Override public Result<Repo, paxel.dedup.domain.model.errors.OpenRepoError> getRepo(String name) { return Result.err(null); }
        @Override public Result<Repo, paxel.dedup.domain.model.errors.CreateRepoError> createRepo(String name, Path path, int indices) { return Result.err(null); }
        @Override public Result<Repo, paxel.dedup.domain.model.errors.ModifyRepoError> changePath(String name, Path path) { return Result.err(null); }
        @Override public Result<Boolean, paxel.dedup.domain.model.errors.DeleteRepoError> deleteRepo(String name) { return Result.ok(false); }
        @Override public Path getRepoDir() { return Paths.get("/mock/config"); }
        @Override public Result<Boolean, paxel.dedup.domain.model.errors.RenameRepoError> renameRepo(String oldName, String newName) { return Result.ok(false); }
    }

    @Test
    void testUpdateProcessWithStubbedFS() throws IOException {
        UpdateReposProcess process = new UpdateReposProcess(
                new CliParameter(),
                List.of("testRepo"),
                true, // all
                1,    // threads
                new StubDedupConfig(),
                new ObjectMapper(),
                false // progress
        );

        int result = process.update();
        assertThat(result).isEqualTo(0);
    }
}
