package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.errors.DeleteRepoError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class RmReposProcess {

    private final CliParameter cliParameter;
    private final String name;
    private final DedupConfig dedupConfig;

    public int delete() {
        if (cliParameter.isVerbose()) {
            log.info("Deleting {} from {}", name, dedupConfig.getRepoDir());
        }

        Result<Boolean, DeleteRepoError> deleteResult = dedupConfig.deleteRepo(name);
        if (deleteResult.hasFailed()) {
            List<Exception> exceptions = deleteResult.error().ioExceptions();
            log.error("While deleting {} {} exceptions happened", name, exceptions.size());
            exceptions.forEach(ex -> log.error("Delete {} failed due to:", name, ex));
            return -40;
        }

        if (cliParameter.isVerbose()) {
            log.info("Deleted {}", name);
        }
        return 0;
    }
}