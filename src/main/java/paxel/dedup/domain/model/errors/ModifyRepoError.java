package paxel.dedup.domain.model.errors;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Path;

public record ModifyRepoError(Path path, IOException ioException) {
    public static @NonNull ModifyRepoError missing(@NonNull Path repoPath) {
        return new ModifyRepoError(repoPath, null);
    }

    public static @NonNull ModifyRepoError ioError(@NonNull Path repoPath, @NonNull IOException ioException) {
        return new ModifyRepoError(repoPath, ioException);
    }
}
