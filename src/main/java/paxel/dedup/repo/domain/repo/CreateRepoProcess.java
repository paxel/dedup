package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.nio.file.Paths;


@RequiredArgsConstructor
@Slf4j
public class CreateRepoProcess {


    private final CliParameter cliParameter;
    private final String name;
    private final String path;
    private final int indices;
    private final DedupConfig dedupConfig;


    public Result<Integer, DedupError> create() {

        if (cliParameter.isVerbose()) {
            log.info("::Creating Repo at '{}'", dedupConfig.getRepoDir());
        }

        Result<Repo, DedupError> createResult = dedupConfig.createRepo(name, Paths.get(path), indices);
        if (createResult.hasFailed()) {
            return Result.err(createResult.error());
        }
        if (cliParameter.isVerbose()) {
            log.info("::Created Repo '{}'", createResult.value());
        }
        return Result.ok(0);

    }

    private String firstNonBlankLocal(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
