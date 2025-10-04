package paxel.dedup.cli;

import paxel.dedup.config.CreateConfigError;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.config.DeleteRepoError;
import paxel.lib.Result;

import java.io.IOException;
import java.util.List;

public class RmRepoProcess {
    public int delete(String name) {
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


        Result<Boolean, DeleteRepoError> deleteResult = dedupConfig.deleteRepo(name);
        if (deleteResult.hasFailed()) {
            List<Exception> exceptions = deleteResult.error().ioExceptions();
            System.out.println("While deleting " + name + " " + exceptions.size() + " exceptions happened");
            exceptions.getFirst().printStackTrace();
            return -3;
        }

        System.out.println("Deleted " + name);
        return 0;
    }
}