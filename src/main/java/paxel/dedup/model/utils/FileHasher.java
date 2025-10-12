package paxel.dedup.model.utils;

import lombok.SneakyThrows;
import paxel.dedup.model.errors.LoadError;
import paxel.lib.Result;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface FileHasher {
    CompletableFuture<Result<String, LoadError>> hash(Path path);

    @SneakyThrows
    void close();
}
