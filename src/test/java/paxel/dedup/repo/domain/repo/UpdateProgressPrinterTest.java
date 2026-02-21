package paxel.dedup.repo.domain.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.LoadError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.adapter.out.serialization.JsonLineCodec;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.terminal.StatisticPrinter;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateProgressPrinterTest {

    @TempDir
    Path tempDir;

    /**
     * Minimal stub that only exposes the repo config directory.
     */
    static class StubDedupConfig implements DedupConfig {
        private final Path repoDir;

        StubDedupConfig(Path repoDir) {
            this.repoDir = repoDir;
        }

        @Override
        public Result<List<Repo>, paxel.dedup.domain.model.errors.OpenRepoError> getRepos() {
            return Result.ok(List.of());
        }

        @Override
        public Result<Repo, paxel.dedup.domain.model.errors.OpenRepoError> getRepo(String name) {
            return Result.err(null);
        }

        @Override
        public Result<Repo, paxel.dedup.domain.model.errors.CreateRepoError> createRepo(String name, Path path, int indices) {
            return Result.err(null);
        }

        @Override
        public Result<Repo, paxel.dedup.domain.model.errors.ModifyRepoError> changePath(String name, Path path) {
            return Result.err(null);
        }

        @Override
        public Result<Boolean, paxel.dedup.domain.model.errors.DeleteRepoError> deleteRepo(String name) {
            return Result.ok(false);
        }

        @Override
        public Path getRepoDir() {
            return repoDir;
        }

        @Override
        public Result<Boolean, paxel.dedup.domain.model.errors.RenameRepoError> renameRepo(String oldName, String newName) {
            return Result.ok(false);
        }
    }

    @Test
    void file_and_directory_updates_are_reflected_in_statistic_printer() throws IOException {
        // Arrange: repo data and config dirs
        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve("a.txt");
        Files.writeString(file, "hello"); // small file (<20 bytes) to hit synchronous path

        Path configRoot = tempDir.resolve("config");
        Files.createDirectories(configRoot);

        Repo repo = new Repo("r1", dataDir.toString(), 1);
        DedupConfig config = new StubDedupConfig(configRoot);
        ObjectMapper mapper = new ObjectMapper();
        FileSystem fs = new NioFileSystemAdapter();
        RepoManager repoManager = new RepoManager(repo, config, new JsonLineCodec<>(mapper, RepoFile.class), fs);

        // Ensure index dir exists; RepoManager.load() will also create 0.idx if missing.
        Files.createDirectories(configRoot.resolve("r1"));
        Result<Statistics, LoadError> load = repoManager.load();
        assertThat(load.hasFailed()).isFalse();

        // Remaining paths map: include the file (will be removed) and a second leftover
        Map<Path, RepoFile> remaining = new HashMap<>();
        remaining.put(file, RepoFile.builder().relativePath("a.txt").size(5L).hash("h").build());
        Path leftover = dataDir.resolve("leftover.bin");
        remaining.put(leftover, RepoFile.builder().relativePath("leftover.bin").size(1L).hash("x").build());

        StatisticPrinter sp = new StatisticPrinter();
        // No-op change listener so calls don't NPE
        sp.registerChangeListener(() -> {
        });

        Statistics stats = new Statistics("s");

        // FileHasher stub that returns a completed future immediately
        paxel.dedup.domain.model.FileHasher hasher = new paxel.dedup.domain.model.FileHasher() {
            @Override
            public CompletableFuture<paxel.lib.Result<String, LoadError>> hash(Path path) {
                return CompletableFuture.completedFuture(Result.ok("HASH-OK"));
            }

            @Override
            public void close() { /* nothing */ }
        };

        UpdateProgressPrinter upp = new UpdateProgressPrinter(remaining, sp, repoManager, stats, hasher);

        // Act: simulate traversal
        upp.addDir(dataDir);
        upp.file(file);         // processes and removes it from remaining
        upp.finishedDir(dataDir);
        upp.scanFinished();
        upp.close();

        // Assert: StatisticPrinter lines reflect changes deterministically
        // Directories line should contain finishedDirs / allDirs (1 / 1) and a scan finished marker at some point
        String dirLine = sp.getLineAt(3); // "Directories: ..."
        assertThat(dirLine).contains("1");

        // Deleted count should be the size of the remaining map after processing the file (leftover only => 1)
        String deletedLine = sp.getLineAt(6); // "    Deleted: ..."
        assertThat(deletedLine).contains("1");

        // Files line contains "finished" after close
        String filesLine = sp.getLineAt(4); // "      Files: ..."
        assertThat(filesLine).contains("finished");

        // Statistics updated with deleted count
        final long[] deletedStat = {-1};
        stats.forCounter((k, v) -> {
            if ("deleted".equals(k)) deletedStat[0] = v;
        });
        assertThat(deletedStat[0]).isEqualTo(1L);

        // Index file should have been appended to with the processed file
        Path indexPath = configRoot.resolve("r1/0.idx");
        assertThat(indexPath).exists();
        List<String> lines = Files.readAllLines(indexPath);
        assertThat(lines).isNotEmpty();
        // RepoFile JSON uses compact field names: p=relativePath
        assertThat(String.join("\n", lines)).contains("\"p\":\"a.txt\"");
    }
}
