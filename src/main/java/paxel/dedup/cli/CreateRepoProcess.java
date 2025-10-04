package paxel.dedup.cli;

import paxel.dedup.config.*;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Paths;

public class CreateRepoProcess {
    public int create(String name, String path, int indices) {
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
        Result<Repo, CreateRepoError> createResult = dedupConfig.createRepo(name, Paths.get(path), indices);
        if (createResult.hasFailed()) {
            IOException ioException = createResult.error().ioException();
            if (ioException != null) {
                System.err.println(createResult.error().path() + " not a valid repo path");
                ioException.printStackTrace();
               return -2;
            }
        }

        System.out.println("Created " + createResult.value());
        return 0;

    }
}
