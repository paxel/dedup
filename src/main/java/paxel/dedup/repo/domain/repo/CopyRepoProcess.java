package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.infrastructure.logging.ConsoleLogger;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class CopyRepoProcess {
    private static final ConsoleLogger log = ConsoleLogger.getInstance();
    private final CliParameter cliParameter;
    private final String sourceRepo;
    private final String destinationRepo;
    private final String path;
    private final DedupConfig dedupConfig;

    public int copy() {
        if (sourceRepo.equals(destinationRepo)) {
            log.error("Cannot copy to the same directory: {}", sourceRepo);
            return -62;
        }

        if (cliParameter.isVerbose()) {
            log.info("cloning {} to {}", sourceRepo, destinationRepo);
        }
        List<IOException> ioExceptions = copyDirectory(dedupConfig.getRepoDir().resolve(sourceRepo), dedupConfig.getRepoDir().resolve(destinationRepo));

        ioExceptions.forEach(ex -> log.error("Copying file failed:", ex));
        if (!ioExceptions.isEmpty()) {
            return -61;
        }
        Result<Repo, DedupError> repoModifyRepoErrorResult = dedupConfig.changePath(destinationRepo, Paths.get(path));
        if (repoModifyRepoErrorResult.hasFailed()) {
            log.error("cloning {} to {} failed: {}", sourceRepo, destinationRepo, repoModifyRepoErrorResult.error());
            return -60;
        }
        if (cliParameter.isVerbose()) {
            log.info("cloning {} to {}", sourceRepo, destinationRepo);
        }
        return 0;
    }

    public List<IOException> copyDirectory(Path from, Path to) {
        List<IOException> errors = new ArrayList<>();
        try (Stream<Path> fileStream = Files.walk(from)) {
            fileStream.forEach(source -> {
                Path destination = to.resolve(from.relativize(source).toString());
                try {
                    Files.copy(source, destination);
                } catch (IOException e) {
                    errors.add(e);
                }
            });
        } catch (IOException e) {
            errors.add(e);
        }
        return errors;
    }
}