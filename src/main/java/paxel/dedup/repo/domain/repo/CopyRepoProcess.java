package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Slf4j
public class CopyRepoProcess {
    private final CliParameter cliParameter;
    private final String sourceRepo;
    private final String destinationRepo;
    private final String path;
    private final DedupConfig dedupConfig;

    public Result<Integer, DedupError> copy() {
        if (sourceRepo.equals(destinationRepo)) {
            return Result.err(DedupError.of(paxel.dedup.domain.model.errors.ErrorType.MODIFY_REPO, "Cannot copy to the same directory: " + sourceRepo));
        }

        if (cliParameter.isVerbose()) {
            log.info("cloning {} to {}", sourceRepo, destinationRepo);
        }
        List<IOException> ioExceptions = copyDirectory(dedupConfig.getRepoDir().resolve(sourceRepo), dedupConfig.getRepoDir().resolve(destinationRepo));

        if (!ioExceptions.isEmpty()) {
            return Result.err(DedupError.of(paxel.dedup.domain.model.errors.ErrorType.WRITE, "Copying file failed", ioExceptions.getFirst()));
        }
        Result<Repo, DedupError> repoModifyRepoErrorResult = dedupConfig.changePath(destinationRepo, Paths.get(path));
        if (repoModifyRepoErrorResult.hasFailed()) {
            return Result.err(repoModifyRepoErrorResult.error());
        }
        if (cliParameter.isVerbose()) {
            log.info("cloning {} to {}", sourceRepo, destinationRepo);
        }
        return Result.ok(0);
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