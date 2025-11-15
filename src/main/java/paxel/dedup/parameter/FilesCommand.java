package paxel.dedup.parameter;

import com.fasterxml.jackson.databind.ObjectMapper;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.model.errors.DedupConfigErrorHandler;
import paxel.dedup.repo.domain.files.FilesProcess;
import paxel.lib.Result;
import picocli.CommandLine;

@CommandLine.Command(name = "files", description = "Manages files in a repo")
public class FilesCommand {

    @CommandLine.ParentCommand
    CliParameter cliParameter;
    private DedupConfig dedupConfig;


    @CommandLine.Command(name = "ls", description = "prints files in a repo")
    public int ls(
            @CommandLine.Parameters(description = "Repo") String repo,
            @CommandLine.Option(names = {"-f", "--filter"}) String filter) {
        int i = initDefaultConfig();
        if (i != 0)
            return i;

        return new FilesProcess(cliParameter, repo, dedupConfig, filter, new ObjectMapper()).ls();
    }


    @CommandLine.Command(name = "rm", description = "deletes files in a repo")
    public int rm(
            @CommandLine.Parameters(description = "Repo") String repo,
            @CommandLine.Option(names = {"-f", "--filter"}) String filter) {
        int i = initDefaultConfig();
        if (i != 0)
            return i;

        return new FilesProcess(cliParameter, repo, dedupConfig, filter, new ObjectMapper()).rm();
    }

    @CommandLine.Command(name = "cp", description = "copies files in source and not in reference to a target")
    public int copy(
            @CommandLine.Parameters(description = "Source repo or dir") String source,
            @CommandLine.Parameters(description = "Target repo or dir") String target,
            @CommandLine.Option(names = {"-f", "--filter"}) String filter) {
        int i = initDefaultConfig();
        if (i != 0)
            return i;

        return new FilesProcess(cliParameter, source, dedupConfig, filter, new ObjectMapper()).copy(target, false);
    }


    @CommandLine.Command(name = "mv", description = "Moves files in source and not in reference to a target")
    public int move(
            @CommandLine.Parameters(description = "Source repo or dir") String source,
            @CommandLine.Parameters(description = "Target repo or dir") String target,
            @CommandLine.Option(names = {"-f", "--filter"}) String filter) {
        int i = initDefaultConfig();
        if (i != 0)
            return i;

        return new FilesProcess(cliParameter, source,  dedupConfig, filter, new ObjectMapper()).copy(target, true);
    }

    @CommandLine.Command(name = "types", description = "Lists the mime types in a repo")
    public int types(
            @CommandLine.Parameters(description = "Repo") String repo
    ) {
        int i = initDefaultConfig();
        if (i != 0)
            return i;

        return new FilesProcess(cliParameter, repo, dedupConfig, null, new ObjectMapper()).types();
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
