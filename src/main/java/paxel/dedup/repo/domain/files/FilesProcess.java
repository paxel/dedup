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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
                    Path path1 = Paths.get(r.relativePath());
                    String path = path1.getParent().toString();
                    if (last.get() == null || !last.get().equals(path)) {
                        log.info("{}", path);
                        last.set(path);
                    }
                    log.info(String.format("  %-50s %-12s %s", path1.getFileName().toString(), r.size(), new Date(r.lastModified())));
                });
        return 0;
    }

    public int rm() {
        return rm(null);
    }

    public int rm(Consumer<RemoveProgress> progressCallback) {
        Result<RepoManager, Integer> result = openRepo(source);
        if (result.hasFailed()) {
            return result.error();
        }
        repoFilter = filterFactory.createFilter(filter);

        try {
            List<RepoFile> files = result.value().stream()
                    .filter(repoFile -> !repoFile.missing())
                    .filter(repoFilter)
                    .sorted(Comparator.comparing(RepoFile::relativePath))
                    .toList();

            int total = files.size();
            if (progressCallback != null) {
                progressCallback.accept(new RemoveProgress(total, 0, "Starting..."));
            }

            int done = 0;
            for (RepoFile r : files) {
                if (Thread.currentThread().isInterrupted()) {
                    return -1;
                }
                fileSystem.delete(Paths.get(result.value().getRepo().absolutePath()).resolve(r.relativePath()));
                done++;
                if (progressCallback != null) {
                    progressCallback.accept(new RemoveProgress(total, done, r.relativePath()));
                }
            }
        } catch (TunneledIoException e) {
            log.error("{} {}", e.getMessage(), e.getCause().getClass().getSimpleName());
            return -213;
        } catch (IOException e) {
            log.error("Could not delete file: {}", e.getMessage());
            return -213;
        }
        return 0;
    }

    public record RemoveProgress(int total, int completed, String currentFile) {
    }

    public record CopyProgress(int total, int completed, String currentFile) {
    }

    public int copy(String target, boolean move, String appendix) {
        return copy(target, move, appendix, null);
    }

    public int copy(String target, boolean move, String appendix, Consumer<CopyProgress> progressCallback) {
        Result<RepoManager, Integer> result = openRepo(source);
        if (result.hasFailed()) {
            return result.error();
        }
        repoFilter = filterFactory.createFilter(filter);
        try {
            List<RepoFile> files = result.value().stream()
                    .filter(repoFile -> !repoFile.missing())
                    .filter(repoFilter)
                    .toList();

            int total = files.size();
            if (progressCallback != null) {
                progressCallback.accept(new CopyProgress(total, 0, "Starting..."));
            }

            int done = 0;
            for (RepoFile r : files) {
                if (Thread.currentThread().isInterrupted()) {
                    return -1;
                }
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
                done++;
                if (progressCallback != null) {
                    progressCallback.accept(new CopyProgress(total, done, r.relativePath()));
                }
            }
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
