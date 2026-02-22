package paxel.dedup.domain.model;

import org.apache.tika.Tika;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;

public class MimetypeProvider {
    private static final Tika TIKA = new Tika();

    public Result<String, DedupError> get(Path file) {
        try {
            return Result.ok(TIKA.detect(file));
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.IO, file + ": mimetype detection failed", e));
        }
    }
}
