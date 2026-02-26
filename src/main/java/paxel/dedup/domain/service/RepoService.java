package paxel.dedup.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
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
        var repoManager = paxel.dedup.repo.domain.repo.RepoManager.forRepo(repo, dedupConfig, new paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter());
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
}
