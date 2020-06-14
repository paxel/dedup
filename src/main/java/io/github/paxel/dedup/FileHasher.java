package io.github.paxel.dedup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.xml.bind.DatatypeConverter;

/**
 *
 */
public class FileHasher {

    static final int MAX = 3;

    private final long length;

    public FileHasher(long length) {
        this.length = length;
    }
    private final byte[] buffer = new byte[8096];

    public String calc(int layer, FileCollector.FileMessage f) throws IOException {
        if (layer == 0) {
            // calc simple hash start

            try (InputStream in = Files.newInputStream(f.getPath())) {
                int read = in.read(buffer);
                return calcBlockHash(read, "SHA1");
            }
        }
        if (layer == 1) {
            // calc simple hash end
            long skip = length - buffer.length;
            if (skip < 0) {
                // not enough data to check end
                return "";
            }
            try (InputStream in = Files.newInputStream(f.getPath())) {
                long skipped = in.skip(skip);
                if (skipped != skip) {
                    throw new IllegalStateException("Didn't skip enough: " + skip + " != " + skipped);
                }
                int read = in.read(buffer);
                return calcBlockHash(read, "SHA1");
            }

        }
        if (layer == 2) {
            // calc full hash
            try (InputStream in = Files.newInputStream(f.getPath())) {
                return calcHash(in, "MD5");
            }
        }
        throw new IllegalArgumentException("Should not be called for that level: " + layer);
    }

    private String calcBlockHash(int read, final String hashAlgo) throws RuntimeException {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(hashAlgo);
            messageDigest.update(buffer, 0, read);
            byte[] digiest = messageDigest.digest();
            return DatatypeConverter.printHexBinary(digiest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String calcHash(InputStream in, String algo) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algo);

            for (;;) {
                int read = in.read(buffer);
                if (read < 0) {
                    break;
                }
                messageDigest.update(buffer, 0, read);
            }
            byte[] digiest = messageDigest.digest();
            return DatatypeConverter.printHexBinary(digiest);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
