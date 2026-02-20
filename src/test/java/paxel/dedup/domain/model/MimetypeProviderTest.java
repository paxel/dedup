package paxel.dedup.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.domain.model.errors.IoError;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MimetypeProviderTest {

    @TempDir
    Path tempDir;

    private final MimetypeProvider provider = new MimetypeProvider();

    @Test
    void testDetectTextFile() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");
        Result<String, IoError> result = provider.get(file);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo("text/plain");
    }

    @Test
    void testDetectMissingFile() {
        Path file = tempDir.resolve("missing.txt");
        Result<String, IoError> result = provider.get(file);
        assertThat(result.hasFailed()).isTrue();
    }
}
