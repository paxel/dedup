package paxel.dedup.data;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.util.Date;

@Builder
@Jacksonized
public record RepoFile(String hash, String relativePath, Long size, long lastModified, boolean missing, String meta) {
}
