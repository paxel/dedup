package paxel.dedup.repo.domain.diff;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

@RequiredArgsConstructor
public class DiffProcess {
    private final CliParameter cliParameter;
    private final String source;
    private final String target;
    private final DedupConfig dedupConfig;
    private final String filter;
    private final ObjectMapper objectMapper;
    private Predicate<RepoFile> repoFilter;
    private final FilterFactory filterFactory = new FilterFactory();

    public int print() {
        Result<Repos, Integer> init = init();
        if (init.hasFailed()) {
            return init.error();
        }
        RepoManager sourceRepo = init.value().source();
        RepoManager targetRepo = init.value().target();

        sourceRepo.stream()
                .filter(repoFile -> !repoFile.missing())
                .filter(repoFilter)
                .forEach(r -> {
                    List<RepoFile> byHash = targetRepo.getByHashAndSize(r.hash(), r.size());
                    if (byHash.isEmpty()) {
                        System.out.println("New: " + r.relativePath());
                    } else {
                        Optional<RepoFile> exsting = byHash.stream().filter(repoFile -> !repoFile.missing()).findAny();
                        if (exsting.isPresent()) {
                            // File exists
                            if (cliParameter.isVerbose()) {
                                System.out.println("Equal: " + r.relativePath() + " = " + exsting.get().relativePath());
                            }
                        } else {
                            System.out.println("Deleted in target: " + r.relativePath());
                        }
                    }
                });
        return 0;
    }

    public int copy(String target, boolean move) {
        Result<Repos, Integer> init = init();
        if (init.hasFailed()) {
            return init.error();
        }
        RepoManager sourceRepo = init.value().source();
        RepoManager targetRepo = init.value().target();

        try {
            sourceRepo.stream()
                    .filter(repoFile -> !repoFile.missing())
                    .filter(repoFilter)
                    .forEach(r -> {
                        List<RepoFile> byHash = targetRepo.getByHashAndSize(r.hash(), r.size());
                        if (byHash.isEmpty()) {
                            Path targetFile = Paths.get(target).resolve(r.relativePath());
                            if (!Files.exists(targetFile.getParent())) {
                                try {
                                    Files.createDirectories(targetFile.getParent());
                                } catch (IOException e) {
                                    throw new TunneledIoException("Could not create " + targetFile.getParent(), e);
                                }
                            }
                            Path sourceFile = Paths.get(sourceRepo.getRepo().absolutePath()).resolve(r.relativePath());
                            try {
                                if (move) {
                                    Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                                    if (cliParameter.isVerbose()) {
                                        System.out.println("Moved " + r.relativePath());
                                    }
                                } else {
                                    Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                                    if (cliParameter.isVerbose()) {
                                        System.out.println("Copied " + r.relativePath());
                                    }
                                }
                            } catch (IOException e) {
                                throw new TunneledIoException("Could not copy/move " + sourceFile + " to " + targetFile, e);
                            }
                        }
                    });
        } catch (TunneledIoException e) {
            System.err.println(e.getMessage() + " " + e.getCause().getClass().getSimpleName());
            return -200;
        }
        return 0;
    }

    public int delete() {
        Result<Repos, Integer> init = init();
        if (init.hasFailed()) {
            return init.error();
        }
        RepoManager sourceRepo = init.value().source();
        RepoManager targetRepo = init.value().target();

        try {
            sourceRepo.stream()
                    .filter(repoFile -> !repoFile.missing())
                    .filter(repoFilter)
                    .forEach(r -> {
                        List<RepoFile> byHash = targetRepo.getByHashAndSize(r.hash(), r.size());
                        if (!byHash.isEmpty()) {

                            Path sourceFile = Paths.get(sourceRepo.getRepo().absolutePath()).resolve(r.relativePath());
                            try {
                                Files.delete(sourceFile);
                                if (cliParameter.isVerbose()) {
                                    System.out.println("Deleted " + r.relativePath());
                                }
                            } catch (IOException e) {
                                throw new TunneledIoException("Could not delete " + sourceFile, e);
                            }
                        }
                    });
        } catch (TunneledIoException e) {
            System.err.println(e.getMessage() + " " + e.getCause().getClass().getSimpleName());
            return -200;
        }
        return 0;
    }

    private Result<Repos, Integer> init() {
        this.repoFilter = filterFactory.createFilter(filter);

        Result<RepoManager, Integer> sourcceRepo = openRepo(source, -70);
        if (sourcceRepo.hasFailed()) {
            return sourcceRepo.mapError(Function.identity());
        }
        return openRepo(target, -80)
                .map(target -> new Repos(sourcceRepo.value(), target), Function.identity());
    }


    private Result<RepoManager, Integer> openRepo(String name, int errOffset) {
        Result<Repo, OpenRepoError> repo = dedupConfig.getRepo(name);
        if (repo.hasFailed()) {
            System.err.println("Could not open " + name + " " + repo.error());
            return Result.err(errOffset - 1);
        }
        RepoManager repoManager = new RepoManager(repo.value(), dedupConfig, objectMapper);
        Result<Statistics, LoadError> loadResult = repoManager.load();
        if (loadResult.hasFailed()) {
            System.err.println("Could not load " + name + " " + loadResult.error());
            return Result.err(errOffset - 2);
        }
        return Result.ok(repoManager);
    }


    record Repos(RepoManager source, RepoManager target) {
    }
}
