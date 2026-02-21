package paxel.dedup.repo.domain.diff;

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
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

@RequiredArgsConstructor
@Slf4j
public class DiffProcess {
    private final CliParameter cliParameter;
    private final String source;
    private final String target;
    private final DedupConfig dedupConfig;
    private final String filter;
    private final FileSystem fileSystem;
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
                        log.info("New: {}", r.relativePath());
                    } else {
                        Optional<RepoFile> exsting = byHash.stream().filter(repoFile -> !repoFile.missing()).findAny();
                        if (exsting.isPresent()) {
                            // File exists
                            if (cliParameter.isVerbose()) {
                                log.info("Equal: {} = {}", r.relativePath(), exsting.get().relativePath());
                            }
                        } else {
                            log.info("Deleted in target: {}", r.relativePath());
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
                            if (!fileSystem.exists(targetFile.getParent())) {
                                try {
                                    fileSystem.createDirectories(targetFile.getParent());
                                } catch (IOException e) {
                                    throw new TunneledIoException("Could not create " + targetFile.getParent(), e);
                                }
                            }
                            Path sourceFile = Paths.get(sourceRepo.getRepo().absolutePath()).resolve(r.relativePath());
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
                        }
                    });
        } catch (TunneledIoException e) {
            log.error("{} {}", e.getMessage(), e.getCause().getClass().getSimpleName());
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
                                fileSystem.delete(sourceFile);
                                if (cliParameter.isVerbose()) {
                                    log.info("Deleted {}", r.relativePath());
                                }
                            } catch (IOException e) {
                                throw new TunneledIoException("Could not delete " + sourceFile, e);
                            }
                        }
                    });
        } catch (TunneledIoException e) {
            log.error("{} {}", e.getMessage(), e.getCause().getClass().getSimpleName());
            return -200;
        }
        return 0;
    }

    /**
     * Sync source repo A into target repo B according to the rules:
     * - Equality only by (hash,size), path is irrelevant for existence checks
     * - copyNew: copy files from A that are not present in B (by content) to the same relativePath in B
     * - never overwrite an existing file at the target path; count as skipped
     * - after successful copy, update target index with add(missing=false)
     * - deleteMissing: if A has a missing entry for (hash,size) and B has that content present, delete those B files
     * - after successful delete, update target index with add(missing=true) for the deleted path
     * - best effort: continue on errors and summarize
     *
     * @param copyNew       copy contents that are in A but not present in B (by content)
     * @param deleteMissing delete contents present in B that A marks as missing
     * @return 0 on success, negative error code on init error
     */
    public int sync(boolean copyNew, boolean deleteMissing) {
        Result<Repos, Integer> init = init();
        if (init.hasFailed()) {
            return init.error();
        }
        RepoManager sourceRepo = init.value().source();
        RepoManager targetRepo = init.value().target();

        SyncCounters counters = new SyncCounters();

        sourceRepo.stream()
                .filter(repoFilter)
                .forEach(entry -> syncEntry(entry, sourceRepo, targetRepo, copyNew, deleteMissing, counters));

        log.info(counters.summary());
        return 0;
    }

    private void syncEntry(RepoFile entry, RepoManager sourceRepo, RepoManager targetRepo, boolean copyNew, boolean deleteMissing, SyncCounters counters) {
        try {
            List<RepoFile> matches = targetRepo.getByHashAndSize(entry.hash(), entry.size());
            boolean contentPresentInB = matches.stream().anyMatch(b -> !b.missing());

            if (entry.missing()) {
                handleDeleteIfRequested(deleteMissing, contentPresentInB, matches, targetRepo, counters);
                return;
            }

            handleCopyIfRequested(copyNew, contentPresentInB, entry, sourceRepo, targetRepo, counters);
        } catch (RuntimeException e) {
            counters.incrementErrors();
            log.error("Unexpected error in sync for {}: {}", entry.relativePath(), e.toString());
        }
    }

    private void handleDeleteIfRequested(boolean deleteMissing, boolean contentPresentInB, List<RepoFile> matches, RepoManager targetRepo, SyncCounters counters) {
        if (deleteMissing && contentPresentInB) {
            handleDelete(matches, targetRepo, counters);
        }
    }

    private void handleCopyIfRequested(boolean copyNew, boolean contentPresentInB, RepoFile entry, RepoManager sourceRepo, RepoManager targetRepo, SyncCounters counters) {
        if (!copyNew) {
            return;
        }
        if (contentPresentInB) {
            counters.incrementEqual();
            return;
        }
        handleCopy(entry, sourceRepo, targetRepo, counters);
    }

    private void handleCopy(RepoFile r, RepoManager sourceRepo, RepoManager targetRepo, SyncCounters counters) {
        counters.incrementNew();
        Path targetFile = Paths.get(targetRepo.getRepo().absolutePath()).resolve(r.relativePath());

        if (fileSystem.exists(targetFile)) {
            counters.incrementSkipped();
            return;
        }

        if (ensureParentDirectory(targetFile, counters)) {
            performCopy(r, sourceRepo, targetRepo, targetFile, counters);
        }
    }

    private boolean ensureParentDirectory(Path targetFile, SyncCounters counters) {
        Path parent = targetFile.getParent();
        if (parent == null || fileSystem.exists(parent)) {
            return true;
        }
        try {
            fileSystem.createDirectories(parent);
            return true;
        } catch (IOException e) {
            counters.incrementErrors();
            log.error("Could not create {} {}", parent, e.getClass().getSimpleName());
            return false;
        }
    }

    private void performCopy(RepoFile r, RepoManager sourceRepo, RepoManager targetRepo, Path targetFile, SyncCounters counters) {
        Path sourceFile = Paths.get(sourceRepo.getRepo().absolutePath()).resolve(r.relativePath());
        try {
            fileSystem.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
            counters.incrementCopied();
            targetRepo.addRepoFile(r.withMissing(false));
        } catch (IOException e) {
            counters.incrementErrors();
            log.error("Could not copy {} to {} {}", sourceFile, targetFile, e.getClass().getSimpleName());
        }
    }

    private void handleDelete(List<RepoFile> matches, RepoManager targetRepo, SyncCounters counters) {
        matches.stream()
                .filter(b -> !b.missing())
                .forEach(b -> performDelete(b, targetRepo, counters));
    }

    private void performDelete(RepoFile b, RepoManager targetRepo, SyncCounters counters) {
        counters.incrementDeleted();
        Path bFile = Paths.get(targetRepo.getRepo().absolutePath()).resolve(b.relativePath());
        try {
            fileSystem.delete(bFile);
            counters.incrementRemoved();
            targetRepo.addRepoFile(b.withMissing(true));
        } catch (IOException e) {
            counters.incrementErrors();
            log.error("Could not delete {} {}", bFile, e.getClass().getSimpleName());
        }
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
        Result<Repo, DedupError> repo = dedupConfig.getRepo(name);
        if (repo.hasFailed()) {
            log.error("Could not open {} {}", name, repo.error());
            return Result.err(errOffset - 1);
        }
        RepoManager repoManager = RepoManager.forRepo(repo.value(), dedupConfig, fileSystem);
        Result<Statistics, DedupError> loadResult = repoManager.load();
        if (loadResult.hasFailed()) {
            log.error("Could not load {} {}", name, loadResult.error());
            return Result.err(errOffset - 2);
        }
        return Result.ok(repoManager);
    }


    record Repos(RepoManager source, RepoManager target) {
    }
}
