package paxel.dedup.model.utils;

import java.nio.file.Path;

public interface FileObserver {
    default void fail(Path root, Exception e) {
    }

    default void file(Path f) {
    }

    default void dir(Path f) {
    }
}
