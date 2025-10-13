package paxel.dedup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.NonNull;
import paxel.dedup.model.Repo;
import paxel.dedup.model.errors.*;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;


public final class DefaultDedupConfig implements DedupConfig {

    public static final String DEDUP_REPO_YML = "dedup_repo.yml";
    private final Path repoRootPath;
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

    public DefaultDedupConfig(Path repoRootPath) {
        this.repoRootPath = repoRootPath;
    }


    @Override
    public @NonNull Result<List<Repo>, OpenRepoError> getRepos() {
        if (!Files.exists(repoRootPath)) {
            return Result.err(OpenRepoError.notFound(repoRootPath));
        }
        try (Stream<Path> list = Files.list(repoRootPath)) {
            return Result.ok(list.filter(Files::isDirectory).map(p -> getRepo(p.getFileName().toString())).filter(Result::isSuccess).map(Result::value).toList());
        } catch (IOException e) {
            return Result.err(OpenRepoError.ioError(repoRootPath, e));
        }
    }

    @Override
    public @NonNull Result<Repo, OpenRepoError> getRepo(@NonNull String name) {
        Path repoPath = repoRootPath.resolve(name);
        if (!Files.exists(repoPath)) {
            return Result.err(OpenRepoError.notFound(repoPath));
        }
        Path resolve = repoPath.resolve(DEDUP_REPO_YML);
        if (!Files.exists(resolve)) {
            return Result.err(OpenRepoError.notFound(resolve));
        }

        try {
            return Result.ok(objectMapper.readerFor(Repo.class).readValue(resolve.toFile(), Repo.class));
        } catch (IOException e) {
            return Result.err(OpenRepoError.ioError(resolve, e));
        }

    }

    @Override
    public @NonNull Result<Repo, CreateRepoError> createRepo(@NonNull String name, @NonNull Path path, int indices) {
        Path repoPath = repoRootPath.resolve(name);
        if (Files.exists(repoPath)) {
            return Result.err(CreateRepoError.exists(repoPath));
        }
        try {
            Path path1 = Files.createDirectories(repoPath);
        } catch (IOException e) {
            return Result.err(CreateRepoError.ioError(repoPath, e));
        }
        Path ymlFile = repoPath.resolve(DEDUP_REPO_YML);
        if (Files.exists(ymlFile)) {
            return Result.err(CreateRepoError.exists(ymlFile));
        }

        return writeRepoFiles(name, path, indices, ymlFile);
    }

    public @NonNull Result<Repo, ModifyRepoError> changePath(@NonNull String name, @NonNull Path path) {

        Result<Repo, OpenRepoError> repo = this.getRepo(name);
        if (repo.hasFailed()) {
            return repo.mapError(e -> new ModifyRepoError(e.path(), e.ioException()));
        }
        Path ymlFile = repoRootPath.resolve(name).resolve(DEDUP_REPO_YML);
        return writeRepoFile(name, path, repo.value().indices(), ymlFile)
                .map(Function.identity(), e -> new ModifyRepoError(path, e));
    }

    private Result<Repo, CreateRepoError> writeRepoFiles(String name, Path path, int indices, Path ymlFile) {
        try {
            Result<Repo, IOException> repo = writeRepoFile(name, path, indices, ymlFile);
            if (repo.hasFailed())
                return repo.mapError(e -> new CreateRepoError(path, repo.error()));

            for (int i = 0; i < indices; i++) {
                Files.createFile(ymlFile.resolveSibling(i + ".idx"));
            }
            return Result.ok(repo.value());
        } catch (IOException e) {
            return Result.err(CreateRepoError.ioError(ymlFile, e));
        }
    }


    private Result<Repo, IOException> writeRepoFile(String name, Path path, int indices, Path ymlFile) {
        Repo repo = new Repo(name, path.toAbsolutePath().toString(), indices);
        try {
            objectMapper.writerFor(Repo.class).writeValue(ymlFile.toFile(), repo);
        } catch (IOException e) {
            return Result.err(e);
        }
        return Result.ok(repo);
    }

    @Override
    public @NonNull Result<Boolean, DeleteRepoError> deleteRepo(@NonNull String name) {
        Path repoPath = repoRootPath.resolve(name);
        if (!Files.exists(repoPath)) {
            return Result.ok(false);
        }
        List<Exception> exceptions = new ArrayList<>();
        Result<Boolean, DeleteRepoError> deleteResult = deleteAllFiles(repoPath, exceptions);

        if (deleteResult.hasFailed()) {
            return deleteResult;
        }

        if (!deleteResult.value()) {
            return Result.err(DeleteRepoError.ioErrors(repoPath, exceptions));
        }


        // all deleted
        return deleteResult;
    }

    @NonNull
    private Result<Boolean, DeleteRepoError> deleteAllFiles(Path repoPath, List<Exception> exceptions) {
        try {
            Path resolve = Files.move(repoPath, repoPath.resolveSibling(repoPath.getFileName().toString() + "_del"));

            try (Stream<Path> pathStream = Files.walk(resolve)) {
                pathStream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(file -> {
                    try {
                        boolean delete = file.delete();
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
            return Result.err(DeleteRepoError.ioError(repoPath, e));
        }
    }

    @Override
    public @NonNull Path getRepoDir() {
        return repoRootPath;
    }

    @Override
    public Result<Boolean, RenameRepoError> renameRepo(String oldName, String newName) {
        if (oldName.equals(newName))
            return Result.ok(false);
        Result<Repo, OpenRepoError> repo = getRepo(oldName);
        if (repo.hasFailed())
            return Result.ok(false);
        try {
            Files.move(repoRootPath.resolve(oldName), repoRootPath.resolve(newName));
        } catch (IOException e) {
            return Result.err(RenameRepoError.ioError(repoRootPath.resolve(newName), e));
        }
        Path ymlFile = repoRootPath.resolve(newName).resolve(DEDUP_REPO_YML);

        return writeRepoFile(newName, Paths.get(repo.value().absolutePath()), repo.value().indices(), ymlFile)
                .map(a -> true, e -> new RenameRepoError(ymlFile, List.of(e)));
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

}
