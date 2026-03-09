package paxel.dedup.domain.model;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.lib.Result;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class Sha1Hasher implements FileHasher {

    private final BinaryFormatter hexStringer;
    private final ExecutorService executorService;
    private volatile boolean forceShutdown = false;

    @Override
    public CompletableFuture<Result<String, DedupError>> hash(Path path) {
        return CompletableFuture.supplyAsync(() -> hashMe(path), executorService);
        //  return CompletableFuture.completedFuture(hashMe(path));
    }

    private Result<String, DedupError> hashMe(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            try (InputStream fis = Files.newInputStream(path)) {
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) > 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        return Result.err(DedupError.of(ErrorType.LOAD, path + ": Hashing interrupted", new InterruptedException()));
                    }
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();

            return Result.ok(hexStringer.format(hashBytes));
        } catch (Exception e) {
            return Result.err(DedupError.of(ErrorType.LOAD, path + ": " + e, e));
        }
    }

    public void shutdownNow() {
        forceShutdown = true;
        executorService.shutdownNow();
    }

    @SneakyThrows
    @Override
    public void close() {
        if (forceShutdown) {
            executorService.shutdownNow();
        } else {
            executorService.shutdown();
        }
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    }
}
