package paxel.dedup.domain.port.out;

import paxel.dedup.domain.model.errors.LoadError;
import paxel.lib.Result;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;

public interface FileSystem {
    boolean exists(Path path);
    Stream<Path> list(Path dir) throws IOException;
    Stream<Path> walk(Path start) throws IOException;
    boolean isRegularFile(Path path);
    boolean isDirectory(Path path);
    boolean isSymbolicLink(Path path);
    long size(Path path) throws IOException;
    FileTime getLastModifiedTime(Path path) throws IOException;
    InputStream newInputStream(Path path, StandardOpenOption... options) throws IOException;
    OutputStream newOutputStream(Path path, StandardOpenOption... options) throws IOException;
    BufferedReader newBufferedReader(Path path) throws IOException;
    byte[] readAllBytes(Path path) throws IOException;
    void write(Path path, byte[] bytes, StandardOpenOption... options) throws IOException;
    void delete(Path path) throws IOException;
    boolean deleteIfExists(Path path) throws IOException;
    void createDirectories(Path path) throws IOException;
    void copy(Path source, Path target, CopyOption... options) throws IOException;
    void move(Path source, Path target, CopyOption... options) throws IOException;
    // Add more as needed
}
