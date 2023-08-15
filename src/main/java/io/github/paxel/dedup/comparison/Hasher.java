package io.github.paxel.dedup.comparison;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import paxel.lib.Result;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 */
@AllArgsConstructor
public class Hasher {

    public static final int BUFFER_SIZE = 8046;
    private final HexFormat hexFormat = new HexFormat();

    public static final String MD5 = "md5";
    public static final String SHA1 = "sha1";
    public static final String SHA256 = "sha256";

    public Result<String, HashError> calc(@NonNull Path path, long offset, long size, String algorithm) {
        if (size < 0)
            return Result.err(new HashError("Size must be > 0, was " + size));

        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);

            if (offset > 0) {
                long skipped = 0;
                while (skipped < offset) {
                    long skip = in.skip(offset - skipped);
                    if (skip <= 0)
                        return Result.err(new HashError("Reached end of " + path + ": skipped " + skipped + " of " + offset + " offset bytes."));

                    skipped += skip;
                }
            }

            long hashed = 0;
            byte[] buffer = new byte[(int) Math.min(size, BUFFER_SIZE)];
            for (; ; ) {
                int read = in.read(buffer);
                if (read <= 0)
                    return Result.err(new HashError("Reached end of " + path + ": hashed " + hashed + " of " + size + " size bytes."));

                // use the bytes that have been read, but not more than the remaining size;
                int hashable = (int) Math.min(size - hashed, read);
                messageDigest.update(buffer, 0, hashable);

                hashed += read;
                if (hashed >= size) {
                    break;
                }
            }
            return Result.ok(hexFormat.asString(messageDigest.digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            return Result.err(new HashError(e, "Exception while hashing " + path));
        }
    }


    @Getter
    @AllArgsConstructor
    public static class HashError {
        private Exception e;
        private String description;

        public HashError(String description) {
            this.description = description;
        }
    }

}
