package paxel.dedup.parameter;

import paxel.dedup.RepoCreation;
import paxel.dedup.RepoDeletion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "repo", helpCommand = true, subcommands = {CommandLine.HelpCommand.class})
public class RepoCommand {

    @Command(name = "create", helpCommand = true)
    public int create(
            @Parameters(index = "0", description = "Name of the repo") String name,
            @Parameters(index = "1", description = "Path of the repo") String path,
            @Option(defaultValue = "10", names = {"--indices", "-i"}, description = "Number of index files") int indices) {

        return new RepoCreation().create(name, path, indices);
    }

    @Command(name = "delete", helpCommand = true)
    public int delete(
            @Parameters(index = "0", description = "Name of the repo") String name) {
        return new RepoDeletion().delete(name);
    }


}
