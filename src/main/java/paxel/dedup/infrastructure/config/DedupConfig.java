package paxel.dedup.infrastructure.config;

import lombok.NonNull;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.lib.Result;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface DedupConfig {

    /**
     * Provide all existing {@link Repo}s
     *
     * @return the repos
     */
    @NonNull
    Result<List<Repo>, DedupError> getRepos();

    /**
     * Provide a {@link Repo} by name
     *
     * @return the {@link Repo} or {@link  Optional#empty()}
     */
    @NonNull
    Result<Repo, DedupError> getRepo(@NonNull String name);

    /**
     * Creates a Repo with given name or explains the reason why not.
     *
     * @param name The name of the new repo.
     * @return The new {@link Repo} or an error if the repo could not be created.
     */
    @NonNull
    Result<Repo, DedupError> createRepo(@NonNull String name, @NonNull Path path, int indices);

    @NonNull
    Result<Repo, DedupError> changePath(@NonNull String name, @NonNull Path path);

    /**
     * Deletes a Repo with given name or explains the reason why not.
     *
     * @param name The repo to be deleted.
     * @return {@code true} if the repo existed and was deleted; {@code false} if the repo did not exist; or an error when the repo could not be deleted.
     */
    @NonNull
    Result<Boolean, DedupError> deleteRepo(@NonNull String name);

    /**
     * Retrieve the repo root dir
     *
     * @return the root relativePath.
     */
    @NonNull
    Path getRepoDir();

    /**
     * Renames a repo and moves it to the new name
     *
     * @param oldName the current name of the repo
     * @param newName the new name of the repo
     * @return {@code true} if the repo existed and was moved; {@code false} if the repo did not exist or was not moved; or an error when the repo could not be moved.
     */
    Result<Boolean, DedupError> renameRepo(String oldName, String newName);

    /**
     * Updates the config of the repo YAML while keeping name, path, and indices the same.
     */
    @NonNull
    default Result<Repo, DedupError> setRepoConfig(@NonNull String name, @NonNull Repo.Codec codec, boolean compressed) {
        return Result.err(DedupError.of(paxel.dedup.domain.model.errors.ErrorType.MODIFY_REPO,
                getRepoDir().resolve(name).resolve("dedup_repo.yml") + ": failed persisting repo config"));
    }

    /**
     * Updates the codec setting of the repo YAML while keeping name, path, and indices the same.
     *
     * @deprecated Use {@link #setRepoConfig(String, Repo.Codec, boolean)} instead.
     */
    @Deprecated
    @NonNull
    default Result<Repo, DedupError> setRepoConfig(@NonNull String name, @NonNull Repo.Codec codec) {
        return setRepoConfig(name, codec, false);
    }

    /**
     * Updates the codec of the repo YAML while keeping name, path, and indices the same.
     *
     * @deprecated Use {@link #setRepoConfig(String, Repo.Codec, boolean)} instead.
     */
    @Deprecated
    @NonNull
    default Result<Repo, DedupError> setCodec(@NonNull String name, @NonNull Repo.Codec codec) {
        return setRepoConfig(name, codec, false);
    }
}
