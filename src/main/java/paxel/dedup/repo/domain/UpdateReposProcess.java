package paxel.dedup.repo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.Repo;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.*;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;


public class UpdateReposProcess {


    private CliParameter cliParameter;

    public int update(List<String> names, boolean all, CliParameter cliParameter) {
        this.cliParameter = cliParameter;
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
                Result<Long, UpdateRepoError> result = updateRepo(new RepoManager(repo, dedupConfig, objectMapper, cliParameter));
                if (cliParameter.isVerbose()) {
                    if (result.isSuccess()) {
                        System.out.println("Updated " + repo + " " + result.value());
                    } else {
                        System.out.println("Updated " + repo + " " + result.error());
                    }
                }

            }
            return 0;
        }

        for (String name : names) {
            Result<Repo, OpenRepoError> getRepoResult = dedupConfig.getRepo(name);
            if (getRepoResult.isSuccess()) {
                Result<Long, UpdateRepoError> result = updateRepo(new RepoManager(getRepoResult.value(), dedupConfig, objectMapper, cliParameter));
                System.out.println("Updated " + name + " " + result);
            }
        }
        return 0;
    }

    private Result<Long, UpdateRepoError> updateRepo(RepoManager repo) {
        Result<Statistics, LoadError> load = repo.load();
        if (load.hasFailed())
            return load.mapError(f -> new UpdateRepoError(repo.getRepoDir(), load.error().ioException()));
        if (cliParameter.isVerbose()) {
            System.out.println("loaded: " + repo.getRepo().name());
            load.value().forCounter((a, b) -> System.out.println(a + ": " + b));
            load.value().forTimer((a, b) -> System.out.println(a + ": " + b));
            System.out.println("--");
        }

        AtomicLong added = new AtomicLong();
        try (Stream<Path> path = Files.walk(Paths.get(repo.getRepo().absolutePath()))) {
            path.forEach(absolutePath -> {
                if (Files.isRegularFile(absolutePath)) {
                    Result<Boolean, WriteError> add = repo.add(absolutePath);
                    if (add.isSuccess() && add.value() == Boolean.TRUE)
                        added.incrementAndGet();
                    else {
                        if (cliParameter.isVerbose())
                            System.out.println("Not added " + absolutePath);
                    }
                } else {
                    if (cliParameter.isVerbose())
                        System.out.println("Skipping " + absolutePath);
                }
            });
        } catch (IOException e) {
            return Result.err(UpdateRepoError.ioException(repo.getRepoDir(), e));
        }
        return Result.ok(added.get());
    }


}
