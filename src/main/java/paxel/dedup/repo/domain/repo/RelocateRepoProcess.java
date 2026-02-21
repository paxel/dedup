package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.ModifyRepoError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.nio.file.Paths;

@RequiredArgsConstructor
@Slf4j
public class RelocateRepoProcess {
    private final CliParameter cliParameter;
    private final String repo;
    private final String path;
    private final DedupConfig dedupConfig;


    public int move() {

        if (cliParameter.isVerbose()) {
            log.info("Relocating {} path to {}", repo, path);
        }

        Result<Repo, ModifyRepoError> repoModifyRepoErrorResult = dedupConfig.changePath(repo, Paths.get(path));
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
