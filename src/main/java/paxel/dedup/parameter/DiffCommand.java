package paxel.dedup.parameter;

import com.fasterxml.jackson.databind.ObjectMapper;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.model.errors.DedupConfigErrorHandler;
import paxel.dedup.repo.domain.diff.DiffProcess;
import paxel.lib.Result;
import picocli.CommandLine;
import picocli.CommandLine.*;

@Command(name = "diff", description = "Checks diffs to and/or from repos")
public class DiffCommand {

    @CommandLine.ParentCommand
    CliParameter cliParameter;
    private DedupConfig dedupConfig;

    @Command(name = "print", description = "prints differences between source and reference")
    public int print(
            @Parameters(description = "Source repo or dir") String source,
            @Parameters(description = "Reference repo or dir") String reference,
            @Option(names = {"-f", "--filter"}) String filter) {
        int i = initDefaultConfig();
        if (i != 0)
            return i;

        return new DiffProcess(cliParameter, source, reference, dedupConfig, filter, new ObjectMapper()).print();
    }

    @Command(name = "cp", description = "copies files in source and not in reference to a target")
    public int copy(
            @Parameters(description = "Source repo or dir") String source,
            @Parameters(description = "Reference repo or dir") String reference,
            @Parameters(description = "Target repo or dir") String target,
            @Option(names = {"-f", "--filter"}) String filter) {
        int i = initDefaultConfig();
        if (i != 0)
            return i;

        return new DiffProcess(cliParameter, source, reference, dedupConfig, filter, new ObjectMapper()).copy(target, false);
    }


    @Command(name = "mv", description = "Moves files in source and not in reference to a target")
    public int move(
            @Parameters(description = "Source repo or dir") String source,
            @Parameters(description = "Reference repo or dir") String reference,
            @Parameters(description = "Target repo or dir") String target,
            @Option(names = {"-f", "--filter"}) String filter) {
        int i = initDefaultConfig();
        if (i != 0)
            return i;

        return new DiffProcess(cliParameter, source, reference, dedupConfig, filter, new ObjectMapper()).copy(target, true);
    }

    @Command(name = "rm", description = "Delete files in source that are already in reference")
    public int move(
            @Parameters(description = "Source repo or dir") String source,
            @Parameters(description = "Reference repo or dir") String reference,
            @Option(names = {"-f", "--filter"}) String filter) {
        int i = initDefaultConfig();
        if (i != 0) return i;

        return new DiffProcess(cliParameter, source, reference, dedupConfig, filter, new ObjectMapper()).delete();
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
