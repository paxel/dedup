package paxel.dedup.domain.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.lib.Result;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class Sha1HasherTest {

    @TempDir
    Path tempDir;

    private Sha1Hasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new Sha1Hasher(new HexFormatter(), Executors.newFixedThreadPool(2));
    }

    @AfterEach
    void tearDown() {
        hasher.close();
    }

    @Test
    void testHash() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        Result<String, DedupError> result = hasher.hash(file).get();

        assertThat(result.isSuccess()).isTrue();
        // SHA-1 of "hello world" is 2aae6c35c94fcfb415dbe95f408b9ce91ee846ed
        assertThat(result.value()).isEqualTo("2aae6c35c94fcfb415dbe95f408b9ce91ee846ed");
    }

    @Test
    void testHashEmptyFile() throws Exception {
        Path file = tempDir.resolve("empty.txt");
        Files.createFile(file);

        Result<String, DedupError> result = hasher.hash(file).get();

        assertThat(result.isSuccess()).isTrue();
        // SHA-1 of empty string is da39a3ee5e6b4b0d3255bfef95601890afd80709
        assertThat(result.value()).isEqualTo("da39a3ee5e6b4b0d3255bfef95601890afd80709");
    }
}
