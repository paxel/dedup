package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

@RequiredArgsConstructor
@Slf4j
public class RmReposProcess {
    private final CliParameter cliParameter;
    private final String name;
    private final DedupConfig dedupConfig;

    public Result<Integer, DedupError> delete() {
        if (cliParameter.isVerbose()) {
            log.info("Deleting {} from {}", name, dedupConfig.getRepoDir());
        }

        Result<Boolean, DedupError> deleteResult = dedupConfig.deleteRepo(name);
        if (deleteResult.hasFailed()) {
            return Result.err(deleteResult.error());
        }

        if (cliParameter.isVerbose()) {
            log.info("Deleted {}", name);
        }
        return Result.ok(0);
    }
}