package paxel.dedup.parameter;

import lombok.Data;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Data
public class CliParameter {

    @Option(names = "-v", description = "Verbose logging", scope = CommandLine.ScopeType.INHERIT)
    private boolean verbose;


}

