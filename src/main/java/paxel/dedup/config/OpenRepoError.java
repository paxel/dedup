package paxel.dedup.config;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Path;

public record OpenRepoError(Path resolve, IOException e) {
    public static @NonNull OpenRepoError notFound(@NonNull Path repoPath) {
        return new OpenRepoError(repoPath, null);
    }

    public static @NonNull OpenRepoError ioError(@NonNull Path resolve, @NonNull IOException ioException) {
        return new OpenRepoError(resolve, ioException);
    }
}
