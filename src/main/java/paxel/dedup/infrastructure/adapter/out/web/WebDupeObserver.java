package paxel.dedup.infrastructure.adapter.out.web;

import lombok.RequiredArgsConstructor;
import paxel.dedup.domain.service.DupeObserver;
import paxel.dedup.domain.service.EventBus;
import paxel.dedup.repo.domain.repo.DuplicateRepoProcess;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class WebDupeObserver implements DupeObserver {
    private final String repoName;
    private final EventBus eventBus;

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
    public void onFinished(int groupCount) {
        eventBus.publish("dupe-finished", Map.of("repo", repoName, "groupCount", groupCount));
    }

    @Override
    public void onGroupsReady(String repo, List<List<DuplicateRepoProcess.RepoRepoFile>> groups) {
        eventBus.publish("dupes-finished", Map.of("repo", repo, "groups", groups));
    }

    @Override
    public void onError(String repo, String message) {
        eventBus.publish("error", Map.of("repo", repo, "message", message));
    }
}
