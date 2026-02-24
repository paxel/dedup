package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.util.Comparator;
import java.util.List;


@RequiredArgsConstructor
@Slf4j
public class ListReposProcess {

    private final CliParameter cliParameter;
    private final DedupConfig dedupConfig;

    public Result<Integer, DedupError> list() {

        Result<List<Repo>, DedupError> getReposResult = dedupConfig.getRepos();
        if (!getReposResult.isSuccess()) {
            return Result.err(getReposResult.error());
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
        return Result.ok(0);
    }

    private String firstNonBlankLocal(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
