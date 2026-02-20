package paxel.dedup.domain.model;

import org.apache.tika.Tika;
import paxel.dedup.domain.model.errors.IoError;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;

public class MimetypeProvider {

    public Result<String, IoError> get(Path file) {
        try {
            Tika tika = new Tika();
            return Result.ok(tika.detect(file));
        } catch (IOException e) {
            return Result.err(new IoError(e, file, null));
        }
    }
}
