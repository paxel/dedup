package paxel.dedup.infrastructure.config;

import lombok.NonNull;
import paxel.dedup.domain.model.errors.CreateConfigError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DedupConfigFactory {
    public static @NonNull Result<DedupConfig, CreateConfigError> create() {
        return create(new NioFileSystemAdapter());
    }

    public static @NonNull Result<DedupConfig, CreateConfigError> create(FileSystem fileSystem) {

        Path path = Paths.get(System.getProperty("user.home"), ".config/dedup/repos");
        if (!fileSystem.exists(path)) {
            try {
                fileSystem.createDirectories(path);
            } catch (IOException e) {
                return Result.err(new CreateConfigError(path, e));
            }
        }

        return Result.ok(new DefaultDedupConfig(path, fileSystem));
    }
}
