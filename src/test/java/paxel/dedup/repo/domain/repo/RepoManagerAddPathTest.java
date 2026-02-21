package paxel.dedup.repo.domain.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.domain.model.MimetypeProvider;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.adapter.out.serialization.JsonLineCodec;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RepoManagerAddPathTest {

    @TempDir
    Path tempDir;

    static class StubDedupConfig implements DedupConfig {
        private final Path repoDir;

        StubDedupConfig(Path repoDir) {
            this.repoDir = repoDir;
        }

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
            return Result.err(null);
        }

        @Override
        public Result<Boolean, DedupError> deleteRepo(String name) {
            return Result.ok(false);
        }

        @Override
        public Path getRepoDir() {
            return repoDir;
        }

        @Override
        public Result<Boolean, DedupError> renameRepo(String oldName, String newName) {
            return Result.ok(false);
        }
    }

    @Test
    void addPath_writes_small_file_entry_to_correct_index_with_expected_fields() throws Exception {
        // Arrange
        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path small = dataDir.resolve("tiny.txt");
        Files.writeString(small, "abcdef"); // 6 bytes (<20) triggers in-memory hash path

        Path configRoot = tempDir.resolve("config");
        Files.createDirectories(configRoot.resolve("r2"));

        Repo repo = new Repo("r2", dataDir.toString(), 2); // 2 indices, size%2 -> index selection
        DedupConfig cfg = new StubDedupConfig(configRoot);
        ObjectMapper mapper = new ObjectMapper();
        FileSystem fs = new NioFileSystemAdapter();
        RepoManager repoManager = new RepoManager(repo, cfg, new JsonLineCodec<>(mapper, RepoFile.class), fs);

        // Load will create 0.idx and 1.idx
        Result<Statistics, DedupError> load = repoManager.load();
        assertThat(load.hasFailed()).isFalse();

        // Stub FileHasher returns a constant hash immediately
        paxel.dedup.domain.model.FileHasher hasher = new paxel.dedup.domain.model.FileHasher() {
            @Override
            public CompletableFuture<Result<String, DedupError>> hash(Path path) {
                return CompletableFuture.completedFuture(Result.ok("HASH-SMALL"));
            }

            @Override
            public void close() {
            }
        };

        // Act
        Result<RepoFile, DedupError> addRes = repoManager.addPath(small, hasher, new MimetypeProvider()).get();

        // Assert: entry added and fields present
        assertThat(addRes.hasFailed()).isFalse();
        RepoFile rf = addRes.value();
        assertThat(rf).isNotNull();
        assertThat(rf.relativePath()).isEqualTo("tiny.txt");
        assertThat(rf.size()).isEqualTo(6L);
        assertThat(rf.hash()).isNotBlank();

        // Since size=6 and indices=2 -> 6%2=0, entry should be appended to 0.idx
        Path index0 = configRoot.resolve("r2/0.idx");
        assertThat(index0).exists();
        String all = Files.readString(index0);
        // RepoFile JSON uses compact field names: p=relativePath, s=size, h=hash
        assertThat(all).contains("\"p\":\"tiny.txt\"");
        assertThat(all).contains("\"s\":6");
        assertThat(all).contains("\"h\":");
    }
}
