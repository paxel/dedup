package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.ModifyRepoError;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.lib.Result;

import java.nio.file.Paths;

@RequiredArgsConstructor
public class RelocateRepoProcess {
    private final CliParameter cliParameter;
    private final String repo;
    private final String path;
    private final DedupConfig dedupConfig;


    public int move() {

        if (cliParameter.isVerbose()) {
            System.out.printf("Relocating %s path to %s%n", repo, path);
        }

        Result<Repo, ModifyRepoError> repoModifyRepoErrorResult = dedupConfig.changePath(repo, Paths.get(path));
        if (repoModifyRepoErrorResult.hasFailed()) {
            System.err.printf("Relocating %s to %s failed: %s%n", repo, path, repoModifyRepoErrorResult.error());
            return -70;
        }
        if (cliParameter.isVerbose()) {
            System.out.printf("Relocated %s path to %s%n", repo, path);
        }
        return 0;
    }
}
