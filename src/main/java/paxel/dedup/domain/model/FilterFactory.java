package paxel.dedup.domain.model;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class FilterFactory {

    private enum FilterType {
        MIME("mime:"),
        NAME("name:"),
        SIZE("size:");

        private final String prefix;

        FilterType(String prefix) {
            this.prefix = prefix;
        }

        public static FilterType fromString(String filter) {
            if (filter.startsWith(MIME.prefix)) return MIME;
            if (filter.startsWith(NAME.prefix)) return NAME;
            if (filter.startsWith(SIZE.prefix)) return SIZE;
            return null;
        }
    }

    private interface Filter extends Predicate<RepoFile> {
    }

    public Predicate<RepoFile> createFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return a -> true;
        }

        FilterType type = FilterType.fromString(filter);
        if (type == null) {
            return a -> false;
        }

        String expression = filter.substring(type.prefix.length()).trim();

        return switch (type) {
            case MIME -> new MimeFilter(expression);
            case NAME -> new NameFilter(expression);
            case SIZE -> createSizeFilter(expression);
        };
    }

    private Predicate<RepoFile> createSizeFilter(String expression) {
        if (expression.startsWith(">=")) {
            return new SizeFilter(expression.substring(2).trim(), (s, v) -> s >= v);
        } else if (expression.startsWith("<=")) {
            return new SizeFilter(expression.substring(2).trim(), (s, v) -> s <= v);
        } else if (expression.startsWith(">")) {
            return new SizeFilter(expression.substring(1).trim(), (s, v) -> s > v);
        } else if (expression.startsWith("<")) {
            return new SizeFilter(expression.substring(1).trim(), (s, v) -> s < v);
        } else if (expression.startsWith("=")) {
            return new SizeFilter(expression.substring(1).trim(), (s, v) -> s == v);
        } else {
            return new SizeFilter(expression, Objects::equals);
        }
    }

    private record MimeFilter(String substring) implements Filter {
        @Override
        public boolean test(RepoFile a) {
            return a.mimeType() != null && a.mimeType().contains(substring);
        }
    }

    private record NameFilter(String substring) implements Filter {
        @Override
        public boolean test(RepoFile a) {
            return a.relativePath() != null && a.relativePath().contains(substring);
        }
    }

    private record SizeFilter(String valueStr, BiPredicate<Long, Long> comparator) implements Filter {
        @Override
        public boolean test(RepoFile a) {
            try {
                long value = Long.parseLong(valueStr);
                return a.size() != null && comparator.test(a.size(), value);
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
