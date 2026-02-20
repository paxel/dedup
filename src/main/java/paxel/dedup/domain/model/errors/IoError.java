package paxel.dedup.domain.model.errors;

import java.io.IOException;
import java.nio.file.Path;

public record IoError(IOException ioException, Path path, String description) {
}
