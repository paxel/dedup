package paxel.dedup.model.errors;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Path;

public record OpenRepoError(Path path, IOException ioException) {
    public static @NonNull OpenRepoError notFound(@NonNull Path path) {
        return new OpenRepoError(path, null);
    }

    public static @NonNull OpenRepoError ioError(@NonNull Path path, @NonNull IOException ioException) {
        return new OpenRepoError(path, ioException);
    }
}
