package paxel.dedup.application.cli.parameter;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.config.InfrastructureConfig;
import paxel.dedup.repo.domain.files.FilesProcess;
import picocli.CommandLine;

@CommandLine.Command(name = "files", description = "Manages files in a repo", mixinStandardHelpOptions = true)
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public class FilesCommand {

    @CommandLine.ParentCommand
    CliParameter cliParameter;
    private final InfrastructureConfig infrastructureConfig;
    private DedupConfig dedupConfig;


    @CommandLine.Command(name = "ls", description = "prints files in a repo", mixinStandardHelpOptions = true)
    public int ls(
            @CommandLine.Parameters(description = "Repo") String repo,
            @CommandLine.Option(names = {"-f", "--filter"}) String filter) {
        initDefaultConfig();

        return new FilesProcess(cliParameter, repo, dedupConfig, filter, infrastructureConfig.getFileSystem()).ls();
    }


    @CommandLine.Command(name = "rm", description = "deletes files in a repo", mixinStandardHelpOptions = true)
    public int rm(
            @CommandLine.Parameters(description = "Repo") String repo,
            @CommandLine.Option(names = {"-f", "--filter"}) String filter) {
        initDefaultConfig();

        return new FilesProcess(cliParameter, repo, dedupConfig, filter, infrastructureConfig.getFileSystem()).rm();
    }

    @CommandLine.Command(name = "cp", description = "copies files in source and not in reference to a target", mixinStandardHelpOptions = true)
    public int copy(
            @CommandLine.Parameters(description = "Source repo or dir") String source,
            @CommandLine.Parameters(description = "Target repo or dir") String target,
            @CommandLine.Option(names = {"--appendix"}) String appendix,
            @CommandLine.Option(names = {"-f", "--filter"}) String filter) {
        initDefaultConfig();

        return new FilesProcess(cliParameter, source, dedupConfig, filter, infrastructureConfig.getFileSystem()).copy(target, false, appendix);
    }


    @CommandLine.Command(name = "mv", description = "Moves files in source and not in reference to a target", mixinStandardHelpOptions = true)
    public int move(
            @CommandLine.Parameters(description = "Source repo or dir") String source,
            @CommandLine.Parameters(description = "Target repo or dir") String target,
            @CommandLine.Option(names = {"--appendix"}) String appendix,
            @CommandLine.Option(names = {"-f", "--filter"}) String filter) {
        initDefaultConfig();

        return new FilesProcess(cliParameter, source, dedupConfig, filter, infrastructureConfig.getFileSystem()).copy(target, true, appendix);
    }

    @CommandLine.Command(name = "types", description = "Lists the mime types in a repo", mixinStandardHelpOptions = true)
    public int types(
            @CommandLine.Parameters(description = "Repo") String repo
    ) {
        initDefaultConfig();

        return new FilesProcess(cliParameter, repo, dedupConfig, null, infrastructureConfig.getFileSystem()).types();
    }


    private void initDefaultConfig() {
        dedupConfig = infrastructureConfig.getDedupConfig();
    }
}
