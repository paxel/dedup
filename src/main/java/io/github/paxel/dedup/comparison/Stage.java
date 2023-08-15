package io.github.paxel.dedup.comparison;

import lombok.NonNull;
import paxel.lib.Result;

import java.nio.file.Path;

public interface Stage {

    @NonNull Result<Comparison, ComparisonError> create(@NonNull Path p);
}
