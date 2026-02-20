package paxel.dedup.infrastructure.adapter.out.filesystem;

import paxel.dedup.domain.port.out.FileSystem;

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

public class NioFileSystemAdapter implements FileSystem {
    @Override
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    @Override
    public Stream<Path> list(Path dir) throws IOException {
        return Files.list(dir);
    }

    @Override
    public Stream<Path> walk(Path start) throws IOException {
        return Files.walk(start);
    }

    @Override
    public boolean isRegularFile(Path path) {
        return Files.isRegularFile(path);
    }

    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    @Override
    public boolean isSymbolicLink(Path path) {
        return Files.isSymbolicLink(path);
    }

    @Override
    public long size(Path path) throws IOException {
        return Files.size(path);
    }

    @Override
    public FileTime getLastModifiedTime(Path path) throws IOException {
        return Files.getLastModifiedTime(path);
    }

    @Override
    public InputStream newInputStream(Path path, StandardOpenOption... options) throws IOException {
        return Files.newInputStream(path, options);
    }

    @Override
    public OutputStream newOutputStream(Path path, StandardOpenOption... options) throws IOException {
        return Files.newOutputStream(path, options);
    }

    @Override
    public BufferedReader newBufferedReader(Path path) throws IOException {
        return Files.newBufferedReader(path);
    }

    @Override
    public byte[] readAllBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public void write(Path path, byte[] bytes, StandardOpenOption... options) throws IOException {
        Files.write(path, bytes, options);
    }

    @Override
    public void delete(Path path) throws IOException {
        Files.delete(path);
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        return Files.deleteIfExists(path);
    }

    @Override
    public void createDirectories(Path path) throws IOException {
        Files.createDirectories(path);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        Files.copy(source, target, options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        Files.move(source, target, options);
    }
}
