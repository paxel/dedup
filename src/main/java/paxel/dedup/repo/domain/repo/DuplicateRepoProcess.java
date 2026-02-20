package paxel.dedup.repo.domain.repo;
import lombok.RequiredArgsConstructor;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.LoadError;
import paxel.dedup.domain.model.errors.OpenRepoError;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.domain.port.out.LineCodec;
import paxel.lib.Result;

import java.util.*;

@RequiredArgsConstructor
public class DuplicateRepoProcess {
    private final CliParameter cliParameter;
    private final List<String> names;
    private final boolean all;
    private final DedupConfig dedupConfig;
    private final LineCodec<RepoFile> repoFileCodec;


    public int dupes() {
        if (all) {
            Result<List<Repo>, OpenRepoError> repos = dedupConfig.getRepos();
            if (repos.hasFailed()) {
                return -80;
            }
            return dupe(repos.value());
        }
        List<Repo> repos = new ArrayList<>();
        for (String name : names) {
            Result<Repo, OpenRepoError> repoResult = dedupConfig.getRepo(name);
            if (repoResult.isSuccess()) {
                repos.add(repoResult.value());
            }
        }
        dupe(repos);
        return 0;
    }

    private int dupe(List<Repo> repos) {

        Map<UniqueHash, List<RepoRepoFile>> all = new HashMap<>();

        for (Repo repo : repos) {
            RepoManager r = new RepoManager(repo, dedupConfig, repoFileCodec, new NioFileSystemAdapter());
            Result<Statistics, LoadError> load = r.load();
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

    private static void printDuplicates(Map<UniqueHash, List<RepoRepoFile>> all) {
        List<List<RepoRepoFile>> list = all.entrySet().stream().filter(e -> e.getValue().size() > 1).map(Map.Entry::getValue).toList();
        for (List<RepoRepoFile> repoRepoFiles : list) {
            System.out.printf("%s%n %d bytes%n", repoRepoFiles.getFirst().file.hash(), repoRepoFiles.getFirst().file.size());
            repoRepoFiles.stream().sorted(Comparator.comparing(f -> f.file().lastModified()))
                    .forEach(repoRepoFile -> System.out.printf("  %s%n   %s/%s%n",repoRepoFile.repo.name(), repoRepoFile.repo.absolutePath(), repoRepoFile.file.relativePath()));
        }
    }

    record UniqueHash(String hash, long size) {
    }

    record RepoRepoFile(Repo repo, RepoFile file) {

    }
}
