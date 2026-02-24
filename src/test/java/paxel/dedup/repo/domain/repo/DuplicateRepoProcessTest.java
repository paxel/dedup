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
                null,
                null,
                null,
                false,
                false,
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
                null,
                null,
                null,
                false,
                false,
                new NioFileSystemAdapter()
        );

        // Act
        Result<Integer, paxel.dedup.domain.model.errors.DedupError> result = process.dupes();

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(0);
    }

    @Test
    void shouldGenerateReports() throws IOException {
        // Arrange
        Path repoPath = tempDir.resolve("repo3");
        Files.createDirectories(repoPath);
        Repo repo = new Repo("repo3", repoPath.toString(), 1);
        when(dedupConfig.getRepo("repo3")).thenReturn(Result.ok(repo));

        RepoFile file1 = RepoFile.builder().hash("h").relativePath("f1.jpg").size(10L).mimeType("image/jpeg").build();
        RepoFile file2 = RepoFile.builder().hash("h").relativePath("f2.jpg").size(10L).mimeType("image/jpeg").build();

        RepoManager repoManager = RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter());
        repoManager.load();
        repoManager.addRepoFile(file1);
        repoManager.addRepoFile(file2);
        repoManager.close();

        Path mdReport = tempDir.resolve("report.md");
        Path htmlReport = tempDir.resolve("report.html");

        DuplicateRepoProcess process = new DuplicateRepoProcess(
                cliParameter,
                List.of("repo3"),
                false,
                dedupConfig,
                null,
                DuplicateRepoProcess.DupePrintMode.QUIET,
                mdReport.toString(),
                htmlReport.toString(),
                null,
                false,
                false,
                new NioFileSystemAdapter()
        );

        // Act
        process.dupes();

        // Assert
        assertThat(mdReport).exists();
        String mdContent = Files.readString(mdReport);
        assertThat(mdContent).contains("# Duplicate/Similar Files Report");
        assertThat(mdContent).contains("f1.jpg");
        assertThat(mdContent).contains("![thumbnail]");
        assertThat(mdContent).contains("Size:");
        assertThat(mdContent).contains("Modified:");
        assertThat(mdContent).contains("10 B"); // formatSize for 10L

        assertThat(htmlReport).exists();
        String htmlContent = Files.readString(htmlReport);
        assertThat(htmlContent).contains("<h1>Duplicate/Similar Files Report</h1>");
        assertThat(htmlContent).contains("f2.jpg");
        assertThat(htmlContent).contains("<img src=");
        assertThat(htmlContent).contains("<strong>Size:</strong> 10 B");
        assertThat(htmlContent).contains("<strong>Modified:</strong>");
    }

    @Test
    void shouldGenerateReportsSortedBySize() throws IOException {
        // Arrange
        Path repoPath = tempDir.resolve("repo4");
        Files.createDirectories(repoPath);
        Repo repo = new Repo("repo4", repoPath.toString(), 1);
        when(dedupConfig.getRepo("repo4")).thenReturn(Result.ok(repo));

        RepoFile small = RepoFile.builder().hash("h1").relativePath("small.jpg").size(11L).mimeType("image/jpeg").fingerprint("000000000000000e").build();
        RepoFile large = RepoFile.builder().hash("h2").relativePath("large.jpg").size(12L).mimeType("image/jpeg").fingerprint("000000000000000f").build();

        RepoManager repoManager = RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter());
        repoManager.load();
        repoManager.addRepoFile(small);
        repoManager.addRepoFile(large);
        repoManager.close();

        Path mdReport = tempDir.resolve("report_sort.md");

        DuplicateRepoProcess process = new DuplicateRepoProcess(
                cliParameter,
                List.of("repo4"),
                false,
                dedupConfig,
                90, // Similarity threshold
                DuplicateRepoProcess.DupePrintMode.QUIET,
                mdReport.toString(),
                null,
                null,
                false,
                false,
                new NioFileSystemAdapter()
        );

        // Act
        process.dupes();

        // Assert
        assertThat(mdReport).exists();
        String mdContent = Files.readString(mdReport);
        // large.jpg (100) should come before small.jpg (10)
        assertThat(mdContent).contains("large.jpg");
        assertThat(mdContent).contains("small.jpg");
        int largeIndex = mdContent.indexOf("large.jpg");
        int smallIndex = mdContent.indexOf("small.jpg");
        assertThat(largeIndex).isLessThan(smallIndex);
    }

    @Test
    void shouldOrderEqualSizeByOldestFirst() throws IOException {
        // Arrange
        Path repoPath = tempDir.resolve("repo5");
        Files.createDirectories(repoPath);
        Repo repo = new Repo("repo5", repoPath.toString(), 1);
        when(dedupConfig.getRepo("repo5")).thenReturn(Result.ok(repo));

        // Two files with same hash and same size but different lastModified
        RepoFile older = RepoFile.builder()
                .hash("same")
                .relativePath("older.jpg")
                .size(100L)
                .lastModified(1_000L)
                .mimeType("image/jpeg")
                .build();
        RepoFile newer = RepoFile.builder()
                .hash("same")
                .relativePath("newer.jpg")
                .size(100L)
                .lastModified(2_000L)
                .mimeType("image/jpeg")
                .build();

        RepoManager repoManager = RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter());
        repoManager.load();
        repoManager.addRepoFile(older);
        repoManager.addRepoFile(newer);
        repoManager.close();

        Path mdReport = tempDir.resolve("report_equal_size.md");

        DuplicateRepoProcess process = new DuplicateRepoProcess(
                cliParameter,
                List.of("repo5"),
                false,
                dedupConfig,
                null, // exact duplicates mode
                DuplicateRepoProcess.DupePrintMode.QUIET,
                mdReport.toString(),
                null,
                null,
                false,
                false,
                new NioFileSystemAdapter()
        );

        // Act
        process.dupes();

        // Assert: older should come before newer within the same group
        String md = Files.readString(mdReport);
        int olderIdx = md.indexOf("older.jpg");
        int newerIdx = md.indexOf("newer.jpg");
        assertThat(olderIdx).isGreaterThan(-1);
        assertThat(newerIdx).isGreaterThan(-1);
        assertThat(olderIdx).isLessThan(newerIdx);
    }

    @Test
    void shouldDeleteDuplicates() throws IOException {
        // Arrange
        Path repoPath = tempDir.resolve("repo_delete");
        Files.createDirectories(repoPath);
        Path file1 = repoPath.resolve("keep.jpg");
        Path file2 = repoPath.resolve("delete.jpg");
        Files.writeString(file1, "content");
        Files.writeString(file2, "content");

        Repo repo = new Repo("repo_delete", repoPath.toString(), 1);
        when(dedupConfig.getRepo("repo_delete")).thenReturn(Result.ok(repo));

        RepoFile rf1 = RepoFile.builder().hash("h").relativePath("keep.jpg").size(7L).lastModified(1000L).build();
        RepoFile rf2 = RepoFile.builder().hash("h").relativePath("delete.jpg").size(7L).lastModified(2000L).build();

        RepoManager repoManager = RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter());
        repoManager.load();
        repoManager.addRepoFile(rf1);
        repoManager.addRepoFile(rf2);
        repoManager.close();

        DuplicateRepoProcess process = new DuplicateRepoProcess(
                cliParameter,
                List.of("repo_delete"),
                false,
                dedupConfig,
                null,
                DuplicateRepoProcess.DupePrintMode.QUIET,
                null,
                null,
                null,
                true, // delete
                false,
                new NioFileSystemAdapter()
        );

        // Act
        process.dupes();

        // Assert
        assertThat(file1).exists();
        assertThat(file2).doesNotExist();

        // Verify index update
        repoManager.load();
        RepoFile updatedRf2 = repoManager.getByPath("delete.jpg");
        assertThat(updatedRf2.missing()).isTrue();
    }

    @Test
    void shouldMoveDuplicates() throws IOException {
        // Arrange
        Path repoPath = tempDir.resolve("repo_move");
        Files.createDirectories(repoPath);
        Path file1 = repoPath.resolve("keep.jpg");
        Path file2 = repoPath.resolve("move.jpg");
        Files.writeString(file1, "content");
        Files.writeString(file2, "content");

        Path moveDir = tempDir.resolve("moved_files");

        Repo repo = new Repo("repo_move", repoPath.toString(), 1);
        when(dedupConfig.getRepo("repo_move")).thenReturn(Result.ok(repo));

        RepoFile rf1 = RepoFile.builder().hash("h").relativePath("keep.jpg").size(7L).lastModified(1000L).build();
        RepoFile rf2 = RepoFile.builder().hash("h").relativePath("move.jpg").size(7L).lastModified(2000L).build();

        RepoManager repoManager = RepoManager.forRepo(repo, dedupConfig, new NioFileSystemAdapter());
        repoManager.load();
        repoManager.addRepoFile(rf1);
        repoManager.addRepoFile(rf2);
        repoManager.close();

        DuplicateRepoProcess process = new DuplicateRepoProcess(
                cliParameter,
                List.of("repo_move"),
                false,
                dedupConfig,
                null,
                DuplicateRepoProcess.DupePrintMode.QUIET,
                null,
                null,
                moveDir.toString(),
                false,
                false,
                new NioFileSystemAdapter()
        );

        // Act
        process.dupes();

        // Assert
        assertThat(file1).exists();
        assertThat(file2).doesNotExist();
        assertThat(moveDir.resolve("move.jpg")).exists();

        // Verify index update
        repoManager.load();
        RepoFile updatedRf2 = repoManager.getByPath("move.jpg");
        assertThat(updatedRf2.missing()).isTrue();
    }
}
