package paxel.dedup;

import paxel.dedup.config.*;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Paths;

public class RepoCreation {
    public int create(String name, String path, int indices) {
        // TODO: use configured config path
        Result<DedupConfig, CreateConfigError> result = DedupConfigFactory.create();

        if (result.hasFailed()) {
            IOException ioException = result.error().ioException();
            if (ioException != null) {
                System.err.println(result.error().path() + " not a valid config path");
                ioException.printStackTrace();
                System.exit(-1);
            }
        }
        DedupConfig value = result.value();
        Result<Repo, CreateRepoError> repo = value.createRepo(name, Paths.get(path), indices);
        if (result.hasFailed()) {
            IOException ioException = result.error().ioException();
            if (ioException != null) {
                System.err.println(result.error().path() + " not a valid config path");
                ioException.printStackTrace();
                System.exit(-2);
            }
        }

        System.out.println("Created " + repo.value());
        return 0;

    }
}
