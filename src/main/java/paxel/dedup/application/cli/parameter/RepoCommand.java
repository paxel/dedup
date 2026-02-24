package paxel.dedup.application.cli.parameter;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupConfigErrorHandler;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.config.InfrastructureConfig;
import paxel.dedup.repo.domain.repo.*;
import paxel.lib.Result;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "repo", description = "manipulates repos", mixinStandardHelpOptions = true)
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Slf4j
public class RepoCommand {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.ParentCommand
    CliParameter cliParameter;
    private final InfrastructureConfig infrastructureConfig;
    private DedupConfig dedupConfig;

    @Command(name = "create", description = "Creates a non existing repo", mixinStandardHelpOptions = true)
    public int create(
            @Parameters(description = "Name of the repo") String name,
            @Parameters(description = "Path of the repo") String path,
            @Option(defaultValue = "10", names = {"--indices", "-i"}, description = "Number of index files") int indices,
            @Option(names = {"--codec"}, description = "Line codec to use: json|messagepack", defaultValue = "messagepack") String codec,
            @Option(names = {"--strict"}, description = "Fail if selected codec is unavailable") boolean strict) {
        initDefaultConfig();

        Result<Integer, DedupError> result = new CreateRepoProcess(cliParameter, name, path, indices, dedupConfig).create();
        if (result.hasFailed()) {
            new DedupConfigErrorHandler().dump(result.error());
            return -10;
        }
        int rc = result.value();
        if (rc == 0) {
            // Persist codec selection in repo YAML via config API
            Repo.Codec target = switch (codec.toLowerCase()) {
                case "json" -> Repo.Codec.JSON;
                case "messagepack" -> Repo.Codec.MESSAGEPACK;
                default -> null;
            };
            if (target == null) {
                log.warn("Unknown codec '{}' . Supported: json, messagepack. Falling back to default (messagepack on write).", codec);
            } else {
                try {
                    dedupConfig.setCodec(name, target);
                } catch (Exception e) {
                    if (strict) {
                        log.error("Failed to persist codec selection: {}", e.getMessage(), e);
                        return -11;
                    }
                    log.debug("Failed to persist codec selection (non-strict mode)", e);
                }
            }
        }
        return rc;
    }

    @Command(name = "rm", description = "Deletes existing repo", mixinStandardHelpOptions = true)
    public int delete(
            @Parameters(description = "Name of the repo") String name) {
        initDefaultConfig();

        Result<Integer, DedupError> result = new RmReposProcess(cliParameter, name, dedupConfig).delete();
        if (result.hasFailed()) {
            new DedupConfigErrorHandler().dump(result.error());
            return -40;
        }
        return result.value();
    }


    @Command(name = "ls", description = "List all repos", mixinStandardHelpOptions = true)
    public int list() {
        initDefaultConfig();

        Result<Integer, DedupError> result = new ListReposProcess(cliParameter, dedupConfig).list();
        if (result.hasFailed()) {
            new DedupConfigErrorHandler().dump(result.error());
            return -20;
        }
        return result.value();
    }

    @Command(name = "update", description = "Reads all file changes from the path into the Repos DB", mixinStandardHelpOptions = true)
    public int update(
            @Parameters(description = "Repos", arity = "0..*") List<String> repos,
            @Option(names = {"-t", "--threads"}, description = "Number of threads used for hashing", defaultValue = "2") int threads,
            @Option(names = {"-a", "--all"}, description = "All repos") boolean all,
            @Option(names = {"--no-progress"}, description = "Don't show progress page") boolean noProgress,
            @Option(names = {"--refresh-fingerprints"}, description = "Refresh fingerprints for files that don't have one") boolean refreshFingerprints) {
        initDefaultConfig();

        if (!all && hasNoRepos(repos)) {
            printUsageError("No repos specified. Provide at least one repo name or use --all.");
            return CommandLine.ExitCode.USAGE;
        }
        List<String> allNames = repos == null ? List.of() : repos;
        Result<Integer, DedupError> result = new UpdateReposProcess(cliParameter, allNames, all, threads, dedupConfig,
                !noProgress, refreshFingerprints, infrastructureConfig.getFileSystem()).update();
        if (result.hasFailed()) {
            new DedupConfigErrorHandler().dump(result.error());
            return -50;
        }
        return result.value();
    }

