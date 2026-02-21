package paxel.dedup.application.cli.parameter;

import lombok.Data;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Data
@Command(name = "dedup", description = "Dedup CLI", mixinStandardHelpOptions = true)
public class CliParameter {

    @Option(names = "-v", description = "Verbose logging", scope = CommandLine.ScopeType.INHERIT)
    private boolean verbose;


}

