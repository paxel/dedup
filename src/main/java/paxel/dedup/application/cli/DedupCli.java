package paxel.dedup.application.cli;

import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.application.cli.parameter.DiffCommand;
import paxel.dedup.application.cli.parameter.FilesCommand;
import paxel.dedup.application.cli.parameter.RepoCommand;
import paxel.dedup.infrastructure.config.InfrastructureConfig;
import picocli.CommandLine;


public class DedupCli {
    public static void main(String[] args) {

        InfrastructureConfig infrastructureConfig = new InfrastructureConfig();

        CommandLine commandLine = new CommandLine(new CliParameter())
                .addSubcommand(new DiffCommand(infrastructureConfig))
                .addSubcommand(new FilesCommand(infrastructureConfig))
                .addSubcommand(new RepoCommand(infrastructureConfig));


        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }


}
