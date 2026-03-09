package paxel.dedup.domain.service;

import paxel.dedup.repo.domain.repo.DuplicateRepoProcess;

import java.util.List;

public interface DupeObserver {
    void onStart(boolean all, List<String> names);

    void onProcessingRepo(String repo, int index, int total);

    void onGroupingSimilar(int index, int total, int bitLength, int threshold);

    void onFinished(int groupCount);

    void onGroupsReady(String repo, List<List<DuplicateRepoProcess.RepoRepoFile>> groups);

    void onError(String repo, String message);

    DupeObserver NOOP = new DupeObserver() {
        @Override
        public void onStart(boolean all, List<String> names) {
        }

        @Override
        public void onProcessingRepo(String repo, int index, int total) {
        }

        @Override
        public void onGroupingSimilar(int index, int total, int bitLength, int threshold) {
        }

        @Override
        public void onFinished(int groupCount) {
        }

        @Override
        public void onGroupsReady(String repo, List<List<DuplicateRepoProcess.RepoRepoFile>> groups) {
        }

        @Override
        public void onError(String repo, String message) {
        }
    };
}
