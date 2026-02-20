package paxel.dedup.repo.domain.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.LoadError;
import paxel.dedup.domain.model.errors.OpenRepoError;
import paxel.dedup.domain.model.FilterFactory;
import paxel.dedup.domain.model.TunneledIoException;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.repo.domain.repo.RepoManager;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
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
public class FilesProcess {
    private final CliParameter cliParameter;
    private final String source;
    private final DedupConfig dedupConfig;
    private final String filter;
    private final ObjectMapper objectMapper;
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
                            fileSystem.delete(Paths.get(result.value().getRepo().absolutePath()).resolve(r.relativePath()));
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
                                    System.out.println("Moved " + r.relativePath());
                                }
                            } else {
                                fileSystem.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                                if (cliParameter.isVerbose()) {
                                    System.out.println("Copied " + r.relativePath());
                                }
                            }
                        } catch (IOException e) {
                            throw new TunneledIoException("Could not copy/move " + sourceFile + " to " + targetFile, e);
                        }
                    });
        } catch (TunneledIoException e) {
            System.err.println(e.getMessage() + " " + e.getCause().getClass().getSimpleName());
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
        RepoManager repoManager = new RepoManager(repo.value(), dedupConfig, objectMapper, fileSystem);
        Result<Statistics, LoadError> loadResult = repoManager.load();
        if (loadResult.hasFailed()) {
            System.err.println("Could not load " + name + " " + loadResult.error());
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
