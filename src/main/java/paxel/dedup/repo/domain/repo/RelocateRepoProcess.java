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
public class RelocateRepoProcess {
    private final CliParameter cliParameter;
    private final String repo;
    private final String path;
    private final DedupConfig dedupConfig;


    public Result<Integer, DedupError> move() {

        if (cliParameter.isVerbose()) {
            log.info("Relocating {} path to {}", repo, path);
        }

        Result<Repo, DedupError> repoModifyRepoErrorResult = dedupConfig.changePath(repo, Paths.get(path));
        if (repoModifyRepoErrorResult.hasFailed()) {
            return Result.err(repoModifyRepoErrorResult.error());
        }
        if (cliParameter.isVerbose()) {
            log.info("Relocated {} path to {}", repo, path);
        }
        return Result.ok(0);
    }
}
