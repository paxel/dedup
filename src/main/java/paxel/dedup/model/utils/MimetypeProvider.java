package paxel.dedup.model.utils;

import org.apache.tika.Tika;
import paxel.dedup.model.errors.IoError;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;

public class MimetypeProvider {

    private Tika tika;

    public Result<String, IoError> get(Path file) {
        try {
            tika = new Tika();
            return Result.ok(tika.detect(file));
        } catch (IOException e) {
            return Result.err(new IoError(e, file, null));
        }
    }
}
