package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.model.errors.DeleteRepoError;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

import java.util.List;

@RequiredArgsConstructor
public class RmReposProcess {

    private final CliParameter cliParameter;
    private final String name;
    private final DedupConfig dedupConfig;

    public int delete() {
        if (cliParameter.isVerbose()) {
            System.out.println("Deleting " + name + " from " + dedupConfig.getRepoDir());
        }

        Result<Boolean, DeleteRepoError> deleteResult = dedupConfig.deleteRepo(name);
        if (deleteResult.hasFailed()) {
            List<Exception> exceptions = deleteResult.error().ioExceptions();
            System.err.println("While deleting " + name + " " + exceptions.size() + " exceptions happened");
            exceptions.forEach(Throwable::printStackTrace);
            return -40;
        }

        if (cliParameter.isVerbose()) {
            System.out.println("Deleted " + name);
        }
        return 0;
    }
}