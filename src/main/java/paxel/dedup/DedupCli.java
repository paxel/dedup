package paxel.dedup;


import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.parameter.CliParameter;
import paxel.dedup.parameter.RepoCommand;
import paxel.lib.Result;
import picocli.CommandLine;


public class DedupCli {
    public static void main(String[] args) {

        Result<DedupConfig, CreateConfigError> result = DedupConfigFactory.create();

        CliParameter command = new CliParameter();
        CommandLine commandLine = new CommandLine(command)
                .addSubcommand(new RepoCommand(command));


        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }


}
