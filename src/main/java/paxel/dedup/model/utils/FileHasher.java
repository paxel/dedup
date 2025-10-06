package paxel.dedup.model.utils;

import paxel.dedup.model.errors.LoadError;
import paxel.lib.Result;

import java.nio.file.Path;

public interface FileHasher {
    Result<String, LoadError> hash(Path path);
}
