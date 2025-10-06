package paxel.dedup.model.errors;

import java.io.IOException;
import java.nio.file.Path;

public record UpdateRepoError(Path path, Exception ioException) {
    public static UpdateRepoError ioException(Path repoDir, IOException ioException) {
        return new UpdateRepoError(repoDir, ioException);
    }
}
