package paxel.dedup.repo.index;

import java.io.IOException;
import java.nio.file.Path;

public record LoadError(Path path, IOException ioException) {
    public static LoadError ioException(Path path, IOException e) {
        return new LoadError(path, e);
    }
}
