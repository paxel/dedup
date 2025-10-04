package paxel.dedup.parameter;

import paxel.dedup.RepoCreation;
import paxel.dedup.RepoDeletion;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "repo", description = "manipulates repos")
public class RepoCommand {

    @Command(name = "create", description = "Creates a non existing repo")
    public int create(
            @Parameters(index = "0", description = "Name of the repo") String name,
            @Parameters(index = "1", description = "Path of the repo") String path,
            @Option(defaultValue = "10", names = {"--indices", "-i"}, description = "Number of index files") int indices) {

        return new RepoCreation().create(name, path, indices);
    }

    @Command(name = "delete", description = "Deletes existing repo")
    public int delete(
            @Parameters(index = "0", description = "Name of the repo") String name) {
        return new RepoDeletion().delete(name);
    }

}
