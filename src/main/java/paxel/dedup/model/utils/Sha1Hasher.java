package paxel.dedup.model.utils;

import lombok.RequiredArgsConstructor;
import paxel.dedup.model.errors.LoadError;
import paxel.lib.Result;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class Sha1Hasher implements FileHasher {

    private final BinaryFormatter hexStringer;

    @Override
    public CompletableFuture<Result<String, LoadError>> hash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            try (InputStream fis = Files.newInputStream(path)) {
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();

            String hexString = hexStringer.format(hashBytes);
            return CompletableFuture.completedFuture(Result.ok(hexString));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Result.err(new LoadError(path, e, e.toString())));
        }
    }
}
