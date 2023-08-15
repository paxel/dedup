package io.github.paxel.dedup.comparison;

public class ComparisonError {
    public ComparisonError(String description, Exception e) {

    }

    public static ComparisonError hashFailed(Hasher.HashError hashError) {
        return new ComparisonError(hashError.getDescription(),hashError.getE());
    }
}
