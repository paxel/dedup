package io.github.paxel.dedup.comparison;

import paxel.lib.Result;

import java.nio.file.Path;

public class RawByteComparisonStage implements Stage {

    private final long offset;
    private final long size;
    private final String algorithm;

    private final Hasher hasher = new Hasher();

    public RawByteComparisonStage(long offset, long size, String algorithm) {
        this.offset = offset;
        this.size = size;
        this.algorithm = algorithm;
    }

    @Override
    public Result<Comparison, ComparisonError> create(Path p) {
        Result<String, Hasher.HashError> calc = hasher.calc(p, offset, size, algorithm);
        return calc.map(Comparison::new, ComparisonError::hashFailed);
    }
}
