package paxel.dedup.domain.model;

import java.util.function.Predicate;

public interface Filter extends Predicate<RepoFile> {
}
