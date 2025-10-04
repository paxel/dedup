package paxel.dedup.parameter;

import paxel.dedup.RepoCreation;
import paxel.dedup.RepoDeletion;
import paxel.dedup.config.CreateConfigError;
import paxel.dedup.config.DedupConfig;
import paxel.lib.Result;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "repo", helpCommand = true)
public class RepoCommand {


    public RepoCommand(Result<DedupConfig, CreateConfigError> result) {
        this.result = result;
    }

    @Command(name = "create")
    public void create(
            @CommandLine.Parameters(index = "0", description = "Name of the repo") String name,
            @CommandLine.Parameters(index = "1", description = "Path of the repo") String path,
            @CommandLine.Option(defaultValue = "10", names = {"--indices", "-i"}, description = "Number of index files") int indices) {

        new RepoCreation().create(name, path, indices);
    }

    @Command(name = "delete")
    public void delete(
            @CommandLine.Parameters(index = "0", description = "Name of the repo") String name) {
        new RepoDeletion().delete(name);
    }


}
