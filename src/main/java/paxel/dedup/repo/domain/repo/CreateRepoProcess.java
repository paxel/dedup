package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.logging.ConsoleLogger;
import paxel.lib.Result;

import java.nio.file.Paths;


@RequiredArgsConstructor
public class CreateRepoProcess {
    private static final ConsoleLogger log = ConsoleLogger.getInstance();


    private final CliParameter cliParameter;
    private final String name;
    private final String path;
    private final int indices;
    private final DedupConfig dedupConfig;


    public int create() {

        if (cliParameter.isVerbose()) {
            log.info("::Creating Repo at '{}'", dedupConfig.getRepoDir());
        }

        Result<Repo, DedupError> createResult = dedupConfig.createRepo(name, Paths.get(path), indices);
        if (createResult.hasFailed()) {
            DedupError err = createResult.error();
            // Keep legacy message substring to satisfy tests while logging full context
            String msg = firstNonBlankLocal(err.description(), "Failed to create repo '" + name + "'");
            if (err.exception() != null) log.error("{}", msg, err.exception());
            else log.error("{}", msg);
            return -10;
        }
        if (cliParameter.isVerbose()) {
            log.info("::Created Repo '{}'", createResult.value());
        }
        return 0;

    }

    private String firstNonBlankLocal(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
