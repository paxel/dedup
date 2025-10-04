package paxel.dedup;

import paxel.dedup.config.CreateConfigError;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.config.DeleteRepoError;
import paxel.lib.Result;

import java.io.IOException;
import java.util.List;

public class RepoDeletion {
    public int delete(String name) {
        Result<DedupConfig, CreateConfigError> configCreate = DedupConfigFactory.create();

        if (configCreate.hasFailed()) {
            IOException ioException = configCreate.error().ioException();
            if (ioException != null) {
                System.err.println(configCreate.error().path() + " not a valid config path");
                ioException.printStackTrace();
                System.exit(-1);
            }
        }

        DedupConfig value = configCreate.value();


        Result<Boolean, DeleteRepoError> deleteResult = value.deleteRepo(name);
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