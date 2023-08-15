package io.github.paxel.dedup.comparison;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import paxel.lib.Result;

import java.nio.file.Path;

@RequiredArgsConstructor
public class RawByteComparisonStage implements Stage {

    private final long offset;
    private final long size;
    private final String algorithm;

    private final Hasher hasher = new Hasher();

    @Override
    public @NonNull Result<Comparison, ComparisonError> create(@NonNull Path p) {
        Result<String, Hasher.HashError> calc = hasher.calc(p, offset, size, algorithm);
        return calc.map(Comparison::new, ComparisonError::hashFailed);
    }
}
