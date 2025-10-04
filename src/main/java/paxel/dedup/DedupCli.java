package paxel.dedup;


import com.beust.jcommander.ParameterException;
import paxel.dedup.parameter.CliParameter;
import paxel.lib.Result;

public class DedupCli {
    public static void main(String[] args) {
        Result<CliParameter, ParameterException> parse = CliParameter.parse(args);
        if (parse.hasFailed() || parse.value() == null) return;

        DedupCli dedupCli = new DedupCli(parse.value());
    }


    private final CliParameter cliParameter;

    public DedupCli(CliParameter cliParameter) {
        this.cliParameter = cliParameter;
    }

}
