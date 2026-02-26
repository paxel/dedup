package paxel.dedup.domain.model;

import lombok.Builder;

import java.util.Map;

@Builder
public record RepoStats(
        long fileCount,
        long totalSize,
        Map<String, Long> mimeTypeDistribution
) {
}
