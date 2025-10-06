package paxel.dedup.model.errors;

import java.io.IOException;
import java.nio.file.Path;

public record LoadError(Path path, Exception ioException, String description) {
    public static LoadError ioException(Path path, IOException e) {
        return new LoadError(path, e, null);
    }
}
