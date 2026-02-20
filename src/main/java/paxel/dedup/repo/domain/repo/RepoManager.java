package paxel.dedup.repo.domain.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.Getter;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.CloseError;
import paxel.dedup.domain.model.errors.IoError;
import paxel.dedup.domain.model.errors.LoadError;
import paxel.dedup.domain.model.errors.WriteError;
import paxel.dedup.domain.model.BinaryFormatter;
import paxel.dedup.domain.model.FileHasher;
import paxel.dedup.domain.model.HexFormatter;
import paxel.dedup.domain.model.MimetypeProvider;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

public class RepoManager {
    @Getter
    private final Repo repo;
    private final Map<Integer, IndexManager> indices = new ConcurrentHashMap<>();
    private final ObjectReader objectReader;
    private final ObjectWriter objectWriter;
    private final FileSystem fileSystem;
    @Getter
    private final Path repoDir;
    private final BinaryFormatter binaryFormatter = new HexFormatter();


    public RepoManager(Repo repo, DedupConfig dedupConfig, ObjectMapper objectMapper, FileSystem fileSystem) {
        this.repo = repo;
        this.fileSystem = fileSystem;
        objectReader = objectMapper.readerFor(RepoFile.class);
        objectWriter = objectMapper.writerFor(RepoFile.class);
        repoDir = dedupConfig.getRepoDir().resolve(repo.name());
    }

    private static String nameIndexFile(int index) {
        return index + ".idx";
    }

    public Result<Statistics, LoadError> load() {
        for (IndexManager index : indices.values()) {
            Result<Boolean, CloseError> close = index.close();
            if (close.hasFailed()) {
                return close.mapError(f -> new LoadError(f.path(), f.ioException(), "Failed closing the IndexManager"));
            }
        }
        indices.clear();
        Statistics sum = new Statistics(repoDir.toString());

        for (int index = 0; index < repo.indices(); index++) {
            Path indexPath = repoDir.resolve(nameIndexFile(index));
            // Ensure index file exists for a freshly initialized repo
            try {
                if (!fileSystem.exists(indexPath)) {
                    // Ensure parent directory exists (defensive)
                    fileSystem.createDirectories(indexPath.getParent());
                    // Create an empty index file
                    fileSystem.write(indexPath, new byte[0], StandardOpenOption.CREATE);
                }
            } catch (IOException e) {
                return Result.err(new LoadError(indexPath, e, "Could not initialize index file"));
            }

            IndexManager indexManager = new IndexManager(indexPath, objectReader, objectWriter, fileSystem);
            Result<Statistics, LoadError> load = indexManager.load();
            if (load.hasFailed()) {
                return load;
            }
            sum.add(load.value());
            indices.put(index, indexManager);
        }

        return Result.ok(sum);
    }

    public Stream<RepoFile> stream() {
        return indices.values().stream().flatMap(IndexManager::stream);
    }

    public List<RepoFile> getByHash(String hash) {
        return indices.values().stream()
                .map(i -> i.getByHash(hash))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .toList();

    }

    public List<RepoFile> getByHashAndSize(String hash, Long size) {
        return indices.values().stream()
                .map(i -> i.getByHash(hash))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(r -> Objects.equals(r.size(), size))
                .toList();

    }

    public RepoFile getByPath(String relative) {
        List<RepoFile> list = indices.values().stream()
                .map(i -> i.getByPath(relative))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RepoFile::lastModified))
                .toList();
        if (list.isEmpty())
            return null;
        return list.getLast();
    }

    public Result<RepoFile, WriteError> addRepoFile(RepoFile repoFile) {
        return indices.get((int) (repoFile.size() % repo.indices()))
                .add(repoFile)
                .map(f -> repoFile, Function.identity());
    }

    public CompletableFuture<Result<RepoFile, WriteError>> addPath(Path absolutePath, FileHasher fileHasher, MimetypeProvider mimetypeProvider) {
        if (!fileSystem.exists(absolutePath)) {
            return CompletableFuture.completedFuture(Result.ok(null));
        }
        Path relativize = Paths.get(repo.absolutePath()).relativize(absolutePath);
        RepoFile oldRepoFile = getByPath(relativize.toString());

        Result<Long, LoadError> sizeResult = getSize(absolutePath);
        if (sizeResult.hasFailed()) {
            return CompletableFuture.completedFuture(sizeResult.mapError(f -> new WriteError(null, f.path(), f.ioException())));
        }
        Result<FileTime, LoadError> lastModifiedResult = getLastModifiedTime(absolutePath);
        if (lastModifiedResult.hasFailed()) {
            return CompletableFuture.completedFuture(lastModifiedResult.mapError(f -> new WriteError(null, f.path(), f.ioException())));
        }

        Long size = sizeResult.value();
        FileTime fileTime = lastModifiedResult.value();

        if (oldRepoFile != null) {
            if (Objects.equals(oldRepoFile.size(), size)) {
                if (fileTime.toMillis() <= oldRepoFile.lastModified()) {
                    if (!oldRepoFile.missing()) {
                        return CompletableFuture.completedFuture(Result.ok(null));
                    } else {
                        // repapeared
                        return CompletableFuture.completedFuture(addRepoFile(oldRepoFile.withMissing(false)));
                    }
                }
            }
        }

        return calcHash(absolutePath, size, fileHasher).thenApply(hashResult -> {
            if (hashResult.hasFailed())
                return hashResult.mapError(l -> new WriteError(null, absolutePath, l.ioException()));

            Result<String, IoError> stringIoErrorResult = mimetypeProvider.get(absolutePath);
            RepoFile repoFile = RepoFile.builder()
                    .size(size)
                    .relativePath(relativize.toString())
                    .lastModified(fileTime.toMillis())
                    .hash(hashResult.value())
                    .mimeType(stringIoErrorResult.getValueOr(null))
                    .build();

            return addRepoFile(repoFile);
        });
    }

    private CompletableFuture<Result<String, LoadError>> calcHash(Path absolutePath, long size, FileHasher fileHasher) {
        if (size < 20) {
            try {
                return CompletableFuture.completedFuture(Result.ok(binaryFormatter.format(fileSystem.readAllBytes(absolutePath))));
            } catch (IOException e) {
                return CompletableFuture.completedFuture(Result.err(new LoadError(absolutePath, e, e.toString())));
            }
        }
        return fileHasher.hash(absolutePath);
    }

    private Result<FileTime, LoadError> getLastModifiedTime(Path absolutePath) {
        try {
            return Result.ok(fileSystem.getLastModifiedTime(absolutePath));
        } catch (IOException e) {
            return Result.err(new LoadError(absolutePath, e, "Could not get last modified"));
        }
    }

    private Result<Long, LoadError> getSize(Path absolutePath) {
        try {
            return Result.ok(fileSystem.size(absolutePath));
        } catch (IOException e) {
            return Result.err(new LoadError(absolutePath, e, "Could not get size"));
        }
    }


}