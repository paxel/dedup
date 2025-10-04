package paxel.dedup.parameter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import lombok.Data;
import paxel.lib.Result;

import java.util.List;

@Data
@Parameters(parametersValidators = RepoParameterValidation.class)
public class CliParameter {
    public static Result<CliParameter, ParameterException> parse(String[] args) {
        CliParameter cliParameter = new CliParameter();
        JCommander build = JCommander.newBuilder()
                .addObject(cliParameter)
                .build();
        try {
            build.parse(args);
            if (cliParameter.isHelp()) {
                build.usage();
                return Result.ok(null);
            }
        } catch (ParameterException e) {
            e.usage();
            return Result.err(e);
        }
        return Result.ok(cliParameter);
    }

    @Parameter(names = "-v", description = "Verbose logging")
    private boolean verbose;

    @Parameter(names = "-h", description = "print this help")
    private boolean help;

    @Parameter(names = {"--all", "-a"}, description = "Use all repos")
    private boolean all;

    @Parameter(names = "-R", description = "Name of the repos to use")
    private List<String> repos;

    private String command;

    CliParameter() {
    }
}

