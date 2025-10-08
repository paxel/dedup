package paxel.dedup.repo.domain;

import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.errors.DeleteRepoError;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

import java.io.IOException;
import java.util.List;

public class RmRepoProcess {
    public int delete(String name, CliParameter cliParameter) {
        Result<DedupConfig, CreateConfigError> configResult = DedupConfigFactory.create();
        if (configResult.hasFailed()) {
            new DedupConfigErrorHandler().dump(configResult.error());
            return -1;
        }

        DedupConfig dedupConfig = configResult.value();


        Result<Boolean, DeleteRepoError> deleteResult = dedupConfig.deleteRepo(name);
        if (deleteResult.hasFailed()) {
            List<Exception> exceptions = deleteResult.error().ioExceptions();
            System.out.println("While deleting " + name + " " + exceptions.size() + " exceptions happened");
            exceptions.getFirst().printStackTrace();
            return -6;
        }

        System.out.println("Deleted " + name);
        return 0;
    }
}