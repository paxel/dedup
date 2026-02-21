package paxel.dedup.application.cli.parameter;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.config.InfrastructureConfig;
import paxel.dedup.repo.domain.diff.DiffProcess;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "diff", description = "Checks diffs to and/or from repos")
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public class DiffCommand {

    @CommandLine.ParentCommand
    CliParameter cliParameter;
    private final InfrastructureConfig infrastructureConfig;
    private DedupConfig dedupConfig;

    @Command(name = "print", description = "prints differences between source and reference")
    public int print(
            @Parameters(description = "Source repo or dir") String source,
            @Parameters(description = "Reference repo or dir") String reference,
            @Option(names = {"-f", "--filter"}) String filter) {
        initDefaultConfig();

        return new DiffProcess(cliParameter, source, reference, dedupConfig, filter, infrastructureConfig.getFileSystem()).print();
    }

    @Command(name = "cp", description = "copies files in source and not in reference to a target")
    public int copy(
            @Parameters(description = "Source repo or dir") String source,
            @Parameters(description = "Reference repo or dir") String reference,
            @Parameters(description = "Target repo or dir") String target,
            @Option(names = {"-f", "--filter"}) String filter) {
        initDefaultConfig();

        return new DiffProcess(cliParameter, source, reference, dedupConfig, filter, infrastructureConfig.getFileSystem()).copy(target, false);
    }


    @Command(name = "mv", description = "Moves files in source and not in reference to a target")
    public int move(
            @Parameters(description = "Source repo or dir") String source,
            @Parameters(description = "Reference repo or dir") String reference,
            @Parameters(description = "Target repo or dir") String target,
            @Option(names = {"-f", "--filter"}) String filter) {
        initDefaultConfig();

        return new DiffProcess(cliParameter, source, reference, dedupConfig, filter, infrastructureConfig.getFileSystem()).copy(target, true);
    }

    @Command(name = "rm", description = "Delete files in source that are already in reference")
    public int move(
            @Parameters(description = "Source repo or dir") String source,
            @Parameters(description = "Reference repo or dir") String reference,
            @Option(names = {"-f", "--filter"}) String filter) {
        initDefaultConfig();

        return new DiffProcess(cliParameter, source, reference, dedupConfig, filter, infrastructureConfig.getFileSystem()).delete();
    }


    @Command(name = "sync", description = "Syncs target repo with source: copy new contents, optionally delete missing")
    public int sync(
            @Parameters(description = "Source repo or dir (A)") String source,
            @Parameters(description = "Target repo or dir (B)") String target,
            @Option(names = {"--copyNew"}, description = "Copy contents that exist in A but not in B (default: ${DEFAULT-VALUE})", defaultValue = "true") boolean copyNew,
            @Option(names = {"--deleteMissing"}, description = "Delete in B contents that A marks as missing (default: ${DEFAULT-VALUE})", defaultValue = "false") boolean deleteMissing,
            @Option(names = {"--mirror"}, description = "Equivalent to --copyNew --deleteMissing (overrides the two options)") boolean mirror,
            @Option(names = {"-f", "--filter"}) String filter) {
        initDefaultConfig();

        if (mirror) {
            copyNew = true;
            deleteMissing = true;
        }

        return new DiffProcess(cliParameter, source, target, dedupConfig, filter, infrastructureConfig.getFileSystem())
                .sync(copyNew, deleteMissing);
    }


    private void initDefaultConfig() {
        dedupConfig = infrastructureConfig.getDedupConfig();
    }

}
