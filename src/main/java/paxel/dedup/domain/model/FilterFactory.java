package paxel.dedup.domain.model;

import java.util.function.Predicate;

public class FilterFactory {
    public Predicate<RepoFile> createFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return a -> true;
        } else if (filter.startsWith("mime:")) {
            String substring = filter.substring(5);
            return a -> {
                if (a.mimeType() == null) {
                    return false;
                }
                return a.mimeType().contains(substring);
            };
        } else if (filter.startsWith("name:")) {
            String substring = filter.substring(5);
            return a -> {
                if (a.relativePath() == null) {
                    return false;
                }
                return a.relativePath().contains(substring);
            };
        } else if (filter.startsWith("size:")) {
            String substring = filter.substring(5);
            try {
                long size = Long.parseLong(substring);
                return a -> a.size() != null && a.size() == size;
            } catch (NumberFormatException e) {
                return a -> false;
            }
        } else {
            return a -> false;
        }
    }

}
