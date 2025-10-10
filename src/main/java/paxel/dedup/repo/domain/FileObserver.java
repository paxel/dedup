package paxel.dedup.repo.domain;

import java.io.IOException;
import java.nio.file.Path;

public interface FileObserver {
    default void fail(Path root, Exception e) {
    }

    default void file(Path f) {
    }

    default void dir(Path f) {
    }
}