    @Command(name = "config", description = "Configures a repo", mixinStandardHelpOptions = true)
    public int config(
            @Parameters(description = "Name of the repo") String name,
            @Option(names = {"--codec"}, description = "Line codec to use: json|messagepack") String codec) {
        initDefaultConfig();

        Result<Repo, DedupError> repoResult = dedupConfig.getRepo(name);
        if (repoResult.hasFailed()) {
            log.error("Repo '{}' not found: {}", name, repoResult.error().describe());
            return -1;
        }

        Repo repo = repoResult.value();
        Repo.Codec targetCodec = repo.codec();
        if (codec != null) {
            targetCodec = switch (codec.toLowerCase()) {
                case "json" -> Repo.Codec.JSON;
                case "messagepack" -> Repo.Codec.MESSAGEPACK;
                default -> {
                    log.warn("Unknown codec '{}' . Supported: json, messagepack. Keeping current.", codec);
                    yield repo.codec();
                }
            };
        }

        Result<Repo, DedupError> result = dedupConfig.setRepoConfig(name, targetCodec);
        if (result.isSuccess()) {
            log.info("Updated config for repo '{}'", name);
            return 0;
        } else {
            log.error("Failed to update config for repo '{}': {}", name, result.error().describe());
            return -2;
        }
    }

    @Command(name = "prune", description = "Prunes the DB removing all old versions and deleted files", mixinStandardHelpOptions = true)
    public int prune(
            @Parameters(description = "Repos", arity = "0..*") List<String> repos,
            @Option(names = {"-a", "--all"}, description = "All repos") boolean all,
            @Option(defaultValue = "10", names = {"--indices", "-i"}, description = "Number of index files") int indices,
            @Option(names = {"--keep-deleted"}, description = "Keep entries marked as deleted (do not drop missing files)") boolean keepDeleted,
            @Option(names = {"--change-codec"}, description = "Change codec during prune: json|messagepack") String changeCodec) {
        initDefaultConfig();

        if (!all && hasNoRepos(repos)) {
            printUsageError("No repos specified. Provide at least one repo name or use --all.");
            return CommandLine.ExitCode.USAGE;
        }
        Repo.Codec targetCodec = null;
        if (changeCodec != null && !changeCodec.isBlank()) {
            targetCodec = switch (changeCodec.toLowerCase()) {
                case "json" -> Repo.Codec.JSON;
                case "messagepack" -> Repo.Codec.MESSAGEPACK;
                default -> {
                    log.warn("Unknown codec '{}' . Supported: json, messagepack", changeCodec);
                    yield null;
                }
            };
        }

        List<String> allNames = repos == null ? List.of() : repos;
        Result<Integer, DedupError> result = new PruneReposProcess(cliParameter, allNames, all, indices, dedupConfig, keepDeleted, targetCodec).prune();
        if (result.hasFailed()) {
            new DedupConfigErrorHandler().dump(result.error());
            return -30;
        }
        return result.value();
    }

    @Command(name = "cp", description = "Copies the Repo into a new one with a new path, keeping all the entries from the original. The original is unmodified", mixinStandardHelpOptions = true)
    public int clone(
            @Parameters(description = "Source Repo") String sourceRepo,
            @Parameters(description = "Destination Repo") String destinationRepo,
            @Parameters(description = "Path of the new repo") String path) {
        initDefaultConfig();

        Result<Integer, DedupError> result = new CopyRepoProcess(cliParameter, sourceRepo, destinationRepo, path, dedupConfig).copy();
        if (result.hasFailed()) {
            new DedupConfigErrorHandler().dump(result.error());
            return -60;
        }
        return result.value();
    }

