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
        CliParameter cliParameter = new CliParameter();

        CommandLine commandLine = new CommandLine(cliParameter)
                .addSubcommand(new DiffCommand(infrastructureConfig))
                .addSubcommand(new FilesCommand(infrastructureConfig))
                .addSubcommand(new RepoCommand(infrastructureConfig));

        CommandLine.ParseResult parseResult = commandLine.parseArgs(args);
        if (parseResult.isUsageHelpRequested()) {
            commandLine.usage(System.out);
            System.exit(0);
        } else if (parseResult.isVersionHelpRequested()) {
            commandLine.printVersionHelp(System.out);
            System.exit(0);
        }

        paxel.dedup.infrastructure.logging.ConsoleLogger.getInstance().setVerbose(cliParameter.isVerbose());

        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }


}
