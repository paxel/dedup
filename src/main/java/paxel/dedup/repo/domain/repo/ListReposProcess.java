package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.OpenRepoError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class ListReposProcess {

    private final CliParameter cliParameter;
    private final DedupConfig dedupConfig;

    public int list() {

        Result<List<Repo>, OpenRepoError> getReposResult = dedupConfig.getRepos();
        if (!getReposResult.isSuccess()) {
            IOException ioException = getReposResult.error().ioException();
            if (ioException != null) {
                log.error("{} Invalid", getReposResult.error().path(), ioException);
            }
            return -20;
        }
        getReposResult.value().stream()
                .sorted(Comparator.comparing(Repo::name, String::compareTo))
                .map(repo -> {
                    if (cliParameter.isVerbose()) {
                        return repo.name() + ": " + repo.absolutePath() + " index files: " + repo.indices();
                    } else {
                        return repo.name() + ": " + repo.absolutePath();
                    }
                })
                .forEach(line -> log.info("{}", line));
        return 0;
    }
}