    @Command(name = "rel", description = "Relocates the path of a Repo. The entries remain unchanged.", mixinStandardHelpOptions = true)
    public int clone(
            @Parameters(description = "Source Repo") String repo,
            @Parameters(description = "The relocated path") String path) {
        initDefaultConfig();

        Result<Integer, DedupError> result = new RelocateRepoProcess(cliParameter, repo, path, dedupConfig).move();
        if (result.hasFailed()) {
            new DedupConfigErrorHandler().dump(result.error());
            return -70;
        }
        return result.value();
    }

    @Command(name = "mv", description = "Moves the repos to a new Repo. The entries remain unchanged.", mixinStandardHelpOptions = true)
    public int move(
            @Parameters(description = "Source Repo") String sourceRepo,
            @Parameters(description = "Target Repo") String destinationRepo) {
        initDefaultConfig();

        Result<Integer, DedupError> result = new MoveRepoProcess(cliParameter, sourceRepo, destinationRepo, dedupConfig).move();
        if (result.hasFailed()) {
            new DedupConfigErrorHandler().dump(result.error());
            return -90;
        }
        return result.value();
    }

    @Command(name = "dupes", description = "Manage duplicates in one or more repos.", mixinStandardHelpOptions = true)
    public int move(
            @Parameters(description = "Repos", arity = "0..*") List<String> repos,
            @Option(names = {"-a", "--all"}, description = "All repos") boolean all,
            @Option(names = {"--threshold"}, description = "Threshold for image fingerprint similarity (0-100, default 0 for exact match)") Integer threshold,
            @Option(names = {"--print"}, description = "Print duplicate groups to standard out") boolean print,
            @Option(names = {"--md"}, description = "Generate a Markdown report with thumbnails") String mdPath,
            @Option(names = {"--html"}, description = "Generate an HTML report with thumbnails") String htmlPath,
            @Option(names = {"--move"}, description = "Move duplicates to this directory, keeping the first one") String movePath,
            @Option(names = {"--delete"}, description = "Delete duplicates, keeping the first one") boolean delete,
            @Option(names = {"--interactive"}, description = "Interactive mode with a web UI") boolean interactive,
            @Option(names = {"--width"}, description = "Width filter expression (e.g. >=1920, <=800, =1024)") String widthFilter,
            @Option(names = {"--height"}, description = "Height filter expression (e.g. >=1080, <720, =600)") String heightFilter) {
        initDefaultConfig();

        if (!all && hasNoRepos(repos)) {
            printUsageError("No repos specified. Provide at least one repo name or use --all.");
            return CommandLine.ExitCode.USAGE;
        }
        List<String> allNames = repos == null ? List.of() : repos;
        DuplicateRepoProcess.DupePrintMode printMode = getDupePrintMode(print);
        Result<Integer, DedupError> result = new DuplicateRepoProcess(cliParameter, allNames, all, dedupConfig, threshold, printMode, mdPath, htmlPath, movePath, delete, interactive, widthFilter, heightFilter).dupes();
        if (result.hasFailed()) {
            new DedupConfigErrorHandler().dump(result.error());
            return -80;
        }
        return result.value();
    }


    private void initDefaultConfig() {
        dedupConfig = infrastructureConfig.getDedupConfig();
    }

    private DuplicateRepoProcess.DupePrintMode getDupePrintMode(boolean print) {
        if (print) {
            return DuplicateRepoProcess.DupePrintMode.PRINT;
        }
        return DuplicateRepoProcess.DupePrintMode.QUIET;
    }

    private boolean hasNoRepos(List<String> repos) {
        if (repos == null) return true;
        return repos.isEmpty();
    }

    private void printUsageError(String message) {
        if (spec != null && spec.commandLine() != null) {
            spec.commandLine().getErr().println("Error: " + message);
            spec.commandLine().usage(spec.commandLine().getErr());
        } else {
            System.err.println("Error: " + message);
        }
    }

}
