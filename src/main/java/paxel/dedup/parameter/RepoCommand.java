package paxel.dedup.parameter;

import paxel.dedup.repo.domain.CreateRepoProcess;
import paxel.dedup.repo.domain.RmRepoProcess;
import paxel.dedup.repo.domain.LsReposProcess;
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

        return new CreateRepoProcess().create(name, path, indices);
    }

    @Command(name = "rm", description = "Deletes existing repo")
    public int delete(
            @Parameters(index = "0", description = "Name of the repo") String name) {
        return new RmRepoProcess().delete(name);
    }


    @Command(name = "ls", description = "Lists repos")
    public int list() {
        return new LsReposProcess().list();
    }

}
