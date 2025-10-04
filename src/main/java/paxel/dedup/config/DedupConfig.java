package paxel.dedup.config;

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
    List<Repo> getRepos();

    /**
     * Provide a {@link Repo} by name
     *
     * @return the {@link Repo} or {@link  Optional#empty()}
     */
    Optional<Repo> getRepo(String name);

    /**
     * Creates a Repo with given name or explains the reason why not.
     *
     * @param name The name of the new repo.
     * @return The new {@link Repo} or {@link CreateRepoError} if the repo could not be created.
     */
    Result<Repo, CreateRepoError> createRepo(String name);

    /**
     * Deletes a Repo with given name or explains the reason why not.
     *
     * @param repo The repo to be deleted.
     * @return {@code true} if the repo existed and was deleted. {@code false} if the repo did not exist. {@link DeleteRepoError} if the repo could not be deleted.
     */
    Result<Boolean, DeleteRepoError> deleteRepo(Repo repo);

    /**
     * Retrieve the repo root dir
     *
     * @return the root path.
     */
    Path getRepoDir();

}
