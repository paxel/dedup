package paxel.dedup.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.repo.domain.files.FilesProcess;
import paxel.dedup.repo.domain.repo.CopyRepoProcess;
import paxel.dedup.repo.domain.repo.MoveRepoProcess;
import paxel.dedup.repo.domain.repo.PruneReposProcess;
import paxel.dedup.repo.domain.repo.RelocateRepoProcess;
import paxel.lib.Result;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class RepoService {

    private final DedupConfig dedupConfig;
    private final FileSystem fileSystem;

    public RepoService(DedupConfig dedupConfig) {
        this(dedupConfig, new paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter());
    }

    /**
     * Retrieves all repositories sorted by name.
     *
     * @return a list of all repositories, or a DedupError if retrieval fails.
     */
    public Result<List<Repo>, DedupError> getRepos() {
        return dedupConfig.getRepos()
                .map(repos -> repos.stream()
                        .map(this::enrichWithStats)
                        .sorted(Comparator.comparing(Repo::name, String::compareTo))
                        .collect(Collectors.toList()), Function.identity());
    }

    private Repo enrichWithStats(Repo repo) {
        var repoManager = paxel.dedup.repo.domain.repo.RepoManager.forRepo(repo, dedupConfig, fileSystem);
        var loadResult = repoManager.load();
        if (loadResult.isSuccess()) {
            Map<String, Long> mimeDistribution = new java.util.HashMap<>();
            long totalSize = 0;
            long fileCount = 0;
            for (var file : repoManager.stream().filter(f -> !f.missing()).toList()) {
                fileCount++;
                totalSize += file.size();
                if (file.mimeType() != null && !file.mimeType().isBlank()) {
                    mimeDistribution.put(file.mimeType(), mimeDistribution.getOrDefault(file.mimeType(), 0L) + 1L);
                }
            }
            return repo.withStats(paxel.dedup.domain.model.RepoStats.builder()
                    .fileCount(fileCount)
                    .totalSize(totalSize)
                    .mimeTypeDistribution(mimeDistribution)
                    .build());
        }
        return repo;
    }

    /**
     * Retrieves a repository by name.
     *
     * @param name the name of the repository.
     * @return the repository, or a DedupError if it doesn't exist or retrieval fails.
     */
    public Result<Repo, DedupError> getRepo(String name) {
        return dedupConfig.getRepo(name);
    }

    /**
     * Creates a new repository.
     *
     * @param name       the name of the repository.
     * @param path       the local filesystem path to the repository data.
     * @param indices    the number of index files to use.
     * @param codec      the codec to use for index files.
     * @param compressed whether to use compression.
     * @return the created repository, or a DedupError if creation fails.
     */
    public Result<Repo, DedupError> createRepo(String name, Path path, int indices, Repo.Codec codec, boolean compressed) {
        log.info("Creating Repo '{}' at '{}' (codec={}, compressed={})", name, path, codec, compressed);
        return dedupConfig.createRepo(name, path, indices, codec, compressed);
    }

    /**
     * Creates a new repository with default settings.
     *
     * @param name    the name of the repository.
     * @param path    the local filesystem path to the repository data.
     * @param indices the number of index files to use.
     * @return the created repository, or a DedupError if creation fails.
     */
    public Result<Repo, DedupError> createRepo(String name, Path path, int indices) {
        return createRepo(name, path, indices, Repo.Codec.MESSAGEPACK, false);
    }

    /**
     * Deletes an existing repository.
     *
     * @param name the name of the repository to delete.
     * @return true if deleted, or a DedupError if deletion fails.
     */
    public Result<Boolean, DedupError> deleteRepo(String name) {
        log.info("Deleting Repo '{}'", name);
        return dedupConfig.deleteRepo(name);
    }

    /**
     * Updates the configuration for an existing repository.
     *
     * @param name       the name of the repository.
     * @param codec      the codec to use for index files.
     * @param compressed whether to use compression.
     * @return the updated repository, or a DedupError if update fails.
     */
    public Result<Repo, DedupError> updateRepoConfig(String name, Repo.Codec codec, boolean compressed) {
        log.info("Updating config for Repo '{}': codec={}, compressed={}", name, codec, compressed);
        return dedupConfig.setRepoConfig(name, codec, compressed);
    }

    /**
     * Prunes missing files from the repository index.
     *
     * @param name the name of the repository.
     * @return 0 on success, or a DedupError if pruning fails.
     */
    public Result<Integer, DedupError> pruneRepo(String name) {
        log.info("Pruning Repo '{}'", name);
        return new PruneReposProcess(new CliParameter(), List.of(name), false, 1, dedupConfig, false, null).prune();
    }

    /**
     * Copies or moves files from a repository to a target directory.
     *
     * @param name     the name of the source repository.
     * @param target   the target directory path.
     * @param move     true to move, false to copy.
     * @param filter   optional filter for files.
     * @param appendix optional appendix for file names.
     * @return 0 on success, or a DedupError if copy/move fails.
     */
    public Result<Integer, DedupError> copyFiles(String name, String target, boolean move, String filter, String appendix) {
        log.info("{} files from Repo '{}' to '{}' (filter={}, appendix={})", move ? "Moving" : "Copying", name, target, filter, appendix);
        FilesProcess process = new FilesProcess(new CliParameter(), name, dedupConfig, filter, fileSystem);
        int result = process.copy(target, move, appendix);
        if (result < 0) {
            return Result.err(DedupError.of(paxel.dedup.domain.model.errors.ErrorType.UPDATE_REPO, "Copy/Move failed with code " + result));
        }
        return Result.ok(result);
    }

    /**
     * Relocates a repository to a new path.
     *
     * @param name    the name of the repository.
     * @param newPath the new absolute path.
     * @return the updated repository, or a DedupError.
     */
    public Result<Repo, DedupError> relocateRepo(String name, String newPath) {
        log.info("Relocating Repo '{}' to '{}'", name, newPath);
        return new RelocateRepoProcess(new CliParameter(), name, newPath, dedupConfig).move().map(i -> dedupConfig.getRepo(name).value(), Function.identity());
    }

    /**
     * Clones a repository to a new one with a new path.
     *
     * @param sourceName      the name of the source repository.
     * @param destinationName the name of the new repository.
     * @param newPath         the path for the new repository.
     * @return 0 on success, or a DedupError.
     */
    public Result<Integer, DedupError> cloneRepo(String sourceName, String destinationName, String newPath) {
        log.info("Cloning Repo '{}' to '{}' at '{}'", sourceName, destinationName, newPath);
        return new CopyRepoProcess(new CliParameter(), sourceName, destinationName, newPath, dedupConfig).copy();
    }

    /**
     * Moves a repository to a new name.
     *
     * @param sourceName      the current name of the repository.
     * @param destinationName the new name for the repository.
     * @return 0 on success, or a DedupError.
     */
    public Result<Integer, DedupError> moveRepo(String sourceName, String destinationName) {
        log.info("Moving Repo '{}' to '{}'", sourceName, destinationName);
        return new MoveRepoProcess(new CliParameter(), sourceName, destinationName, dedupConfig).move();
    }
}
