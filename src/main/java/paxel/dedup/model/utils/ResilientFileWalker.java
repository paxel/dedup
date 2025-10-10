package paxel.dedup.model.utils;

import lombok.RequiredArgsConstructor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class ResilientFileWalker {

    private final FileObserver fileObserver;

    public void walk(Path root) {
        try (Stream<Path> list = Files.list(root)) {
            list.sorted(Comparator.comparing(Path::toString)).forEach(f -> {
                if (Files.isRegularFile(f)) {
                    fileObserver.file(f);
                } else if (Files.isDirectory(f)) {
                    fileObserver.dir(f);
                    if (!Files.isSymbolicLink(f)) {
                        walk(f);
                    }
                }
            });
        } catch (Exception e) {
            fileObserver.fail(root, e);
        }
    }
}
