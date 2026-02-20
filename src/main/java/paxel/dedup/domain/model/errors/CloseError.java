package paxel.dedup.domain.model.errors;

import java.io.IOException;
import java.nio.file.Path;

public record CloseError(Path path, IOException ioException) {
    public static CloseError ioException(Path path, IOException ioException) {
        return new CloseError(path, ioException);
    }
}
