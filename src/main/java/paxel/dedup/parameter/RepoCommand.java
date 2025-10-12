package paxel.dedup.parameter;

import paxel.dedup.repo.domain.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "repo", description = "manipulates repos")
public class RepoCommand {

    @CommandLine.ParentCommand
    CliParameter cliParameter;

    @Command(name = "create", description = "Creates a non existing repo")
    public int create(
            @Parameters(description = "Name of the repo") String name,
            @Parameters(description = "Path of the repo") String path,
            @Option(defaultValue = "10", names = {"--indices", "-i"}, description = "Number of index files") int indices) {

        return new CreateRepoProcess().create(name, path, indices, cliParameter);
    }

    @Command(name = "rm", description = "Deletes existing repo")
    public int delete(
            @Parameters(description = "Name of the repo") String name) {
        return new RmRepoProcess().delete(name, cliParameter);
    }


    @Command(name = "ls", description = "Lists repos")
    public int list() {
        return new LsReposProcess().list(cliParameter);
    }

    @Command(name = "update", description = "Reads all changes into the Repo")
    public int update(
            @Option(names = {"-R"}, description = "Repos") List<String> names,
            @Option(names = {"-t", "--threads"}, description = "number of threads used for hashing", defaultValue = "2") int threads,
            @Option(names = {"-a", "--all"}, description = "All repos") boolean all) {
        return (new UpdateReposProcess().update(names, all, cliParameter, threads));
    }

    @Command(name = "prune", description = "Prunes the DB removing all old versions and deleted files")
    public int prune(
            @Option(names = {"-R"}, description = "Repos") List<String> names,
            @Option(names = {"-a", "--all"}, description = "All repos") boolean all,
            @Option(defaultValue = "10", names = {"--indices", "-i"}, description = "Number of index files") int indices) {
        return (new PruneReposProcess().prune(names, all, cliParameter, indices));
    }

}
