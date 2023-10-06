package io.github.paxel.dedup.comparison;

import paxel.lib.Result;

import java.nio.file.Path;

public interface Stage {

    Result<Comparison, ComparisonError> create(Path p);
}
