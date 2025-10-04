package paxel.dedup;


import com.beust.jcommander.ParameterException;
import paxel.dedup.config.CreateConfigError;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.config.DedupConfigFactory;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

public class DedupCli {
    public static void main(String[] args) {
        Result<CliParameter, ParameterException> parse = CliParameter.parse(args);
        if (parse.hasFailed() || parse.value() == null)
            return;

        CliParameter parameter = parse.value();

        Result<DedupConfig, CreateConfigError> result = DedupConfigFactory.create();
        if (result.hasFailed()) {
            CreateConfigError error = result.error();
            System.err.println("Can't create config dir " + error.path() + " " + error.ioException());
            return;
        }

        DedupCli dedupCli = new DedupCli(parameter, result.value());
        switch (parameter.getCommand()) {
            case null -> System.err.println("No command is used");
            default -> System.err.println("Unknown command " + parameter.getCommand());
        }
    }


    private final CliParameter cliParameter;
    private final DedupConfig dedupConfig;

    public DedupCli(CliParameter cliParameter, DedupConfig dedupConfig) {
        this.cliParameter = cliParameter;
        this.dedupConfig = dedupConfig;
    }

}
