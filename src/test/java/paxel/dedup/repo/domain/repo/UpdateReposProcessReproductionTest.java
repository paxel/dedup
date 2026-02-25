package paxel.dedup.repo.domain.repo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateReposProcessReproductionTest {

    private DedupConfig dedupConfig;
    private CliParameter cliParameter;

    @BeforeEach
    void setUp() {
        dedupConfig = mock(DedupConfig.class);
        when(dedupConfig.getRepoDir()).thenReturn(java.nio.file.Paths.get("/tmp/config"));
        cliParameter = new CliParameter();
    }

    @Test
    void shouldReturnErrorWhenRequestedRepoIsNotFound() {
        // Arrange
        String name = "missingRepo";
        when(dedupConfig.getRepo(name)).thenReturn(Result.err(DedupError.of(ErrorType.OPEN_REPO, name + " not found")));

        UpdateReposProcess process = new UpdateReposProcess(
                cliParameter,
                List.of(name),
                false,
                1,
                dedupConfig,
                false,
                false,
                mock(FileSystem.class)
        );

        // Act
        Result<Integer, DedupError> result = process.update();

        // Assert
        assertThat(result.hasFailed()).as("Should fail when requested repo is missing").isTrue();
        assertThat(result.error().describe()).contains(name).contains("not found");
    }

    @Test
    void shouldReturnErrorWhenAllIsSpecifiedButNoReposFound() {
        // Arrange
        when(dedupConfig.getRepos()).thenReturn(Result.ok(List.of()));

        UpdateReposProcess process = new UpdateReposProcess(
                cliParameter,
                List.of(),
                true,
                1,
                dedupConfig,
                false,
                false,
                mock(FileSystem.class)
        );

        // Act
        Result<Integer, DedupError> result = process.update();

        // Assert
        assertThat(result.hasFailed()).as("Should fail when --all is specified but no repos configured").isTrue();
    }

    @Test
    void shouldReturnErrorWhenRepositoryDirectoryDoesNotExistOnDisk() throws java.io.IOException {
        // Arrange
        String name = "existingRepo";
        Path repoPath = java.nio.file.Files.createTempDirectory("non-existent-repo-test");
        java.nio.file.Files.delete(repoPath); // Ensure it doesn't exist

        Repo repo = new Repo(name, repoPath.toString(), 1);
        when(dedupConfig.getRepo(name)).thenReturn(Result.ok(repo));

        FileSystem fs = mock(FileSystem.class);
        when(fs.exists(repoPath)).thenReturn(false);

        UpdateReposProcess process = new UpdateReposProcess(
                cliParameter,
                List.of(name),
                false,
                1,
                dedupConfig,
                false,
                false,
                fs
        );

        // Act
        Result<Integer, DedupError> result = process.update();

        // Assert
        assertThat(result.hasFailed()).as("Should fail when repository directory is missing on disk").isTrue();
        assertThat(result.error().describe()).contains(repoPath.toString()).contains("Repository directory does not exist");
    }
}
