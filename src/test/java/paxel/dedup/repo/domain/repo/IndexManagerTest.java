package paxel.dedup.repo.domain.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.adapter.out.serialization.JacksonMapperLineCodec;
import paxel.dedup.infrastructure.adapter.out.serialization.JsonFrameIterator;
import paxel.dedup.infrastructure.adapter.out.serialization.JsonFrameWriter;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexManagerTest {

    @TempDir
    Path tempDir;

    private Path indexFile;
    private ObjectMapper objectMapper;
    private IndexManager indexManager;

    @BeforeEach
    void setUp() {
        indexFile = tempDir.resolve("test.idx");
        objectMapper = new ObjectMapper();
        // Default to real FS for most tests, or we can use the mock
        indexManager = new IndexManager(indexFile, new JacksonMapperLineCodec<>(objectMapper, RepoFile.class), new NioFileSystemAdapter(), JsonFrameIterator::new, JsonFrameWriter::new);
    }

    @Test
    void testLoadEmptyIndex() {
        // Arrange
        try {
            Files.createFile(indexFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Act
        Result<Statistics, DedupError> result = indexManager.load();

        // Assert
        assertThat(result.hasFailed()).isFalse();
        assertThat(indexManager.stream().count()).isEqualTo(0);
    }

    @Test
    void testLoadAndStream() throws IOException {
        // Arrange
        RepoFile file1 = RepoFile.builder().hash("h1").relativePath("p1").size(10L).build();
        RepoFile file2 = RepoFile.builder().hash("h2").relativePath("p2").size(20L).build();

        Files.writeString(indexFile, objectMapper.writeValueAsString(file1) + "\n" + objectMapper.writeValueAsString(file2) + "\n");

        // Act
        indexManager.load();

        // Assert
        List<RepoFile> files = indexManager.stream().toList();
        assertThat(files).hasSize(2);
        assertThat(files).extracting(RepoFile::relativePath).containsExactlyInAnyOrder("p1", "p2");
    }

    @Test
    void testUpdatePath() throws IOException {
        // Arrange
        RepoFile file1 = RepoFile.builder().hash("h1").relativePath("p1").size(10L).build();
        RepoFile file1Updated = RepoFile.builder().hash("h1-new").relativePath("p1").size(15L).build();

        Files.writeString(indexFile, objectMapper.writeValueAsString(file1) + "\n" + objectMapper.writeValueAsString(file1Updated) + "\n");

        // Act
        indexManager.load();

        // Assert
        assertThat(indexManager.stream().count()).isEqualTo(1);
        RepoFile result = indexManager.getByPath("p1");
        assertThat(result.hash()).isEqualTo("h1-new");
        assertThat(result.size()).isEqualTo(15L);

        // Check hash lookup
        assertThat(indexManager.getByHash("h1")).isEmpty();
        assertThat(indexManager.getByHash("h1-new")).hasSize(1);
    }

    @Test
    void testDuplicates() throws IOException {
        // Arrange
        RepoFile file1 = RepoFile.builder().hash("common").relativePath("p1").size(10L).build();
        RepoFile file2 = RepoFile.builder().hash("common").relativePath("p2").size(10L).build();

        Files.writeString(indexFile, objectMapper.writeValueAsString(file1) + "\n" + objectMapper.writeValueAsString(file2) + "\n");

        // Act
        Result<Statistics, DedupError> loadResult = indexManager.load();

        // Assert
        assertThat(indexManager.getByHash("common")).hasSize(2);
        // Statistics should show 1 duplicate (2 files with same hash - 1)
        // Note: Statistics.counter name for duplicates is "duplicates"
        loadResult.value().forCounter((key, value) -> {
            if ("duplicates".equals(key)) {
                assertThat(value).isEqualTo(1);
            }
        });
    }
}
