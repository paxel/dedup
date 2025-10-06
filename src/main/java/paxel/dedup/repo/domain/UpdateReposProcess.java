package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.Repo;
import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.model.errors.OpenRepoError;
import paxel.dedup.model.errors.UpdateRepoError;
import paxel.dedup.model.errors.WriteError;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class UpdateReposProcess {


    public int update(List<String> names, boolean all) {
        // TODO: use configured config relativePath
        Result<DedupConfig, CreateConfigError> configResult = DedupConfigFactory.create();

        if (configResult.hasFailed()) {
            IOException ioException = configResult.error().ioException();
            if (ioException != null) {
                System.err.println(configResult.error().path() + " not a valid config relativePath");
                ioException.printStackTrace();
            }
            return -1;
        }
        ObjectMapper objectMapper = new ObjectMapper();

        DedupConfig dedupConfig = configResult.value();
        if (all) {
            Result<List<Repo>, OpenRepoError> lsResult = dedupConfig.getRepos();
            if (lsResult.hasFailed()) {
                IOException ioException = lsResult.error().ioException();
                if (ioException != null) {
                    System.err.println(lsResult.error().path() + " ls failed");
                    ioException.printStackTrace();
                }
                return -3;
            }
            for (Repo repo : lsResult.value()) {
                Result<Long, UpdateRepoError> result = updateRepo(new RepoManager(repo, dedupConfig, objectMapper));
                System.out.println("Updated " + repo + " " + result);
            }
            return 0;
        }

        for (String name : names) {
            Result<Repo, OpenRepoError> getRepoResult = dedupConfig.getRepo(name);
            if (getRepoResult.isSuccess()) {
                Result<Long, UpdateRepoError> result = updateRepo(new RepoManager(getRepoResult.value(), dedupConfig, objectMapper));
                System.out.println("Updated " + name + " " + result);
            }
        }
        return 0;
    }

    private Result<Long, UpdateRepoError> updateRepo(RepoManager repo) {
        AtomicLong added = new AtomicLong();
        try (Stream<Path> x = Files.walk(Paths.get(repo.getRepo().absolutePath()))) {
            x.forEach(absolutePath -> {
                Result<Boolean, WriteError> add = repo.add(absolutePath);
                if (add.isSuccess() && add.value() == Boolean.TRUE)
                    added.incrementAndGet();
            });
        } catch (IOException e) {
            return Result.err(UpdateRepoError.ioException(repo.getRepoDir(), e));
        }
        return Result.ok(added.get());
    }


}
