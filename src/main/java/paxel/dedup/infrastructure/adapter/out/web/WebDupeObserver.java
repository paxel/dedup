package paxel.dedup.infrastructure.adapter.out.web;

import lombok.RequiredArgsConstructor;
import paxel.dedup.domain.service.DupeObserver;
import paxel.dedup.domain.service.EventBus;
import paxel.dedup.repo.domain.repo.DuplicateRepoProcess;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@RequiredArgsConstructor
public class WebDupeObserver implements DupeObserver {
    private final String repoName;
    private final EventBus eventBus;
    private final BiConsumer<String, List<List<DuplicateRepoProcess.RepoRepoFile>>> groupsStore;

    @Override
    public void onStart(boolean all, List<String> names) {
        eventBus.publish("dupe-start", Map.of("repo", repoName, "all", all, "names", names));
    }

    @Override
    public void onProcessingRepo(String repo, int index, int total) {
        eventBus.publish("dupe-processing-repo", Map.of("repo", repo, "index", index, "total", total));
    }

    @Override
    public void onGroupingSimilar(int index, int total, int bitLength, int threshold) {
        eventBus.publish("dupe-grouping-hamming", Map.of("repo", repoName, "index", index, "total", total, "bitLength", bitLength, "similarity", threshold));
    }

    @Override
    public void onFinished(String repo, int groupCount) {
        eventBus.publish("dupe-finished", Map.of("repo", repoName, "groupCount", groupCount));
    }

    @Override
    public void onGroupsReady(String repo, List<List<DuplicateRepoProcess.RepoRepoFile>> groups) {
        // Store groups server-side instead of sending them all via WebSocket
        groupsStore.accept(repoName, groups);

        int totalFiles = groups.stream().mapToInt(List::size).sum();
        // Send only metadata via WebSocket — the frontend will fetch batches via REST
        eventBus.publish("dupes-finished", Map.of(
                "repo", repoName,
                "totalGroups", groups.size(),
                "totalFiles", totalFiles
        ));
    }

    @Override
    public void onError(String repo, String message) {
        eventBus.publish("error", Map.of("repo", repo, "message", message));
    }
}
