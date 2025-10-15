package paxel.dedup.model.utils;

import java.nio.file.Path;

public interface FileObserver {
    default void fail(Path root, Throwable e) {
    }

    default void file(Path f) {
    }

    default void addDir(Path f) {
    }

    default void finishedDir(Path f) {
    }


    default void close() {
    }

    default void scanFinished() {
    }
}
