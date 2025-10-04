package paxel.dedup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.NonNull;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;


public final class DefaultDedupConfig implements DedupConfig {

    private final Path repoRootPath;
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    public DefaultDedupConfig(Path repoRootPath) {
        this.repoRootPath = repoRootPath;
    }


    @Override
    public @NonNull Result<List<Repo>, OpenRepoError> getRepos() {
        if (!Files.exists(repoRootPath)) {
            return Result.err(OpenRepoError.notFound(repoRootPath));
        }
        try (Stream<Path> list = Files.list(repoRootPath)) {
            return Result.ok(
                    list.filter(Files::isDirectory)
                            .map(p -> getRepo(p.getFileName().toString()))
                            .filter(Result::isSuccess)
                            .map(Result::value)
                            .toList());
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
        Path resolve = repoPath.resolve("dedup_repo.yml");
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
            Files.createDirectories(repoPath);
        } catch (IOException e) {
            return Result.err(CreateRepoError.ioError(repoPath, e));
        }
        Path resolve = repoPath.resolve("dedup_repo.yml");
        if (Files.exists(resolve)) {
            return Result.err(CreateRepoError.exists(resolve));
        }

        try {
            Repo repo = new Repo(name, path.toAbsolutePath(), indices);
            objectMapper.writerFor(Repo.class).writeValue(resolve.toFile(), repo);
            return Result.ok(repo);
        } catch (IOException e) {
            return Result.err(CreateRepoError.ioError(resolve, e));
        }
    }

    @Override
    public @NonNull Result<Boolean, DeleteRepoError> deleteRepo(@NonNull Repo repo) {
        return Result.err(new DeleteRepoError());
    }

    @Override
    public @NonNull Path getRepoDir() {
        return repoRootPath;
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
