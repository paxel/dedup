package paxel.dedup.repo.domain.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateReposProcessTest {

    @TempDir
    Path tempDir;

    private DedupConfig dedupConfig;
    private ObjectMapper objectMapper;
    private CliParameter cliParameter;
    private FileSystem fileSystem;

    @BeforeEach
    void setUp() {
        dedupConfig = mock(DedupConfig.class);
        objectMapper = new ObjectMapper();
        cliParameter = new CliParameter();
        fileSystem = new NioFileSystemAdapter();
        when(dedupConfig.getRepoDir()).thenReturn(tempDir.resolve("config"));
    }

    @Test
    void testUpdateSingleRepo() throws IOException {
        // Arrange
        Path repoPath = tempDir.resolve("data");
        Files.createDirectories(repoPath);
        Path file1 = repoPath.resolve("file1.txt");
        Files.writeString(file1, "content1");

        Path configRepoDir = tempDir.resolve("config/testRepo");
        Files.createDirectories(configRepoDir);

        Repo repo = new Repo("testRepo", repoPath.toString(), 1);
        when(dedupConfig.getRepo("testRepo")).thenReturn(Result.ok(repo));

        UpdateReposProcess process = new UpdateReposProcess(
                cliParameter,
                List.of("testRepo"),
                false,
                1,
                dedupConfig,
                objectMapper,
                false
        );

        // Act
        int exitCode = process.update();

        // Debug: list config dir
        System.out.println("[DEBUG_LOG] Config dir contents: " + Files.list(configRepoDir).toList());

        // Assert
        assertThat(exitCode).isEqualTo(0);
        
        // Verify index was created
        Path indexPath = configRepoDir.resolve("0.idx");
        assertThat(indexPath).exists();
        
        List<String> lines = Files.readAllLines(indexPath);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("file1.txt");
    }
}
