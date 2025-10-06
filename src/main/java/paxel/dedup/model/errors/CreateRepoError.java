package paxel.dedup.model.errors;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Path;

public record CreateRepoError(Path path, IOException ioException) {
    public static @NonNull CreateRepoError exists(@NonNull Path repoPath) {
        return new CreateRepoError(repoPath, null);
    }

    public static @NonNull CreateRepoError ioError(@NonNull Path repoPath, @NonNull IOException ioException) {
        return new CreateRepoError(repoPath, ioException);
    }
}
