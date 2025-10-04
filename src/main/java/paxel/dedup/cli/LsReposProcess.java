package paxel.dedup.cli;

import paxel.dedup.config.*;
import paxel.lib.Result;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class LsReposProcess {
    public int list() {
        // TODO: use configured config path
        Result<DedupConfig, CreateConfigError> configResult = DedupConfigFactory.create();

        if (configResult.hasFailed()) {
            IOException ioException = configResult.error().ioException();
            if (ioException != null) {
                System.err.println(configResult.error().path() + " not a valid config path");
                ioException.printStackTrace();
                return -1;
            }
        }
        DedupConfig dedupConfig = configResult.value();
        Result<List<Repo>, OpenRepoError> getReposResult = dedupConfig.getRepos();
        if (!getReposResult.isSuccess()) {
            IOException ioException = getReposResult.error().ioException();
            if (ioException != null) {
                System.err.println(getReposResult.error().path() + " Invalid");
                ioException.printStackTrace();
                return -2;
            }
            return -1;
        }
        getReposResult.value().stream()
                .sorted(Comparator.comparing(Repo::name, String::compareTo))
                .map(repo -> repo.name() + ": " + repo.path())
                .forEach(System.out::println);
        return 0;
    }
}
