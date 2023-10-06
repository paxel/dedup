package io.github.paxel.dedup;

import java.util.Objects;

public record Duplicate(FileCollector.FileMessage original, FileCollector.FileMessage duplicate) {

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Duplicate other)) return false;
        if (!other.canEqual(this)) return false;
        final Object this$original = this.original();
        final Object other$original = other.original();
        if (!Objects.equals(this$original, other$original)) return false;
        final Object this$duplicate = this.duplicate();
        final Object other$duplicate = other.duplicate();
        return Objects.equals(this$duplicate, other$duplicate);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Duplicate;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $original = this.original();
        result = result * PRIME + ($original == null ? 43 : $original.hashCode());
        final Object $duplicate = this.duplicate();
        result = result * PRIME + ($duplicate == null ? 43 : $duplicate.hashCode());
        return result;
    }

    public String toString() {
        return "Duplicate(original=" + this.original() + ", duplicate=" + this.duplicate() + ")";
    }
}
