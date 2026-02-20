package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.OpenRepoError;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.lib.Result;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
public class ListReposProcess {

    private final CliParameter cliParameter;
    private final DedupConfig dedupConfig;

    public int list() {

        Result<List<Repo>, OpenRepoError> getReposResult = dedupConfig.getRepos();
        if (!getReposResult.isSuccess()) {
            IOException ioException = getReposResult.error().ioException();
            if (ioException != null) {
                System.err.println(getReposResult.error().path() + " Invalid");
                ioException.printStackTrace();
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
                .forEach(System.out::println);
        return 0;
    }
}
