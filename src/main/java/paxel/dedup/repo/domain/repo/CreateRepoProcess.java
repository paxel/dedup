package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.CreateRepoError;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Paths;

@RequiredArgsConstructor
public class CreateRepoProcess {

    private final CliParameter cliParameter;
    private final String name;
    private final String path;
    private final int indices;
    private final DedupConfig dedupConfig;


    public int create() {

        if (cliParameter.isVerbose()) {
            System.out.printf("::Creating Repo at '%s'%n", dedupConfig.getRepoDir());
        }

        Result<Repo, CreateRepoError> createResult = dedupConfig.createRepo(name, Paths.get(path), indices);
        if (createResult.hasFailed()) {
            IOException ioException = createResult.error().ioException();
            if (ioException != null) {
                System.err.println(createResult.error().path() + " not a valid repo relativePath");
                ioException.printStackTrace();
            }
            return -10;
        }
        if (cliParameter.isVerbose()) {
            System.out.printf("::Created Repo '%s'%n", createResult.value());
        }
        return 0;

    }
}
