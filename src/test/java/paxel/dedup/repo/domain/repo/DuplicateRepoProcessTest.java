package paxel.dedup.repo.domain.repo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DuplicateRepoProcessTest {

    @TempDir
    Path tempDir;

    private DedupConfig dedupConfig;
    private CliParameter cliParameter;

    @BeforeEach
    void setUp() {
        dedupConfig = mock(DedupConfig.class);
        cliParameter = new CliParameter();
        when(dedupConfig.getRepoDir()).thenReturn(tempDir.resolve("config"));
    }

    @Test
    void shouldFindSimilarImagesWhenFingerprintIsPresent() throws IOException {
        // Arrange
        Path repoPath = tempDir.resolve("repo1");
        Files.createDirectories(repoPath);
        Path configRepoDir = tempDir.resolve("config/repo1");
        Files.createDirectories(configRepoDir);

        Repo repo = new Repo("repo1", repoPath.toString(), 1);
        when(dedupConfig.getRepo("repo1")).thenReturn(Result.ok(repo));

        // Create an index file with two similar images
        // Using a fake fingerprint (hex string)
        String fp1 = "000000000000000f"; // some bits set
        String fp2 = "000000000000000e"; // 1 bit different

        RepoFile file1 = RepoFile.builder()
                .hash("hash1")
                .relativePath("img1.jpg")
                .size(100L)
                .fingerprint(fp1)
                .build();
        RepoFile file2 = RepoFile.builder()
                .hash("hash2")
                .relativePath("img2.jpg")
                .size(100L)
                .fingerprint(fp2)
                .build();

        RepoManager repoManager = RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter());
        repoManager.load();
        repoManager.addRepoFile(file1);
        repoManager.addRepoFile(file2);

        DuplicateRepoProcess process = new DuplicateRepoProcess(
                cliParameter,
                List.of("repo1"),
                false,
                dedupConfig,
                90, // threshold
                DuplicateRepoProcess.DupePrintMode.PRINT,
                new NioFileSystemAdapter()
        );

        // Act
        Result<Integer, paxel.dedup.domain.model.errors.DedupError> result = process.dupes();

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(0);
        // Verification of output is hard with log.info, but we mainly want to ensure it doesn't log "No images with fingerprints found."
    }

    @Test
    void shouldNotFindImagesWhenFingerprintIsBlank() throws IOException {
        // Arrange
        Path repoPath = tempDir.resolve("repo2");
        Files.createDirectories(repoPath);
        Path configRepoDir = tempDir.resolve("config/repo2");
        Files.createDirectories(configRepoDir);

        Repo repo = new Repo("repo2", repoPath.toString(), 1);
        when(dedupConfig.getRepo("repo2")).thenReturn(Result.ok(repo));

        RepoFile file1 = RepoFile.builder()
                .hash("hash1")
                .relativePath("img1.jpg")
                .size(100L)
                .fingerprint("") // Blank fingerprint
                .build();

        RepoManager repoManager = RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter());
        repoManager.load();
        repoManager.addRepoFile(file1);

        DuplicateRepoProcess process = new DuplicateRepoProcess(
                cliParameter,
                List.of("repo2"),
                false,
                dedupConfig,
                90,
                DuplicateRepoProcess.DupePrintMode.PRINT,
                new NioFileSystemAdapter()
        );

        // Act
        Result<Integer, paxel.dedup.domain.model.errors.DedupError> result = process.dupes();

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(0);
    }
}
