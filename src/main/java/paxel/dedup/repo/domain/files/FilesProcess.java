package paxel.dedup.repo.domain.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.model.Repo;
import paxel.dedup.model.RepoFile;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.LoadError;
import paxel.dedup.model.errors.OpenRepoError;
import paxel.dedup.model.utils.FilterFactory;
import paxel.dedup.model.utils.TunneledIoException;
import paxel.dedup.parameter.CliParameter;
import paxel.dedup.repo.domain.repo.RepoManager;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@RequiredArgsConstructor
public class FilesProcess {
    private final CliParameter cliParameter;
    private final String source;
    private final DedupConfig dedupConfig;
    private final String filter;
    private final ObjectMapper objectMapper;
    private Predicate<RepoFile> repoFilter;
    private final FilterFactory filterFactory = new FilterFactory();


    public int ls() {
        Result<RepoManager, Integer> result = openRepo(source);
        if (result.hasFailed()) {
            return result.error();
        }
        repoFilter = filterFactory.createFilter(filter);

        AtomicReference<String> last = new AtomicReference<>();
        result.value().stream()
                .filter(repoFile -> !repoFile.missing())
                .filter(repoFilter)
                .sorted(Comparator.comparing(RepoFile::relativePath))
                .forEach(r -> {
                    String path = Paths.get(r.relativePath()).getParent().toString();
                    if (last.get() == null || !last.get().equals(path)) {
                        System.out.println(path);
                        last.set(path);
                    }
                    System.out.println("  %-50s %-12s %s".formatted(Paths.get(r.relativePath()).getFileName().toString(), r.size(), new Date(r.lastModified())));
                });
        return 0;
    }

    public int rm() {
        Result<RepoManager, Integer> result = openRepo(source);
        if (result.hasFailed()) {
            return result.error();
        }
        repoFilter = filterFactory.createFilter(filter);

        try {
            result.value().stream()
                    .filter(repoFile -> !repoFile.missing())
                    .filter(repoFilter)
                    .sorted(Comparator.comparing(RepoFile::relativePath))
                    .forEach(r -> {
                        try {
                            Files.delete(Paths.get(result.value().getRepo().absolutePath()).resolve(r.relativePath()));
                        } catch (IOException e) {
                            throw new TunneledIoException("Could not delete " + r.relativePath(), e);
                        }

                    });
        } catch (TunneledIoException e) {
            System.err.println(e.getMessage() + " " + e.getCause().getClass().getSimpleName());
            return -213;
        }
        return 0;
    }


    public int types() {
        Result<RepoManager, Integer> result = openRepo(source);
        if (result.hasFailed()) {
            return result.error();
        }

        try {
            result.value().stream()
                    .filter(repoFile -> !repoFile.missing())
                    .map(RepoFile::mimeType)
                    .distinct()
                    .sorted()
                    .forEach(r -> {
                        System.out.println(r);
                    });
        } catch (TunneledIoException e) {
            System.err.println(e.getMessage() + " " + e.getCause().getClass().getSimpleName());
            return -213;
        }
        return 0;
    }

    private Result<RepoManager, Integer> openRepo(String name) {
        Result<Repo, OpenRepoError> repo = dedupConfig.getRepo(name);
        if (repo.hasFailed()) {
            System.err.println("Could not open " + name + " " + repo.error());
            return Result.err(-121);
        }
        RepoManager repoManager = new RepoManager(repo.value(), dedupConfig, objectMapper);
        Result<Statistics, LoadError> loadResult = repoManager.load();
        if (loadResult.hasFailed()) {
            System.err.println("Could not load " + name + " " + loadResult.error());
            return Result.err(-123);
        }
        return Result.ok(repoManager);
    }

}
