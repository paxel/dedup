package paxel.dedup.domain.model;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import paxel.dedup.domain.model.errors.LoadError;
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

    @Override
    public CompletableFuture<Result<String, LoadError>> hash(Path path) {
        return CompletableFuture.supplyAsync(() -> hashMe(path), executorService);
        //  return CompletableFuture.completedFuture(hashMe(path));
    }

    private Result<String, LoadError> hashMe(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            try (InputStream fis = Files.newInputStream(path)) {
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) > 0) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();

            return Result.ok(hexStringer.format(hashBytes));
        } catch (Exception e) {
            return Result.err(new LoadError(path, e, e.toString()));
        }
    }

    @SneakyThrows
    @Override
    public void close() {
        executorService.shutdown();
        while (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
        }
    }
}
