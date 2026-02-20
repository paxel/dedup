package paxel.dedup.application.cli.parameter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.config.DedupConfigFactory;
import paxel.dedup.domain.model.errors.CreateConfigError;
import paxel.dedup.domain.model.errors.DedupConfigErrorHandler;
import paxel.dedup.infrastructure.config.InfrastructureConfig;
import paxel.dedup.repo.domain.repo.*;
import paxel.lib.Result;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "repo", description = "manipulates repos")
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public class RepoCommand {

    @CommandLine.ParentCommand
    CliParameter cliParameter;
    private final InfrastructureConfig infrastructureConfig;
    private DedupConfig dedupConfig;

    @Command(name = "create", description = "Creates a non existing repo")
    public int create(
            @Parameters(description = "Name of the repo") String name,
            @Parameters(description = "Path of the repo") String path,
            @Option(defaultValue = "10", names = {"--indices", "-i"}, description = "Number of index files") int indices) {
        initDefaultConfig();

        return new CreateRepoProcess(cliParameter, name, path, indices, dedupConfig).create();
    }

    @Command(name = "rm", description = "Deletes existing repo")
    public int delete(
            @Parameters(description = "Name of the repo") String name) {
        initDefaultConfig();

        return new RmReposProcess(cliParameter, name, dedupConfig).delete();
    }


    @Command(name = "ls", description = "List all repos")
    public int list() {
        initDefaultConfig();

        return new ListReposProcess(cliParameter, dedupConfig).list();
    }

    @Command(name = "update", description = "Reads all file changes from the path into the Repos DB")
    public int update(
            @Option(names = {"-R"}, description = "Repos") List<String> names,
            @Option(names = {"-t", "--threads"}, description = "Number of threads used for hashing", defaultValue = "2") int threads,
            @Option(names = {"-a", "--all"}, description = "All repos") boolean all,
            @Option(names = {"--no-progress"}, description = "Don't show progress page") boolean noProgress) {
        initDefaultConfig();

        return (new UpdateReposProcess(cliParameter, names, all, threads, dedupConfig, infrastructureConfig.getObjectMapper(),
                !noProgress).update());
    }

    @Command(name = "prune", description = "Prunes the DB removing all old versions and deleted files")
    public int prune(
            @Option(names = {"-R"}, description = "Repos") List<String> names,
            @Option(names = {"-a", "--all"}, description = "All repos") boolean all,
            @Option(defaultValue = "10", names = {"--indices", "-i"}, description = "Number of index files") int indices) {
        initDefaultConfig();

        return (new PruneReposProcess(cliParameter, names, all, indices, dedupConfig, infrastructureConfig.getObjectMapper()).prune());
    }

    @Command(name = "cp", description = "Copies the Repo into a new one with a new path, keeping all the entries from the original. The original is unmodified")
    public int clone(
            @Parameters(description = "Source Repo") String sourceRepo,
            @Parameters(description = "Destination Repo") String destinationRepo,
            @Parameters(description = "Path of the new repo") String path) {
        initDefaultConfig();

        return (new CopyRepoProcess(cliParameter, sourceRepo, destinationRepo, path, dedupConfig).copy());
    }

    @Command(name = "rel", description = "Relocates the path of a Repo. The entries remain unchanged.")
    public int clone(
            @Parameters(description = "Source Repo") String repo,
            @Parameters(description = "The relocated path") String path) {
        initDefaultConfig();

        return (new RelocateRepoProcess(cliParameter, repo, path, dedupConfig).move());
    }

    @Command(name = "mv", description = "Moves the repos to a new Repo. The entries remain unchanged.")
    public int move(
            @Parameters(description = "Source Repo") String sourceRepo,
            @Parameters(description = "Target Repo") String destinationRepo) {
        initDefaultConfig();

        return (new MoveRepoProcess(cliParameter, sourceRepo, destinationRepo, dedupConfig).move());
    }

    @Command(name = "dupes", description = "Manage duplicates in one or more repos.")
    public int move(
            @Option(names = {"-R"}, description = "Repos") List<String> names,
            @Option(names = {"-a", "--all"}, description = "All repos") boolean all) {
        initDefaultConfig();

        return (new DuplicateRepoProcess(cliParameter, names, all, dedupConfig).dupes());
    }


    private void initDefaultConfig() {
        dedupConfig = infrastructureConfig.getDedupConfig();
    }

}
