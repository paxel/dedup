package paxel.dedup.config;

import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DedupConfigFactory {
    public static Result<DedupConfig, CreateConfigError> create() {

        Path path = Paths.get(System.getProperty("user.dir"), ".config/dedup/repos");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                return Result.err(new CreateConfigError(path, e));
            }
        }

        return Result.ok(new DefaultDedupConfig(path));
    }
}
