package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.logging.ConsoleLogger;
import paxel.lib.Result;

@RequiredArgsConstructor
public class MoveRepoProcess {
    private static final ConsoleLogger log = ConsoleLogger.getInstance();
    private final CliParameter cliParameter;
    private final String sourceRepo;
    private final String destinationRepo;
    private final DedupConfig dedupConfig;

    public int move() {

        if (cliParameter.isVerbose()) {
            log.info("Renaming repo {} to {}", sourceRepo, destinationRepo);
        }
        Result<Boolean, DedupError> result = dedupConfig.renameRepo(sourceRepo, destinationRepo);
        if (result.hasFailed()) {
            log.error("Renaming repo {} to {} has failed: {}", sourceRepo, destinationRepo, result.error());
            return -90;
        }
        if (cliParameter.isVerbose()) {
            log.info("Renamed repo {} to {}", sourceRepo, destinationRepo);
        }

        return 0;
    }
}
