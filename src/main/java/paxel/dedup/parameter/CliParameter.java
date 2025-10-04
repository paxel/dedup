package paxel.dedup.parameter;

import lombok.Data;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Data
@CommandLine.Command(subcommands = RepoCommand.class)
public class CliParameter {

    @Option(names = "-v", description = "Verbose logging")
    private boolean verbose;


}

