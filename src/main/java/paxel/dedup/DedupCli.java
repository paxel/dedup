package paxel.dedup;

import paxel.dedup.model.errors.CreateConfigError;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.parameter.CliParameter;
import paxel.dedup.parameter.DiffCommand;
import paxel.dedup.parameter.FilesCommand;
import paxel.dedup.parameter.RepoCommand;
import paxel.lib.Result;
import picocli.CommandLine;


public class DedupCli {
    public static void main(String[] args) {

        Result<DedupConfig, CreateConfigError> result = DedupConfigFactory.create();

        CommandLine commandLine = new CommandLine(new CliParameter())
                .addSubcommand(new DiffCommand())
                .addSubcommand(new FilesCommand())
                .addSubcommand(new RepoCommand());


        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }


}
