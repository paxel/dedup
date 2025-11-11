package paxel.dedup.parameter;

import com.fasterxml.jackson.databind.ObjectMapper;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.model.errors.DedupConfigErrorHandler;
import paxel.dedup.repo.domain.diff.CreateDiffProcess;
import paxel.lib.Result;
import picocli.CommandLine;
import picocli.CommandLine.*;

@Command(name = "diff", description = "Checks diffs to and/or from repos")
public class DiffCommand {

    @CommandLine.ParentCommand
    CliParameter cliParameter;
    private DedupConfig dedupConfig;

    @Command(name = "print", description = "prints differences between source and target")
    public int create(
            @Parameters(description = "Source repo or dir") String source,
            @Parameters(description = "Target repo or dir") String target,
            @Option(names = {"-f", "--filter"}) String filter) {
        int i = initDefaultConfig();
        if (i != 0)
            return i;

        return new CreateDiffProcess(cliParameter, source, target, dedupConfig,filter, new ObjectMapper()).print();
    }


    private int initDefaultConfig() {
        Result<DedupConfig, CreateConfigError> configResult = DedupConfigFactory.create();
        if (configResult.hasFailed()) {
            new DedupConfigErrorHandler().dump(configResult.error());
            return -1;
        }
        dedupConfig = configResult.value();

        return 0;
    }

}
