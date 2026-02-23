package paxel.dedup.repo.domain.files;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.*;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.repo.domain.repo.RepoManager;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@RequiredArgsConstructor
@Slf4j
public class FilesProcess {
    private final CliParameter cliParameter;
    private final String source;
    private final DedupConfig dedupConfig;
    private final String filter;
    private final FileSystem fileSystem;
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
                        log.info("{}", path);
                        last.set(path);
                    }
                    log.info(String.format("  %-50s %-12s %s", Paths.get(r.relativePath()).getFileName().toString(), r.size(), new Date(r.lastModified())));
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
                            fileSystem.delete(Paths.get(result.value().getRepo().absolutePath()).resolve(r.relativePath()));
                        } catch (IOException e) {
                            throw new TunneledIoException("Could not delete " + r.relativePath(), e);
                        }

                    });
        } catch (TunneledIoException e) {
            log.error("{} {}", e.getMessage(), e.getCause().getClass().getSimpleName());
            return -213;
        }
        return 0;
    }

    public int copy(String target, boolean move, String appendix) {
        Result<RepoManager, Integer> result = openRepo(source);
        if (result.hasFailed()) {
            return result.error();
        }
        repoFilter = filterFactory.createFilter(filter);
        try {
            result.value().stream()
                    .filter(repoFile -> !repoFile.missing())
                    .filter(repoFilter)
                    .forEach(r -> {
                        Path targetFile = replaceSuffix(Paths.get(target).resolve(r.relativePath()), appendix);
                        if (!fileSystem.exists(targetFile.getParent())) {
                            try {
                                fileSystem.createDirectories(targetFile.getParent());
                            } catch (IOException e) {
                                throw new TunneledIoException("Could not create " + targetFile.getParent(), e);
                            }
                        }
                        Path sourceFile = Paths.get(result.value().getRepo().absolutePath()).resolve(r.relativePath());
                        try {
                            if (move) {
                                fileSystem.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                                if (cliParameter.isVerbose()) {
                                    log.info("Moved {}", r.relativePath());
                                }
                            } else {
                                fileSystem.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                                if (cliParameter.isVerbose()) {
                                    log.info("Copied {}", r.relativePath());
                                }
                            }
                        } catch (IOException e) {
                            throw new TunneledIoException("Could not copy/move " + sourceFile + " to " + targetFile, e);
                        }
                    });
        } catch (TunneledIoException e) {
            log.error("{} {}", e.getMessage(), e.getCause().getClass().getSimpleName());
            return -200;
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
                    .forEach(type -> log.info("{}", type));
        } catch (TunneledIoException e) {
            log.error("{} {}", e.getMessage(), e.getCause().getClass().getSimpleName());
            return -213;
        }
        return 0;
    }

    private Result<RepoManager, Integer> openRepo(String name) {
        Result<Repo, DedupError> repo = dedupConfig.getRepo(name);
        if (repo.hasFailed()) {
            log.error("Could not open {} {}", name, repo.error());
            return Result.err(-121);
        }
        RepoManager repoManager = RepoManager.forRepo(repo.value(), dedupConfig, fileSystem);
        Result<Statistics, DedupError> loadResult = repoManager.load();
        if (loadResult.hasFailed()) {
            log.error("Could not load {} {}", name, loadResult.error());
            return Result.err(-123);
        }
        return Result.ok(repoManager);
    }

    private Path replaceSuffix(Path path, String newSuffix) {
        if (newSuffix == null) {
            return path;
        }
        if (!newSuffix.startsWith("."))
            newSuffix = "." + newSuffix;
        String file = path.getFileName().toString();
        int index = file.lastIndexOf(".");

        if (index > 0) {
            return path.getParent().resolve(file.substring(0, index) + newSuffix);
        } else {
            return path.getParent().resolve(file + newSuffix);
        }
    }
}
