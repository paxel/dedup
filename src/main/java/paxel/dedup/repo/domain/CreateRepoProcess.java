package paxel.dedup.repo.domain;

import paxel.dedup.config.*;
import paxel.dedup.model.Repo;
import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.model.errors.CreateRepoError;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Paths;

public class CreateRepoProcess {
    public int create(String name, String path, int indices) {
        // TODO: use configured config relativePath
        Result<DedupConfig, CreateConfigError> configResult = DedupConfigFactory.create();

        if (configResult.hasFailed()) {
            IOException ioException = configResult.error().ioException();
            if (ioException != null) {
                System.err.println(configResult.error().path() + " not a valid config relativePath");
                ioException.printStackTrace();
            }
            return -1;
        }
        DedupConfig dedupConfig = configResult.value();
        Result<Repo, CreateRepoError> createResult = dedupConfig.createRepo(name, Paths.get(path), indices);
        if (createResult.hasFailed()) {
            IOException ioException = createResult.error().ioException();
            if (ioException != null) {
                System.err.println(createResult.error().path() + " not a valid repo relativePath");
                ioException.printStackTrace();
            }
            return -2;
        }

        System.out.println("Created " + createResult.value());
        return 0;

    }
}
