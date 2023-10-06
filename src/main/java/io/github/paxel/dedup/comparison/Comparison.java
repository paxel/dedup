package io.github.paxel.dedup.comparison;

import java.util.Objects;

public record Comparison(String key) {

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Comparison other)) return false;
        if (!other.canEqual(this)) return false;
        final Object this$key = this.key();
        final Object other$key = other.key();
        return Objects.equals(this$key, other$key);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Comparison;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $key = this.key();
        result = result * PRIME + ($key == null ? 43 : $key.hashCode());
        return result;
    }

    public String toString() {
        return "Comparison(" + this.key() + ")";
    }
}
