package io.github.paxel.dedup.comparison;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

public class StagedComparisonFactory {

    @NonNull public StagedComparison createRaw(@NonNull Path path) {
        try {
            long size = Files.size(path);
            return createRaw(size);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull public StagedComparison createRaw(long size) {
        if (size < 10000) {
            // small files are just hashed completely
            return new StagedComparison(Collections.singletonList(new RawByteComparisonStage(0, size, Hasher.MD5)));
        }
        else {
            return new StagedComparison(Arrays.asList(
                    // small block in the front
                    new RawByteComparisonStage(0, 1024, Hasher.MD5),
                    // small block in the back
                    new RawByteComparisonStage(size - 1024, 1024, Hasher.MD5),
                    // rest
                    new RawByteComparisonStage(1024, size - 2048, Hasher.MD5)
            ));
        }
    }
}
