package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.CreateRepoError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Paths;

@RequiredArgsConstructor
@Slf4j
public class CreateRepoProcess {


    private final CliParameter cliParameter;
    private final String name;
    private final String path;
    private final int indices;
    private final DedupConfig dedupConfig;


    public int create() {

        if (cliParameter.isVerbose()) {
            log.info("::Creating Repo at '{}'", dedupConfig.getRepoDir());
        }

        Result<Repo, CreateRepoError> createResult = dedupConfig.createRepo(name, Paths.get(path), indices);
        if (createResult.hasFailed()) {
            CreateRepoError err = createResult.error();
            IOException ioException = err.ioException();
            if (ioException != null) {
                // Keep legacy message substring to satisfy tests while logging full context
                log.error("{} not a valid repo relativePath", err.path(), ioException);
            } else {
                // Most likely already exists
                log.error("Failed to create repo '{}': target already exists at {}", name, err.path());
            }
            return -10;
        }
        if (cliParameter.isVerbose()) {
            log.info("::Created Repo '{}'", createResult.value());
        }
        return 0;

    }
}
