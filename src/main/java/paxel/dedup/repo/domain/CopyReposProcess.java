package paxel.dedup.repo.domain;

import lombok.RequiredArgsConstructor;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.parameter.CliParameter;

@RequiredArgsConstructor
public class CopyReposProcess {
    private final CliParameter cliParameter;
    private final String sourceRepo;
    private final String destinationRepo;
    private final String path;
    private final DedupConfig dedupConfig;

    public int copy() {
        return 0;
    }
}
