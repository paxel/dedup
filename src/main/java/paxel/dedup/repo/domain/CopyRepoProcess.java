package paxel.dedup.repo.domain;

import lombok.RequiredArgsConstructor;
import paxel.dedup.config.DedupConfig;
import paxel.dedup.model.Repo;
import paxel.dedup.model.errors.ModifyRepoError;
import paxel.dedup.parameter.CliParameter;
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
        if (cliParameter.isVerbose()) {
            System.out.println("cloning " + sourceRepo + " to " + destinationRepo);
        }
        List<IOException> ioExceptions = copyDirectory(dedupConfig.getRepoDir().resolve(sourceRepo), dedupConfig.getRepoDir().resolve(destinationRepo));
        ioExceptions.forEach(Throwable::printStackTrace);
        Result<Repo, ModifyRepoError> repoModifyRepoErrorResult = dedupConfig.changePath(destinationRepo, Paths.get(path));
        if (repoModifyRepoErrorResult.hasFailed())
            return -60;
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