package paxel.dedup.domain.model;

import lombok.SneakyThrows;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.lib.Result;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface FileHasher {
    CompletableFuture<Result<String, DedupError>> hash(Path path);

    @SneakyThrows
    void close();
}
