package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.logging.ConsoleLogger;
import paxel.lib.Result;

import java.util.*;

@RequiredArgsConstructor
public class DuplicateRepoProcess {
    private static final ConsoleLogger log = ConsoleLogger.getInstance();
    private final CliParameter cliParameter;
    private final List<String> names;
    private final boolean all;
    private final DedupConfig dedupConfig;
    private final Integer threshold;
    private final FileSystem fileSystem;

    public DuplicateRepoProcess(CliParameter cliParameter, List<String> names, boolean all, DedupConfig dedupConfig, Integer threshold) {
        this(cliParameter, names, all, dedupConfig, threshold, new NioFileSystemAdapter());
    }

    public Result<Integer, DedupError> dupes() {
        if (all) {
            Result<List<Repo>, DedupError> repos = dedupConfig.getRepos();
            if (repos.hasFailed()) {
                return Result.err(repos.error());
            }
            return Result.ok(dupe(repos.value()));
        }
        List<Repo> repos = new ArrayList<>();
        for (String name : names) {
            Result<Repo, DedupError> repoResult = dedupConfig.getRepo(name);
            if (repoResult.isSuccess()) {
                repos.add(repoResult.value());
            }
        }
        return Result.ok(dupe(repos));
    }

    private int dupe(List<Repo> repos) {
        if (threshold != null && threshold > 0) {
            return findSimilar(repos);
        }

        Map<UniqueHash, List<RepoRepoFile>> all = new HashMap<>();

        for (Repo repo : repos) {
            RepoManager r = RepoManager.forRepo(repo, dedupConfig, fileSystem);
            Result<Statistics, DedupError> load = r.load();
            if (load.hasFailed()) {
                return -81;
            }
            r.stream()
                    .filter(repoFile1 -> !repoFile1.missing())
                    .forEach(repoFile ->
                            all.computeIfAbsent(new UniqueHash(repoFile.hash(), repoFile.size()),
                                    k -> new ArrayList<>()).add(new RepoRepoFile(repo, repoFile)));
        }

        printDuplicates(all);


        return 0;
    }

    private int findSimilar(List<Repo> repos) {
        List<RepoRepoFile> images = new ArrayList<>();
        for (Repo repo : repos) {
            RepoManager r = RepoManager.forRepo(repo, dedupConfig, fileSystem);
            if (r.load().hasFailed()) continue;
            r.stream()
                    .filter(rf -> !rf.missing() && rf.fingerprint() != null)
                    .forEach(rf -> images.add(new RepoRepoFile(repo, rf)));
        }

        if (images.isEmpty()) {
            log.info("No images with fingerprints found.");
            return 0;
        }

        // Naive O(n^2) comparison for similarity. 
        // For large repos, we'd need a more efficient way like BK-tree or spatial hashing.
        Set<Integer> handled = new HashSet<>();
        for (int i = 0; i < images.size(); i++) {
            if (handled.contains(i)) continue;
            List<RepoRepoFile> group = new ArrayList<>();
            group.add(images.get(i));

            String f1 = images.get(i).file.fingerprint();
            java.math.BigInteger b1 = new java.math.BigInteger(f1, 16);

            for (int j = i + 1; j < images.size(); j++) {
                if (handled.contains(j)) continue;
                String f2 = images.get(j).file.fingerprint();
                java.math.BigInteger b2 = new java.math.BigInteger(f2, 16);

                int distance = hammingDistance(b1, b2);
                // Standard dHash is 64 bits (8x8 comparisons)
                // Normalized distance (0-100)
                double similarity = (1.0 - (double) distance / 64.0) * 100.0;

                if (similarity >= threshold) {
                    group.add(images.get(j));
                    handled.add(j);
                }
            }
            if (group.size() > 1) {
                printSimilarGroup(group);
            }
        }
        return 0;
    }

    private int hammingDistance(java.math.BigInteger b1, java.math.BigInteger b2) {
        return b1.xor(b2).bitCount();
    }

    private void printSimilarGroup(List<RepoRepoFile> group) {
        log.info("Similar Group (Threshold: {}%):", threshold);
        for (RepoRepoFile rrf : group) {
            log.info(String.format("  %s: %s/%s (fingerprint: %s)",
                    rrf.repo.name(), rrf.repo.absolutePath(), rrf.file.relativePath(), rrf.file.fingerprint()));
        }
    }

    private static void printDuplicates(Map<UniqueHash, List<RepoRepoFile>> all) {
        List<List<RepoRepoFile>> list = all.entrySet().stream().filter(e -> e.getValue().size() > 1).map(Map.Entry::getValue).toList();
        for (List<RepoRepoFile> repoRepoFiles : list) {
            log.info(String.format("%s%n %d bytes", repoRepoFiles.getFirst().file.hash(), repoRepoFiles.getFirst().file.size()));
            repoRepoFiles.stream().sorted(Comparator.comparing(f -> f.file().lastModified()))
                    .forEach(repoRepoFile -> log.info(String.format("  %s%n   %s/%s", repoRepoFile.repo.name(), repoRepoFile.repo.absolutePath(), repoRepoFile.file.relativePath())));
        }
    }

    record UniqueHash(String hash, long size) {
    }

    record RepoRepoFile(Repo repo, RepoFile file) {

    }
}
