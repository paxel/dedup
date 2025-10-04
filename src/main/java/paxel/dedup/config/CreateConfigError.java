package paxel.dedup.config;

import java.io.IOException;
import java.nio.file.Path;

public record CreateConfigError(Path path, IOException ioException) {
}
