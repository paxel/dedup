package paxel.dedup.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
public record RepoFile(String hash, String relativePath, Long size, long lastModified, boolean missing, String meta) {
}
