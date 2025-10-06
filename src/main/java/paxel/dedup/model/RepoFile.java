package paxel.dedup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record RepoFile(
        @JsonProperty(value = "h", defaultValue = "") String hash,
        @JsonProperty(value = "p") String relativePath,
        @JsonProperty(value = "s", defaultValue = "0") Long size,
        @JsonProperty(value = "l") long lastModified,
        @JsonProperty(value = "d", defaultValue = "false") boolean missing,
        @JsonProperty(value = "m", defaultValue = "") String meta) {
}
