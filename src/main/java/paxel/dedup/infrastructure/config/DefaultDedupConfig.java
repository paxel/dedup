package paxel.dedup.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;


@RequiredArgsConstructor
public final class DefaultDedupConfig implements DedupConfig {

    public static final String DEDUP_REPO_YML = "dedup_repo.yml";
    private final Path repoRootPath;
    private final FileSystem fileSystem;
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));


    @Override
    public @NonNull Result<List<Repo>, DedupError> getRepos() {
        if (!fileSystem.exists(repoRootPath)) {
            return Result.err(DedupError.of(ErrorType.OPEN_REPO, repoRootPath + " not found"));
        }
        try (Stream<Path> list = fileSystem.list(repoRootPath)) {
            return Result.ok(list.filter(fileSystem::isDirectory).map(p -> getRepo(p.getFileName().toString())).filter(Result::isSuccess).map(Result::value).toList());
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.OPEN_REPO, repoRootPath + " Invalid", e));
        }
    }

    @Override
    public @NonNull Result<Repo, DedupError> getRepo(@NonNull String name) {
        Path repoPath = repoRootPath.resolve(name);
        if (!fileSystem.exists(repoPath)) {
            return Result.err(DedupError.of(ErrorType.OPEN_REPO, repoPath + " not found"));
        }
        Path resolve = repoPath.resolve(DEDUP_REPO_YML);
        if (!fileSystem.exists(resolve)) {
            return Result.err(DedupError.of(ErrorType.OPEN_REPO, resolve + " not found"));
        }

        try {
            byte[] yaml = fileSystem.readAllBytes(resolve);
            @SuppressWarnings("unchecked")
            var map = objectMapper.readValue(yaml, java.util.Map.class);
            String rName = stringOf(map.getOrDefault(RepoYamlKey.NAME.key, name));
            String absolutePath = stringOf(map.get(RepoYamlKey.ABSOLUTE_PATH.key));
            int indices = intOf(map.getOrDefault(RepoYamlKey.INDICES.key, 1));
            Repo.Codec codec = codecOf(stringOf(map.get(RepoYamlKey.CODEC.key))); // defaults to JSON when missing/unknown

            return Result.ok(new Repo(rName, absolutePath, indices, codec));
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.OPEN_REPO, resolve + " Invalid", e));
        }

    }

    @Override
    public @NonNull Result<Repo, DedupError> createRepo(@NonNull String name, @NonNull Path path, int indices) {
        Path repoPath = repoRootPath.resolve(name);
        if (fileSystem.exists(repoPath)) {
            return Result.err(DedupError.of(ErrorType.CREATE_REPO, repoPath + " already exists"));
        }
        try {
            fileSystem.createDirectories(repoPath);
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.CREATE_REPO, repoPath + " not a valid repo relativePath", e));
        }
        Path ymlFile = repoPath.resolve(DEDUP_REPO_YML);
        if (fileSystem.exists(ymlFile)) {
            return Result.err(DedupError.of(ErrorType.CREATE_REPO, ymlFile + " already exists"));
        }

        return writeRepoFiles(name, path, indices, ymlFile);
    }

    @Override
    public @NonNull Result<Repo, DedupError> changePath(@NonNull String name, @NonNull Path path) {

        Result<Repo, DedupError> repo = this.getRepo(name);
        if (repo.hasFailed()) {
            return repo.mapError(e -> DedupError.of(ErrorType.MODIFY_REPO, e.describe(), e.exception()));
        }
        Path ymlFile = repoRootPath.resolve(name).resolve(DEDUP_REPO_YML);
        return writeRepoFile(name, path, repo.value().indices(), ymlFile)
                .map(Function.identity(), e -> DedupError.of(ErrorType.MODIFY_REPO, path + " modify failed", e));
    }

    private Result<Repo, DedupError> writeRepoFiles(String name, Path path, int indices, Path ymlFile) {
        try {
            Result<Repo, IOException> repo = writeRepoFile(name, path, indices, ymlFile);
            if (repo.hasFailed())
                return repo.mapError(e -> DedupError.of(ErrorType.CREATE_REPO, path + " write failed", repo.error()));

            for (int i = 0; i < indices; i++) {
                fileSystem.newOutputStream(ymlFile.resolveSibling(i + ".idx")).close();
            }
            return Result.ok(repo.value());
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.CREATE_REPO, ymlFile + " write failed", e));
        }
    }


    private Result<Repo, IOException> writeRepoFile(String name, Path path, int indices, Path ymlFile) {
        Repo repo = new Repo(name, path.toAbsolutePath().toString(), indices, Repo.Codec.MESSAGEPACK);
        try {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put(RepoYamlKey.NAME.key, repo.name());
            map.put(RepoYamlKey.ABSOLUTE_PATH.key, repo.absolutePath());
            map.put(RepoYamlKey.INDICES.key, repo.indices());
            map.put(RepoYamlKey.CODEC.key, repo.codec().name());
            fileSystem.write(ymlFile, objectMapper.writeValueAsBytes(map));
        } catch (IOException e) {
            return Result.err(e);
        }
        return Result.ok(repo);
    }

    @Override
    public @NonNull Result<Boolean, DedupError> deleteRepo(@NonNull String name) {
        Path repoPath = repoRootPath.resolve(name);
        if (!fileSystem.exists(repoPath)) {
            return Result.ok(false);
        }
        List<Exception> exceptions = new ArrayList<>();
        Result<Boolean, DedupError> deleteResult = deleteAllFiles(repoPath, exceptions);

        if (deleteResult.hasFailed()) {
            return deleteResult;
        }

        if (!deleteResult.value()) {
            Exception cause;
            if (exceptions.isEmpty()) {
                cause = null;
            } else {
                var first = exceptions.getFirst();
                if (first instanceof Exception ex) {
                    cause = ex;
                } else {
                    cause = new Exception(first);
                }
            }
            return Result.err(DedupError.of(ErrorType.DELETE_REPO, "While deleting " + name + " " + exceptions.size() + " exceptions happened",
                    cause));
        }


        // all deleted
        return deleteResult;
    }

    @NonNull
    private Result<Boolean, DedupError> deleteAllFiles(Path repoPath, List<Exception> exceptions) {
        try {
            Path resolve = repoPath.resolveSibling(repoPath.getFileName().toString() + "_del");
            fileSystem.move(repoPath, resolve);

            try (Stream<Path> pathStream = fileSystem.walk(resolve)) {
                pathStream.sorted(Comparator.reverseOrder()).forEach(file -> {
                    try {
                        fileSystem.delete(file);
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                });
            }
            if (exceptions.isEmpty()) {
                return Result.ok(true);
            }
            return Result.ok(false);
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.DELETE_REPO, repoPath + " delete failed", e));
        }
    }

    @Override
    public @NonNull Path getRepoDir() {
        return repoRootPath;
    }

    @Override
    public Result<Boolean, DedupError> renameRepo(String oldName, String newName) {
        if (oldName.equals(newName))
            return Result.ok(false);
        Result<Repo, DedupError> repo = getRepo(oldName);
        if (repo.hasFailed())
            return Result.ok(false);
        try {
            Path resolve1 = repoRootPath.resolve(oldName);
            Path resolve2 = repoRootPath.resolve(newName);
            fileSystem.move(resolve1, resolve2);
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.RENAME_REPO, repoRootPath.resolve(newName) + " rename failed", e));
        }
        Path ymlFile = repoRootPath.resolve(newName).resolve(DEDUP_REPO_YML);
        Result<Repo, IOException> repoIOExceptionResult = writeRepoFile(newName, Paths.get(repo.value().absolutePath()), repo.value().indices(), ymlFile);

        return repoIOExceptionResult
                .map(a -> true, e -> DedupError.of(ErrorType.RENAME_REPO, ymlFile + " write failed", e));
    }

    @Override
    public @NonNull Result<Repo, DedupError> setRepoConfig(@NonNull String name, @NonNull Repo.Codec codec) {
        Result<Repo, DedupError> repo = this.getRepo(name);
        if (repo.hasFailed()) {
            return repo.mapError(e -> DedupError.of(ErrorType.MODIFY_REPO, e.describe(), e.exception()));
        }
        Path ymlFile = repoRootPath.resolve(name).resolve(DEDUP_REPO_YML);
        try {
            Repo updated = new Repo(name, repo.value().absolutePath(), repo.value().indices(), codec);
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put(RepoYamlKey.NAME.key, updated.name());
            map.put(RepoYamlKey.ABSOLUTE_PATH.key, updated.absolutePath());
            map.put(RepoYamlKey.INDICES.key, updated.indices());
            map.put(RepoYamlKey.CODEC.key, updated.codec().name());
            fileSystem.write(ymlFile, objectMapper.writeValueAsBytes(map));
            return Result.ok(updated);
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.MODIFY_REPO, ymlFile + " write failed", e));
        }
    }

    @Override
    @Deprecated
    public @NonNull Result<Repo, DedupError> setCodec(@NonNull String name, @NonNull Repo.Codec codec) {
        return setRepoConfig(name, codec);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (DefaultDedupConfig) obj;
        return Objects.equals(this.repoRootPath, that.repoRootPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repoRootPath);
    }

    @Override
    public String toString() {
        return "DefaultDedupConfig[" + "repoRootPath=" + repoRootPath + ']';
    }

    // --- helpers for YAML <-> Repo mapping without reflection on records (GraalVM-friendly) ---
    private String stringOf(Object o) {
        if (o == null) {
            return null;
        }
        return o.toString();
    }

    private int intOf(Object o) {
        if (o == null) return 1;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private Repo.Codec codecOf(String s) {
        if (s == null || s.isBlank()) return Repo.Codec.JSON; // backward-compatible default
        try {
            return Repo.Codec.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Repo.Codec.JSON;
        }
    }

    // Centralized YAML keys to avoid primitive obsession with map keys
    enum RepoYamlKey {
        NAME("name"),
        ABSOLUTE_PATH("absolutePath"),
        INDICES("indices"),
        CODEC("codec");
        final String key;

        RepoYamlKey(String key) {
            this.key = key;
        }
    }

}
