package paxel.dedup.domain.model;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import paxel.dedup.domain.port.out.FileSystem;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MetadataExtractor {
    private final AutoDetectParser parser = new AutoDetectParser();
    private final FileSystem fileSystem;

    public MetadataExtractor(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public Map<String, String> extract(Path path) {
        Map<String, String> attributes = new HashMap<>();
        try (InputStream stream = fileSystem.newInputStream(path)) {
            Metadata metadata = new Metadata();
            parser.parse(stream, new BodyContentHandler(-1), metadata, new ParseContext());

            // Extract useful attributes
            addIfPresent(attributes, "title", metadata.get(TikaCoreProperties.TITLE));
            addIfPresent(attributes, "artist", metadata.get(XMPDM.ARTIST));
            addIfPresent(attributes, "duration", metadata.get(XMPDM.DURATION));
            addIfPresent(attributes, "width", metadata.get("tiff:ImageWidth"));
            addIfPresent(attributes, "height", metadata.get("tiff:ImageLength"));
            addIfPresent(attributes, "pages", metadata.get("xmpTPg:NPages")); // PDF pages

            // Add other common ones if needed
            addIfPresent(attributes, "album", metadata.get(XMPDM.ALBUM));
            addIfPresent(attributes, "genre", metadata.get(XMPDM.GENRE));

        } catch (Exception e) {
            // Log or handle error if necessary, for now return empty or partial
        }
        return attributes;
    }

    private void addIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
