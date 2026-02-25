package paxel.dedup.domain.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.domain.port.out.FileSystem;

import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@RequiredArgsConstructor
public class AudioFingerprinter {
    private final FileSystem fileSystem;
    private static final int CHUNK_SIZE = 100 * 1024; // 100KB

    public String calculateAudioHash(Path path) {
        try (InputStream is = fileSystem.newInputStream(path)) {
            // Skip common ID3v2/tag headers to focus on content
            // ID3v2 header is 10 bytes: "ID3" + 2 bytes version + 1 byte flags + 4 bytes size
            byte[] header = new byte[10];
            int read = is.read(header);
            if (read == 10 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                // Get size: 4 bytes, 7 bits each (synchsafe integer)
                int size = ((header[6] & 0x7F) << 21) |
                        ((header[7] & 0x7F) << 14) |
                        ((header[8] & 0x7F) << 7) |
                        (header[9] & 0x7F);
                // Total size is header (10) + tag size
                // We skip 'size' bytes after the header
                is.skip(size);
            } else {
                // Not ID3v2 or too short, reset or just continue if not possible to reset
                // Since it's a new stream, we just continue from where we are if we didn't match ID3
                // If we read 10 bytes but not ID3, we can't easily 'unread' with a generic InputStream
                // so we just accept those 10 bytes as part of the chunk for simplicity.
            }

            byte[] chunk = new byte[CHUNK_SIZE];
            int totalRead = 0;
            int n;
            while (totalRead < CHUNK_SIZE && (n = is.read(chunk, totalRead, CHUNK_SIZE - totalRead)) != -1) {
                totalRead += n;
            }

            if (totalRead == 0) return null;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(chunk, 0, totalRead);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.warn("Failed to calculate audio hash for {}: {}", path, e.getMessage());
            return null;
        }
    }
}
