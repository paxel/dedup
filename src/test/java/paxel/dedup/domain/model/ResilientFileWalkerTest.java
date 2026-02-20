package paxel.dedup.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResilientFileWalkerTest {

    @TempDir
    Path tempDir;

    @Test
    void testWalk() throws IOException {
        // Arrange
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Path file1 = root.resolve("file1.txt");
        Files.writeString(file1, "content1");
        
        Path subDir = root.resolve("subdir");
        Files.createDirectories(subDir);
        Path file2 = subDir.resolve("file2.txt");
        Files.writeString(file2, "content2");

        List<Path> observedFiles = new ArrayList<>();
        FileObserver observer = mock(FileObserver.class);
        doAnswer(invocation -> {
            observedFiles.add(invocation.getArgument(0));
            return null;
        }).when(observer).file(any(Path.class));

        FileSystem fileSystem = new NioFileSystemAdapter();
        ResilientFileWalker walker = new ResilientFileWalker(observer, fileSystem);

        // Act
        walker.walk(root);

        // Assert
        assertThat(observedFiles).hasSize(2);
        assertThat(observedFiles).containsExactlyInAnyOrder(file1, file2);
        verify(observer, atLeastOnce()).addDir(any());
        verify(observer, atLeastOnce()).finishedDir(any());
        verify(observer).scanFinished();
        verify(observer).close();
    }
}
