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
public class RelocateRepoProcess {
    private static final ConsoleLogger log = ConsoleLogger.getInstance();
    private final CliParameter cliParameter;
    private final String repo;
    private final String path;
    private final DedupConfig dedupConfig;


    public int move() {

        if (cliParameter.isVerbose()) {
            log.info("Relocating {} path to {}", repo, path);
        }

        Result<Repo, DedupError> repoModifyRepoErrorResult = dedupConfig.changePath(repo, Paths.get(path));
        if (repoModifyRepoErrorResult.hasFailed()) {
            log.error("Relocating {} to {} failed: {}", repo, path, repoModifyRepoErrorResult.error());
            return -70;
        }
        if (cliParameter.isVerbose()) {
            log.info("Relocated {} path to {}", repo, path);
        }
        return 0;
    }
}
