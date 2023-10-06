package io.github.paxel.dedup;

import java.util.List;
import java.util.Objects;

public record UniqueFiles(List<FileCollector.FileMessage> result) {

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof UniqueFiles other)) return false;
        if (!other.canEqual(this)) return false;
        final Object this$result = this.result();
        final Object other$result = other.result();
        return Objects.equals(this$result, other$result);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof UniqueFiles;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $result = this.result();
        result = result * PRIME + ($result == null ? 43 : $result.hashCode());
        return result;
    }

    public String toString() {
        return "UniqueFiles(result=" + this.result() + ")";
    }
}
