package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.errors.ModifyRepoError;
import paxel.dedup.application.cli.parameter.CliParameter;
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
    private final CliParameter cliParameter;
    private final String sourceRepo;
    private final String destinationRepo;
    private final String path;
    private final DedupConfig dedupConfig;

    public int copy() {
        if (sourceRepo.equals(destinationRepo)) {
            System.err.println("Can not copy to same directory");
            return -62;
        }

        if (cliParameter.isVerbose()) {
            System.out.printf("cloning %s to %s%n", sourceRepo, destinationRepo);
        }
        List<IOException> ioExceptions = copyDirectory(dedupConfig.getRepoDir().resolve(sourceRepo), dedupConfig.getRepoDir().resolve(destinationRepo));

        ioExceptions.forEach(Throwable::printStackTrace);
        if (!ioExceptions.isEmpty()) {
            return -61;
        }
        Result<Repo, ModifyRepoError> repoModifyRepoErrorResult = dedupConfig.changePath(destinationRepo, Paths.get(path));
        if (repoModifyRepoErrorResult.hasFailed()) {
            System.err.printf("cloning %s to %s failed: %s%n", sourceRepo, destinationRepo, repoModifyRepoErrorResult.error());
            return -60;
        }
        if (cliParameter.isVerbose()) {
            System.out.printf("cloning %s to %s%n", sourceRepo, destinationRepo);
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