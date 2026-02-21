package paxel.dedup.domain.model.errors;

import lombok.NonNull;

/**
 * Application error type with a single canonical category enum {@link ErrorType}.
 * Includes an optional human-readable description and an optional originating exception.
 * Use {@link #describe()} to get a user-facing message.
 */
public record DedupError(@NonNull ErrorType type,
                         String description,
                         Exception exception) {

    public static DedupError of(@NonNull ErrorType type) {
        return new DedupError(type, null, null);
    }

    public static DedupError of(@NonNull ErrorType type, String description) {
        return new DedupError(type, description, null);
    }

    public static DedupError of(@NonNull ErrorType type, String description, Exception exception) {
        return new DedupError(type, description, exception);
    }

    public DedupError withDescription(String description) {
        return new DedupError(type, description, exception);
    }

    public DedupError withException(Exception exception) {
        return new DedupError(type, description, exception);
    }

    /**
     * Builds a concise, human-readable description including the error type, optional
     * description, and the originating exception's message if present.
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name().replace('_', ' ').toLowerCase());
        if (description != null && !description.isBlank()) {
            sb.append(": ").append(description);
        }
        if (exception != null) {
            String msg = exception.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (description == null || description.isBlank()) {
                    sb.append(": ");
                } else {
                    sb.append(" (");
                }
                sb.append(msg);
                if (description != null && !description.isBlank()) {
                    sb.append(")");
                }
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
