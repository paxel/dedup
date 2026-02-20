package paxel.dedup.repo.domain.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.*;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.adapter.out.serialization.JacksonLineCodec;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepoManagerTest {

    @TempDir
    Path tempDir;

    static class StubDedupConfig implements DedupConfig {
        private final Path repoDir;

        StubDedupConfig(Path repoDir) {
            this.repoDir = repoDir;
        }

        @Override public Result<List<Repo>, OpenRepoError> getRepos() { return Result.ok(List.of()); }
        @Override public Result<Repo, OpenRepoError> getRepo(String name) { return Result.err(null); }
        @Override public Result<Repo, CreateRepoError> createRepo(String name, Path path, int indices) { return Result.err(null); }
        @Override public Result<Repo, ModifyRepoError> changePath(String name, Path path) { return Result.err(null); }
        @Override public Result<Boolean, DeleteRepoError> deleteRepo(String name) { return Result.ok(false); }
        @Override public Path getRepoDir() { return repoDir; }
        @Override public Result<Boolean, RenameRepoError> renameRepo(String oldName, String newName) { return Result.ok(false); }
    }

    @Test
    void testLoadMultipleIndices() throws IOException {
        // Arrange
        Path repoBaseDir = tempDir.resolve("repos");
        Files.createDirectories(repoBaseDir);
        
        Repo repo = new Repo("testRepo", "/tmp/fake", 2);
        DedupConfig config = new StubDedupConfig(repoBaseDir);
        
        ObjectMapper mapper = new ObjectMapper();
        RepoManager repoManager = new RepoManager(repo, config, new JacksonLineCodec<>(mapper, RepoFile.class), new NioFileSystemAdapter());
        
        Path indexDir = repoBaseDir.resolve("testRepo");
        Files.createDirectories(indexDir);
        
        // Create 0.idx and 1.idx (plain JSON lines)
        RepoFile file0 = RepoFile.builder().hash("h0").relativePath("p0").size(0L).build();
        RepoFile file1 = RepoFile.builder().hash("h1").relativePath("p1").size(1L).build();

        Files.writeString(indexDir.resolve("0.idx"), mapper.writeValueAsString(file0) + "\n");
        Files.writeString(indexDir.resolve("1.idx"), mapper.writeValueAsString(file1) + "\n");
        
        // Act
        Result<Statistics, LoadError> loadResult = repoManager.load();
        
        // Assert
        assertThat(loadResult.hasFailed()).isFalse();
        
        // Check if both files are present in the stream
        List<RepoFile> files = repoManager.stream().toList();
        assertThat(files).hasSize(2);
        assertThat(files).extracting(RepoFile::relativePath).containsExactlyInAnyOrder("p0", "p1");
    }
}
