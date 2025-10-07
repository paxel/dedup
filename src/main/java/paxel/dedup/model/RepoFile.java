package paxel.dedup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepoFile(
        @With @JsonProperty(value = "h", required = true, defaultValue = "") String hash,
        @With @JsonProperty(value = "p") String relativePath,
        @With @JsonProperty(value = "s", defaultValue = "0") Long size,
        @With @JsonProperty(value = "l") long lastModified,
        @With @JsonProperty(value = "d", defaultValue = "false") boolean missing,
        @With @JsonProperty(value = "m", defaultValue = "") String meta) {
}
