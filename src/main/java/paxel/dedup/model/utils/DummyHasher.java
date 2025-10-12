package paxel.dedup.model.utils;

import paxel.dedup.model.errors.LoadError;
import paxel.lib.Result;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class DummyHasher implements FileHasher {
    @Override
    public CompletableFuture<Result<String, LoadError>> hash(Path path) {
        throw new UnsupportedOperationException("hash should not happen");
    }

    @Override
    public void close() {

    }
}
