package paxel.dedup.domain.model;

import lombok.SneakyThrows;
import paxel.dedup.domain.port.out.FileSystem;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class ResilientFileWalker {

    private final FileObserver fileObserver;
    private final FileSystem fileSystem;
    private final AtomicBoolean cancelled;
    private final LinkedBlockingDeque<Path> directories = new LinkedBlockingDeque<>();
    private final AtomicBoolean finished = new AtomicBoolean();

    public ResilientFileWalker(FileObserver fileObserver, FileSystem fileSystem) {
        this.fileObserver = fileObserver;
        this.fileSystem = fileSystem;
        this.cancelled = new AtomicBoolean(false);
    }

    public ResilientFileWalker(FileObserver fileObserver, FileSystem fileSystem, AtomicBoolean cancelled) {
        this.fileObserver = fileObserver;
        this.fileSystem = fileSystem;
        this.cancelled = cancelled;
    }

    @SneakyThrows(InterruptedException.class)
    public void walk(Path root) {
        CompletableFuture.runAsync(() ->
                        populateDirectories(root))
                .whenComplete((ignore, ig) -> {
                    if (ig != null)
                        fileObserver.fail(null, ig);
                    this.finished();

                });

        for (; ; ) {
            if (cancelled.get()) {
                fileObserver.close();
                return;
            }
            Path pop = directories.poll(1, TimeUnit.SECONDS);
            if (pop != null)
                processDir(pop);
            else if (finished.get()) {
                fileObserver.close();
                return;
            }
        }
    }

    private void finished() {
        fileObserver.scanFinished();
        finished.set(true);
    }


    private void processDir(Path dir) {
        try (Stream<Path> list = fileSystem.list(dir)) {
            list.filter(fileSystem::isRegularFile)
                    .filter(f -> !fileSystem.isSymbolicLink(f))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(fileObserver::file);
        } catch (Exception e) {
            fileObserver.fail(dir, e);
        } finally {
            fileObserver.finishedDir(dir);
        }
    }

    private void populateDirectories(Path parent) {
        if (cancelled.get()) {
            return;
        }
        // count parent when entered. so the numbers are equal to the "when left"
        directories.add(parent);
        fileObserver.addDir(parent);
        try (Stream<Path> list = fileSystem.list(parent)) {
            list.filter(fileSystem::isDirectory)
                    .filter(f -> !fileSystem.isSymbolicLink(f))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(this::populateDirectories);
        } catch (Exception e) {
            fileObserver.fail(parent, e);
        }
    }
}
