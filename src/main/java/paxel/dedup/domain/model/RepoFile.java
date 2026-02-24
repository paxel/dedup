package paxel.dedup.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.With;

import java.util.Map;

@Builder(toBuilder = true)
@With
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepoFile(
        @JsonProperty(value = "h", required = true) String hash,
        @JsonProperty(value = "p") String relativePath,
        @JsonProperty(value = "s", defaultValue = "0") Long size,
        @JsonProperty(value = "l") long lastModified,
        @JsonProperty(value = "d", defaultValue = "false") boolean missing,
        @JsonProperty(value = "m") String mimeType,
        @JsonProperty(value = "f") String fingerprint,
        @JsonProperty(value = "is") Dimension imageSize,
        @JsonProperty(value = "at") Map<String, String> attributes) {

    @JsonCreator
    public RepoFile(
            @JsonProperty(value = "h", required = true) String hash,
            @JsonProperty(value = "p") String relativePath,
            @JsonProperty(value = "s", defaultValue = "0") Long size,
            @JsonProperty(value = "l") long lastModified,
            @JsonProperty(value = "d", defaultValue = "false") boolean missing,
            @JsonProperty(value = "m") String mimeType,
            @JsonProperty(value = "f") String fingerprint,
            @JsonProperty(value = "is") Dimension imageSize,
            @JsonProperty(value = "at") Map<String, String> attributes) {
        this.hash = hash;
        this.relativePath = relativePath;
        this.size = size != null ? size : 0L;
        this.lastModified = lastModified;
        this.missing = missing;
        this.mimeType = mimeType;
        this.fingerprint = fingerprint;
        this.imageSize = imageSize;
        this.attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }
}
