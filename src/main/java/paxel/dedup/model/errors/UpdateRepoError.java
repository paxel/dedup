package paxel.dedup.model.errors;

import java.io.IOException;
import java.nio.file.Path;

public record UpdateRepoError(Path path, IOException ioException) {
    public static UpdateRepoError ioException(Path repoDir, IOException ioException) {
        return new UpdateRepoError(repoDir, ioException);
    }
}
