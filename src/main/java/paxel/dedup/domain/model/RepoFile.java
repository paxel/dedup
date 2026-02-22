package paxel.dedup.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepoFile(
        @JsonProperty(value = "h", required = true) String hash,
        @JsonProperty(value = "p") String relativePath,
        @JsonProperty(value = "s", defaultValue = "0") Long size,
        @JsonProperty(value = "l") long lastModified,
        @JsonProperty(value = "d", defaultValue = "false") boolean missing,
        @JsonProperty(value = "m") String mimeType,
        @JsonProperty(value = "f") String fingerprint) {

    @JsonCreator
    public RepoFile(
            @JsonProperty(value = "h", required = true) String hash,
            @JsonProperty(value = "p") String relativePath,
            @JsonProperty(value = "s", defaultValue = "0") Long size,
            @JsonProperty(value = "l") long lastModified,
            @JsonProperty(value = "d", defaultValue = "false") boolean missing,
            @JsonProperty(value = "m") String mimeType,
            @JsonProperty(value = "f") String fingerprint) {
        this.hash = hash;
        this.relativePath = relativePath;
        this.size = size != null ? size : 0L;
        this.lastModified = lastModified;
        this.missing = missing;
        this.mimeType = mimeType;
        this.fingerprint = fingerprint;
    }

    public RepoFile withHash(String hash) {
        return toBuilder().hash(hash).build();
    }

    public RepoFile withRelativePath(String relativePath) {
        return toBuilder().relativePath(relativePath).build();
    }

    public RepoFile withSize(Long size) {
        return toBuilder().size(size).build();
    }

    public RepoFile withLastModified(long lastModified) {
        return toBuilder().lastModified(lastModified).build();
    }

    public RepoFile withMissing(boolean missing) {
        return toBuilder().missing(missing).build();
    }

    public RepoFile withMimeType(String mimeType) {
        return toBuilder().mimeType(mimeType).build();
    }

    public RepoFile withFingerprint(String fingerprint) {
        return toBuilder().fingerprint(fingerprint).build();
    }
}
