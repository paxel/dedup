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
            String expression = filter.substring(5).trim();
            if (expression.startsWith(">=")) {
                return compareSize(expression.substring(2).trim(), (s, v) -> s >= v);
            } else if (expression.startsWith("<=")) {
                return compareSize(expression.substring(2).trim(), (s, v) -> s <= v);
            } else if (expression.startsWith(">")) {
                return compareSize(expression.substring(1).trim(), (s, v) -> s > v);
            } else if (expression.startsWith("<")) {
                return compareSize(expression.substring(1).trim(), (s, v) -> s < v);
            } else if (expression.startsWith("=")) {
                return compareSize(expression.substring(1).trim(), (s, v) -> s == v);
            } else {
                return compareSize(expression, (s, v) -> s == v);
            }
        } else {
            return a -> false;
        }
    }

    private Predicate<RepoFile> compareSize(String valueStr, java.util.function.BiPredicate<Long, Long> comparator) {
        try {
            long value = Long.parseLong(valueStr);
            return a -> a.size() != null && comparator.test(a.size(), value);
        } catch (NumberFormatException e) {
            return a -> false;
        }
    }

}
