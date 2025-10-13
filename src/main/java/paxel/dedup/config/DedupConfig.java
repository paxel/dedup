package paxel.dedup.config;

import lombok.NonNull;
import paxel.dedup.model.Repo;
import paxel.dedup.model.errors.*;
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
    Result<List<Repo>, OpenRepoError> getRepos();

    /**
     * Provide a {@link Repo} by name
     *
     * @return the {@link Repo} or {@link  Optional#empty()}
     */
    @NonNull
    Result<Repo, OpenRepoError> getRepo(@NonNull String name);

    /**
     * Creates a Repo with given name or explains the reason why not.
     *
     * @param name The name of the new repo.
     * @return The new {@link Repo} or {@link CreateRepoError} if the repo could not be read.
     */
    @NonNull
    Result<Repo, CreateRepoError> createRepo(@NonNull String name, @NonNull Path path, int indices);

    @NonNull Result<Repo, ModifyRepoError> changePath(@NonNull String name, @NonNull Path path);

    /**
     * Deletes a Repo with given name or explains the reason why not.
     *
     * @param name The repo to be deleted.
     * @return {@code true} if the repo existed and was deleted. {@code false} if the repo did not exist. {@link DeleteRepoError} if the repo could not be deleted.
     */
    @NonNull
    Result<Boolean, DeleteRepoError> deleteRepo(@NonNull String name);

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
     * @return {@code true} if the repo existed and was moved. {@code false} if the repo did not exist or was not moved. {@link RenameRepoError} if the repo could not be moved.
     */
    Result<Boolean, RenameRepoError> renameRepo(String oldName, String newName);
}
