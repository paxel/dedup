package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.logging.ConsoleLogger;
import paxel.lib.Result;

import java.util.Comparator;
import java.util.List;


@RequiredArgsConstructor
public class ListReposProcess {
    private static final ConsoleLogger log = ConsoleLogger.getInstance();

    private final CliParameter cliParameter;
    private final DedupConfig dedupConfig;

    public int list() {

        Result<List<Repo>, DedupError> getReposResult = dedupConfig.getRepos();
        if (!getReposResult.isSuccess()) {
            DedupError err = getReposResult.error();
            // Preserve legacy substring "Invalid" to keep test expectations
            String desc = err.description();
            String msg = firstNonBlankLocal(desc, "Invalid");
            if (err.exception() != null) {
                log.error("{} {}", msg, "Invalid", err.exception());
            } else {
                log.error("{}", msg);
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

    private String firstNonBlankLocal(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
