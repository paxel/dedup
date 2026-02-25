package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

@RequiredArgsConstructor
@Slf4j
public class MoveRepoProcess {
    private final CliParameter cliParameter;
    private final String sourceRepo;
    private final String destinationRepo;
    private final DedupConfig dedupConfig;

    public Result<Integer, DedupError> move() {

        if (cliParameter.isVerbose()) {
            log.info("Renaming repo {} to {}", sourceRepo, destinationRepo);
        }
        Result<Boolean, DedupError> result = dedupConfig.renameRepo(sourceRepo, destinationRepo);
        if (result.hasFailed()) {
            return Result.err(result.error());
        }
        if (cliParameter.isVerbose()) {
            log.info("Renamed repo {} to {}", sourceRepo, destinationRepo);
        }

        return Result.ok(0);
    }
}
