package paxel.dedup.repo.domain;

import lombok.RequiredArgsConstructor;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.model.errors.RenameRepoError;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

@RequiredArgsConstructor
public class MoveRepoProcess {
    private final CliParameter cliParameter;
    private final String sourceRepo;
    private final String destinationRepo;
    private final DedupConfig dedupConfig;

    public int move() {

        if (cliParameter.isVerbose()) {
            System.out.printf("Renaming repo %s to %s%n", sourceRepo, destinationRepo);
        }
        Result<Boolean, RenameRepoError> result = dedupConfig.renameRepo(sourceRepo, destinationRepo);
        if (result.hasFailed()) {
            System.err.printf("Renaming repo %s to %s has failed:%s%n", sourceRepo, destinationRepo, result.error());
            return -90;
        }
        if (cliParameter.isVerbose()) {
            System.out.printf("Renamed repo %s to %s%n", sourceRepo, destinationRepo);
        }

        return 0;
    }
}
