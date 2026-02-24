package paxel.dedup.domain.model;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import paxel.dedup.domain.port.out.FileSystem;

import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public class PdfFingerprinter {
    private final AutoDetectParser parser = new AutoDetectParser();
    private final FileSystem fileSystem;

    public PdfFingerprinter(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public String calculatePdfHash(Path path) {
        try (InputStream stream = fileSystem.newInputStream(path)) {
            BodyContentHandler handler = new BodyContentHandler(5000);
            Metadata metadata = new Metadata();
            parser.parse(stream, handler, metadata, new ParseContext());

            String text = handler.toString();
            if (text == null || text.isBlank()) return null;

            // Normalize: lowercase, no whitespace
            String normalized = text.toLowerCase().replaceAll("\\s+", "");
            if (normalized.isEmpty()) return null;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }
}
