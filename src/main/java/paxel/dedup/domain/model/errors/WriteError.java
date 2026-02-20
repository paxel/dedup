package paxel.dedup.domain.model.errors;

import com.fasterxml.jackson.core.JsonProcessingException;
import paxel.dedup.domain.model.RepoFile;

import java.io.IOException;
import java.nio.file.Path;

public record WriteError(RepoFile repoFile, Path indexFile, Exception e) {
    public static WriteError ioException(Path indexFile, IOException e) {
        return new WriteError(null, indexFile, e);
    }

    public static WriteError ioException(RepoFile repoFile, Path indexFile, IOException e) {
        return new WriteError(repoFile, indexFile, e);
    }

    public static WriteError jsonException(RepoFile repoFile, JsonProcessingException e) {
        return new WriteError(repoFile, null, e);
    }
}
