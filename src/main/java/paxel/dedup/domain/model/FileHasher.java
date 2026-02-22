package paxel.dedup.domain.model;

import lombok.SneakyThrows;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.lib.Result;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface FileHasher extends AutoCloseable {
    CompletableFuture<Result<String, DedupError>> hash(Path path);

    @Override
    @SneakyThrows
    void close();
}
