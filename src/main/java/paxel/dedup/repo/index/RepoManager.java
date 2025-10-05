package paxel.dedup.repo.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.data.Repo;
import paxel.dedup.data.RepoFile;
import paxel.lib.Result;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class RepoManager {
    private final Repo repo;
    private final Map<Integer, IndexManager> indices = new ConcurrentHashMap<>();
    private final ObjectReader objectReader;
    private final ObjectWriter objectWriter;
    private final Path repoDir;


    public RepoManager(Repo repo, DedupConfig dedupConfig, ObjectMapper objectMapper) {
        this.repo = repo;
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
            IndexManager indexManager = new IndexManager(repoDir.resolve(index + ".idx"), objectReader, objectWriter);
            Result<Statistics, LoadError> load = indexManager.load();
            if (load.hasFailed()) {
                return load;
            }
            sum.add(load.value());
            indices.put(index, indexManager);
        }

        return Result.ok(sum);
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

    public Result<Boolean, WriteError> add(Path absolutePath) {
        if (!Files.exists(absolutePath)) {
            return Result.ok(false);
        }
        Path relativize = absolutePath.relativize(Paths.get(repo.absolutePath()));
        RepoFile oldRepoFile = getByPath(relativize.toString());
        Result<Long, LoadError> sizeResult = getSize(absolutePath);
        if (sizeResult.hasFailed())
            return sizeResult.mapError(f -> new WriteError(null, f.path(), f.ioException()));
        Result<FileTime, LoadError> lastModifiedResult = getLastModifiedTime(absolutePath);
        if (lastModifiedResult.hasFailed())
            return sizeResult.mapError(f -> new WriteError(null, f.path(), f.ioException()));

        Long value = sizeResult.value();
        FileTime fileTime = lastModifiedResult.value();

        if (oldRepoFile != null) {
            if (oldRepoFile.size() == value) {
                if (fileTime.toMillis() <= oldRepoFile.lastModified()) {
                    return Result.ok(false);
                }
            }
        }

        RepoFile repoFile = RepoFile.builder()
                .size(value)
                .relativePath(relativize.toString())
                .lastModified(fileTime.toMillis())
                .hash(createHash(absolutePath))
                .build();

        return indices.get((int) (value % repo.indices()))
                .add(repoFile)
                .map(f -> true, Function.identity());
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

    public Result<String, LoadError> calculateSHA1(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            try (InputStream in = Files.newInputStream(path)) {
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();

            // Konvertiert das Hash-Array in einen hexadezimalen String
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return Result.err(new LoadError(path, e, e.toString()));
        }
    }
}