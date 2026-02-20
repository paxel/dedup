package paxel.dedup.domain.model.errors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public record DeleteRepoError(Path resolve, List<Exception> ioExceptions) {

    public static DeleteRepoError ioError(Path repoPath, IOException e) {
        return new DeleteRepoError(repoPath, List.of(e));
    }

    public static DeleteRepoError ioErrors(Path repoPath, List<Exception> exceptions) {
        return new DeleteRepoError(repoPath, List.copyOf(exceptions));
    }
}
