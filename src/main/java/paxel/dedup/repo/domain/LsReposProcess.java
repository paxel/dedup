package paxel.dedup.repo.domain;

import paxel.dedup.config.*;
import paxel.dedup.model.Repo;
import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.model.errors.OpenRepoError;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class LsReposProcess {
    public int list(CliParameter cliParameter) {
        // TODO: use configured config relativePath
        Result<DedupConfig, CreateConfigError> configResult = DedupConfigFactory.create();
        if (configResult.hasFailed()) {
            new DedupConfigErrorHandler().dump(configResult.error());
            return -1;
        }

        DedupConfig dedupConfig = configResult.value();
        Result<List<Repo>, OpenRepoError> getReposResult = dedupConfig.getRepos();
        if (!getReposResult.isSuccess()) {
            IOException ioException = getReposResult.error().ioException();
            if (ioException != null) {
                System.err.println(getReposResult.error().path() + " Invalid");
                ioException.printStackTrace();
            }
            return -4;
        }
        getReposResult.value().stream()
                .sorted(Comparator.comparing(Repo::name, String::compareTo))
                .map(repo -> repo.name() + ": " + repo.absolutePath())
                .forEach(System.out::println);
        return 0;
    }
}
