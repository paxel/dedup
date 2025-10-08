package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.Getter;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.model.Repo;
import paxel.dedup.model.RepoFile;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.CloseError;
import paxel.dedup.model.errors.LoadError;
import paxel.dedup.model.errors.WriteError;
import paxel.dedup.model.utils.BinaryFormatter;
import paxel.dedup.model.utils.FileHasher;
import paxel.dedup.model.utils.HexFormatter;
import paxel.dedup.model.utils.Sha1Hasher;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

public class RepoManager {
    @Getter
    private final Repo repo;
    private final Map<Integer, IndexManager> indices = new ConcurrentHashMap<>();
    private final ObjectReader objectReader;
    private final ObjectWriter objectWriter;
    private final CliParameter cliParameter;
    @Getter
    private final Path repoDir;
    private final BinaryFormatter binaryFormatter = new HexFormatter();
    private final FileHasher fileHasher = new Sha1Hasher(binaryFormatter);


    public RepoManager(Repo repo, DedupConfig dedupConfig, ObjectMapper objectMapper, CliParameter cliParameter) {
        this.repo = repo;
        this.cliParameter = cliParameter;
        objectReader = objectMapper.readerFor(RepoFile.class);
        objectWriter = objectMapper.writerFor(RepoFile.class);
        repoDir = dedupConfig.getRepoDir().resolve(repo.name());
    }


    public Result<Statistics, LoadError> load() {
        for (IndexManager index : indices.values()) {
            Result<Boolean, CloseError> close = index.close();
            if (close.hasFailed()) {
                return close.mapError(f -> new LoadError(close.error().path(), close.error().ioException(), "Failed closing the IndexManager"));
            }
            indices.clear();
        }
        Statistics sum = new Statistics(repoDir.toString());

        for (int index = 0; index < repo.indices(); index++) {
            IndexManager indexManager = new IndexManager(repoDir.resolve(nameIndexFile(index)), objectReader, objectWriter, cliParameter);
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


    private static String nameIndexFile(int index) {
        return index + ".idx";
    }

    public List<RepoFile> getByHash(String hash) {
        return indices.values().stream()
                .map(i -> i.getByHash(hash))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
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

    public Result<Boolean, WriteError> addRepoFile(RepoFile repoFile) {
        return indices.get((int) (repoFile.size() % repo.indices()))
                .add(repoFile)
                .map(f -> true, Function.identity());
    }

    public Result<Boolean, WriteError> addPath(Path absolutePath) {
        if (!Files.exists(absolutePath)) {
            return Result.ok(false);
        }
        Path relativize = Paths.get(repo.absolutePath()).relativize(absolutePath);
        RepoFile oldRepoFile = getByPath(relativize.toString());

        Result<Long, LoadError> sizeResult = getSize(absolutePath);
        if (sizeResult.hasFailed()) {
            return sizeResult.mapError(f -> new WriteError(null, f.path(), f.ioException()));
        }
        Result<FileTime, LoadError> lastModifiedResult = getLastModifiedTime(absolutePath);
        if (lastModifiedResult.hasFailed()) {
            return sizeResult.mapError(f -> new WriteError(null, f.path(), f.ioException()));
        }

        Long size = sizeResult.value();
        FileTime fileTime = lastModifiedResult.value();

        if (oldRepoFile != null) {
            if (Objects.equals(oldRepoFile.size(), size)) {
                if (fileTime.toMillis() <= oldRepoFile.lastModified()) {
                    return Result.ok(false);
                } else {
                    if (cliParameter.isVerbose()) {
                        System.out.println("different last modified for " + relativize + " " + fileTime.toMillis() + " > " + oldRepoFile.lastModified());
                    }
                }
            } else {
                if (cliParameter.isVerbose()) {
                    System.out.println("different sizes for " + relativize + " " + size + " != " + oldRepoFile.size());
                }
            }
        } else {
            if (cliParameter.isVerbose()) {
                System.out.println("No old file found for " + relativize);
            }
        }

        Result<String, LoadError> hashResult = calcHash(absolutePath, size);
        if (hashResult.hasFailed())
            return hashResult.mapError(l -> new WriteError(null, absolutePath, l.ioException()));

        RepoFile repoFile = RepoFile.builder()
                .size(size)
                .relativePath(relativize.toString())
                .lastModified(fileTime.toMillis())
                .hash(hashResult.value())
                .build();

        return addRepoFile(repoFile);
    }

    private Result<String, LoadError> calcHash(Path absolutePath, long size) {
        if (size < 20) {
            try {
                return Result.ok(binaryFormatter.format(Files.readAllBytes(absolutePath)));
            } catch (IOException e) {
                return Result.err(new LoadError(absolutePath, e, e.toString()));
            }
        }
        return fileHasher.hash(absolutePath);
    }

    private Result<FileTime, LoadError> getLastModifiedTime(Path absolutePath) {
        try {
            return Result.ok(Files.getLastModifiedTime(absolutePath));
        } catch (IOException e) {
            return Result.err(new LoadError(absolutePath, e, "Could not get last modified"));
        }
    }

    private Result<Long, LoadError> getSize(Path absolutePath) {
        try {
            return Result.ok(Files.size(absolutePath));
        } catch (IOException e) {
            return Result.err(new LoadError(absolutePath, e, "Could not get size"));
        }
    }


}