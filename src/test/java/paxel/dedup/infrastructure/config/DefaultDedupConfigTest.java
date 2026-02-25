package paxel.dedup.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.lib.Result;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDedupConfigTest {

    @TempDir
    Path tempDir;

    private DefaultDedupConfig newConfig() {
        return new DefaultDedupConfig(tempDir, new NioFileSystemAdapter());
    }

    @Test
    void createRepo_writesYaml_andIdxFiles_and_getRepo_reads_values() {
        // Arrange
        DefaultDedupConfig cfg = newConfig();

        // Act
        Result<Repo, DedupError> created = cfg.createRepo("r1", tempDir.resolve("data"), 2);

        // Assert
        assertThat(created.isSuccess()).isTrue();
        Path repoDir = tempDir.resolve("r1");
        assertThat(Files.exists(repoDir)).isTrue();
        assertThat(Files.exists(repoDir.resolve(DefaultDedupConfig.DEDUP_REPO_YML))).isTrue();
        assertThat(Files.exists(repoDir.resolve("0.idx"))).isTrue();
        assertThat(Files.exists(repoDir.resolve("1.idx"))).isTrue();

        Result<Repo, DedupError> loaded = cfg.getRepo("r1");
        assertThat(loaded.isSuccess()).isTrue();
        assertThat(loaded.value().name()).isEqualTo("r1");
        assertThat(loaded.value().indices()).isEqualTo(2);
        assertThat(loaded.value().absolutePath()).isEqualTo(tempDir.resolve("data").toAbsolutePath().toString());
    }

    @Test
    void changePath_updates_yaml_path_only() {
        // Arrange
        DefaultDedupConfig cfg = newConfig();
        Path pathA = tempDir.resolve("dataA");
        Path pathB = tempDir.resolve("dataB");
        assertThat(cfg.createRepo("r2", pathA, 1).isSuccess()).isTrue();

        // Act
        Result<Repo, DedupError> changed = cfg.changePath("r2", pathB);

        // Assert
        assertThat(changed.isSuccess()).isTrue();
        assertThat(changed.value().absolutePath()).isEqualTo(pathB.toAbsolutePath().toString());
        assertThat(changed.value().indices()).isEqualTo(1);
    }

    @Test
    void deleteRepo_removes_directory_and_returns_true_nonexistent_false() {
        // Arrange
        DefaultDedupConfig cfg = newConfig();
        assertThat(cfg.createRepo("r3", tempDir.resolve("x"), 1).isSuccess()).isTrue();
        Path repoDir = tempDir.resolve("r3");
        assertThat(Files.exists(repoDir)).isTrue();

        // Act
        Result<Boolean, DedupError> del = cfg.deleteRepo("r3");

        // Assert
        assertThat(del.isSuccess()).isTrue();
        assertThat(del.value()).isTrue();
        assertThat(Files.exists(repoDir)).isFalse();

        // Non-existent returns false
        Result<Boolean, DedupError> del2 = cfg.deleteRepo("nope");
        assertThat(del2.isSuccess()).isTrue();
        assertThat(del2.value()).isFalse();
    }

    @Test
    void renameRepo_moves_dir_and_updates_yaml() {
        // Arrange
        DefaultDedupConfig cfg = newConfig();
        Path origPath = tempDir.resolve("origData");
        assertThat(cfg.createRepo("old", origPath, 2).isSuccess()).isTrue();

        // Act
        Result<Boolean, DedupError> renamed = cfg.renameRepo("old", "new");

        // Assert
        assertThat(renamed.isSuccess()).isTrue();
        assertThat(renamed.value()).isTrue();
        assertThat(Files.exists(tempDir.resolve("old"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("new"))).isTrue();

        Result<Repo, DedupError> after = cfg.getRepo("new");
        assertThat(after.isSuccess()).isTrue();
        assertThat(after.value().name()).isEqualTo("new");
        assertThat(after.value().absolutePath()).isEqualTo(origPath.toAbsolutePath().toString());
        assertThat(after.value().indices()).isEqualTo(2);

        // No-op rename returns false
        Result<Boolean, DedupError> noop = cfg.renameRepo("new", "new");
        assertThat(noop.isSuccess()).isTrue();
        assertThat(noop.value()).isFalse();
    }
}
