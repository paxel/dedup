package paxel.dedup.model.errors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public record UpdateRepoError(Path path, List<Exception> ioExceptions, String description) {
    public static UpdateRepoError ioException(Path repoDir, Exception ioException) {
        return new UpdateRepoError(repoDir, List.of(ioException), null);
    }

    public static UpdateRepoError description(String description) {
        return new UpdateRepoError(null, List.of(), description);
    }
}
