package paxel.dedup.model.errors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public record RenameRepoError(Path resolve, List<Exception> ioExceptions) {

    public static RenameRepoError ioError(Path repoPath, IOException e) {
        return new RenameRepoError(repoPath, List.of(e));
    }

    public static RenameRepoError ioErrors(Path repoPath, List<Exception> exceptions) {
        return new RenameRepoError(repoPath, List.copyOf(exceptions));
    }
}