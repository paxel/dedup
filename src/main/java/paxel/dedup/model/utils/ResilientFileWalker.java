package paxel.dedup.model.utils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class ResilientFileWalker {

    private final FileObserver fileObserver;
    private final LinkedBlockingDeque<Path> directories = new LinkedBlockingDeque<>();
    private AtomicBoolean finished = new AtomicBoolean();

    @SneakyThrows(InterruptedException.class)
    public void walk(Path root) {
        directories.add(root);
        CompletableFuture.runAsync(() ->
                        populateDirectories(root))
                .whenComplete((ignore, ig) -> {
                    if (ig != null)
                        fileObserver.fail(null, ig);
                    this.finished();

                });

        for (; ; ) {
            Path pop = directories.poll(1, TimeUnit.SECONDS);
            if (pop != null)
                processDir(pop);
            else if (finished.get())
                return;
        }
    }

    private void finished() {
        finished.set(true);
    }


    private void processDir(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            list.filter(Files::isRegularFile)
                    .filter(f -> !Files.isSymbolicLink(f))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(fileObserver::file);
        } catch (Exception e) {
            fileObserver.fail(dir, e);
        } finally {
            fileObserver.finishedDir(dir);
        }
    }

    private void populateDirectories(Path root) {
        try (Stream<Path> list = Files.list(root)) {
            list.filter(Files::isDirectory)
                    .filter(f -> !Files.isSymbolicLink(f))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(f -> {
                        fileObserver.addDir(f);
                        directories.add(f);
                        populateDirectories(f);
                    });
        } catch (Exception e) {
            fileObserver.fail(root, e);
        }
    }
}
