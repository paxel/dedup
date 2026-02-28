package paxel.dedup.repo.domain.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.adapter.out.serialization.*;
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
    void testCompressedIndex() throws IOException {
        // Arrange
        indexFile = tempDir.resolve("test.idx.gz");
        Files.createFile(indexFile);
        FrameIteratorFactoryFactory ffff = new FrameIteratorFactoryFactory();
        IndexManager compressedManager = new IndexManager(indexFile, new JacksonMapperLineCodec<>(objectMapper, RepoFile.class), new NioFileSystemAdapter(), ffff.forReader(Repo.Codec.JSON, true), ffff.forWriter(Repo.Codec.JSON, true));

        RepoFile file1 = RepoFile.builder().hash("h1").relativePath("p1").size(10L).build();
        RepoFile file2 = RepoFile.builder().hash("h2").relativePath("p2").size(20L).build();

        // Act
        compressedManager.add(file1);
        compressedManager.close();

        compressedManager.add(file2);
        compressedManager.close();

        // Assert
        assertThat(indexFile).exists();
        byte[] bytes = Files.readAllBytes(indexFile);
        assertThat(bytes[0]).isEqualTo((byte) 0x1f);
        assertThat(bytes[1]).isEqualTo((byte) 0x8b);

        // Load it back
        IndexManager reader = new IndexManager(indexFile, new JacksonMapperLineCodec<>(objectMapper, RepoFile.class), new NioFileSystemAdapter(), ffff.forReader(Repo.Codec.JSON, true), ffff.forWriter(Repo.Codec.JSON, true));
        reader.load();
        List<RepoFile> files = reader.stream().toList();
        assertThat(files).hasSize(2);
        assertThat(files).extracting(RepoFile::relativePath).containsExactlyInAnyOrder("p1", "p2");
    }

    @Test
    void testMsgPackIndex() throws IOException {
        // Arrange
        indexFile = tempDir.resolve("test.idx.mp");
        Files.createFile(indexFile);
        ObjectMapper mpMapper = new ObjectMapper(new org.msgpack.jackson.dataformat.MessagePackFactory());
        IndexManager mpManager = new IndexManager(indexFile, new JacksonMapperLineCodec<>(mpMapper, RepoFile.class), new NioFileSystemAdapter(), MsgPackFrameIterator::new, MsgPackFrameWriter::new);

        RepoFile file1 = RepoFile.builder().hash("h1").relativePath("p1").size(10L).build();

        // Act
        mpManager.add(file1);
        mpManager.close();

        // Assert
        IndexManager reader = new IndexManager(indexFile, new JacksonMapperLineCodec<>(mpMapper, RepoFile.class), new NioFileSystemAdapter(), MsgPackFrameIterator::new, MsgPackFrameWriter::new);
        reader.load();
        assertThat(reader.stream().toList()).hasSize(1);
        assertThat(reader.getByPath("p1").hash()).isEqualTo("h1");
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
    void testLoadEmptyCompressedIndex() throws IOException {
        // Arrange
        indexFile = tempDir.resolve("empty_compressed.idx.gz");
        Files.createFile(indexFile);
        FrameIteratorFactoryFactory ffff = new FrameIteratorFactoryFactory();
        IndexManager compressedManager = new IndexManager(indexFile, new JacksonMapperLineCodec<>(objectMapper, RepoFile.class), new NioFileSystemAdapter(), ffff.forReader(Repo.Codec.JSON, true), ffff.forWriter(Repo.Codec.JSON, true));

        // Act
        Result<Statistics, DedupError> result = compressedManager.load();

        // Assert
        assertThat(result.hasFailed()).isFalse();
        assertThat(compressedManager.stream().count()).isEqualTo(0);
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

    @Test
    void shouldLoadValidEntriesAndFixCorruptIndex() throws IOException {
        // Arrange: 1 valid, 1 corrupt (partial JSON), 1 valid
        RepoFile file1 = RepoFile.builder().hash("h1").relativePath("p1").size(10L).build();
        RepoFile file3 = RepoFile.builder().hash("h3").relativePath("p3").size(30L).build();

        String valid1 = objectMapper.writeValueAsString(file1);
        String corrupt = "{\"h\":\"h2\", \"p\":\"p2\", \"s\":"; // Partial JSON
        String valid3 = objectMapper.writeValueAsString(file3);

        Files.writeString(indexFile, valid1 + "\n" + corrupt + "\n" + valid3 + "\n");

        // Act
        indexManager.load();

        // Assert: We should have loaded p1 and p3
        List<RepoFile> files = indexManager.stream().toList();
        assertThat(files).extracting(RepoFile::relativePath).containsExactlyInAnyOrder("p1", "p3");

        // Assert: Index file should have been fixed (only contains 2 valid lines now)
        List<String> lines = Files.readAllLines(indexFile);
        assertThat(lines).hasSize(2);
        assertThat(lines).anyMatch(l -> l.contains("p1"));
        assertThat(lines).anyMatch(l -> l.contains("p3"));
        assertThat(lines).noneMatch(l -> l.contains("h2"));

        // Assert: Backup file should exist
        Path backup = indexFile.resolveSibling(indexFile.getFileName().toString() + ".bak");
        assertThat(backup).exists();
        assertThat(Files.readString(backup)).contains("p2");
    }

    @Test
    void shouldSkipAndRepairWhenHashIsMissing() throws IOException {
        // Arrange: 1 valid, 1 missing hash, 1 valid
        RepoFile file1 = RepoFile.builder().hash("h1").relativePath("p1").size(10L).build();
        RepoFile file3 = RepoFile.builder().hash("h3").relativePath("p3").size(30L).build();

        String valid1 = objectMapper.writeValueAsString(file1);
        String invalid = "{\"p\":\"p2\", \"s\":20}"; // Missing "h"
        String valid3 = objectMapper.writeValueAsString(file3);

        Files.writeString(indexFile, valid1 + "\n" + invalid + "\n" + valid3 + "\n");

        // Act
        indexManager.load();

        // Assert: We should have loaded p1 and p3
        List<RepoFile> files = indexManager.stream().toList();
        assertThat(files).extracting(RepoFile::relativePath).containsExactlyInAnyOrder("p1", "p3");

        // Assert: Index file should have been fixed
        List<String> lines = Files.readAllLines(indexFile);
        assertThat(lines).hasSize(2);
    }
}
