package io.github.paxel.dedup;

import java.util.Objects;

public record Stats(int readonly, int readwrite, int deletedSuccessfully, int deletionFailed, Throwable lastException) {

    public static StatsBuilder builder() {
        return new StatsBuilder();
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Stats other)) return false;
        if (!other.canEqual(this)) return false;
        if (this.readonly() != other.readonly()) return false;
        if (this.readwrite() != other.readwrite()) return false;
        if (this.deletedSuccessfully() != other.deletedSuccessfully()) return false;
        if (this.deletionFailed() != other.deletionFailed()) return false;
        final Object this$lastException = this.lastException();
        final Object other$lastException = other.lastException();
        return Objects.equals(this$lastException, other$lastException);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Stats;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.readonly();
        result = result * PRIME + this.readwrite();
        result = result * PRIME + this.deletedSuccessfully();
        result = result * PRIME + this.deletionFailed();
        final Object $lastException = this.lastException();
        result = result * PRIME + ($lastException == null ? 43 : $lastException.hashCode());
        return result;
    }

    public String toString() {
        return "Stats(readonly=" + this.readonly() + ", readwrite=" + this.readwrite() + ", deletedSuccessfully=" + this.deletedSuccessfully() + ", deletionFailed=" + this.deletionFailed() + ", lastException=" + this.lastException() + ")";
    }

    public StatsBuilder toBuilder() {
        return new StatsBuilder().setReadonly(this.readonly).setReadwrite(this.readwrite).setDeletedSuccessfully(this.deletedSuccessfully).setDeletionFailed(this.deletionFailed).setLastException(this.lastException);
    }

    public static class StatsBuilder {
        private int readonly;
        private int readwrite;
        private int deletedSuccessfully;
        private int deletionFailed;
        private Throwable lastException;

        StatsBuilder() {
        }

        public StatsBuilder setReadonly(int readonly) {
            this.readonly = readonly;
            return this;
        }

        public StatsBuilder setReadwrite(int readwrite) {
            this.readwrite = readwrite;
            return this;
        }

        public StatsBuilder setDeletedSuccessfully(int deletedSuccessfully) {
            this.deletedSuccessfully = deletedSuccessfully;
            return this;
        }

        public StatsBuilder setDeletionFailed(int deletionFailed) {
            this.deletionFailed = deletionFailed;
            return this;
        }

        public StatsBuilder setLastException(Throwable lastException) {
            this.lastException = lastException;
            return this;
        }

        public Stats build() {
            return new Stats(this.readonly, this.readwrite, this.deletedSuccessfully, this.deletionFailed, this.lastException);
        }

        public String toString() {
            return "Stats.StatsBuilder(readonly=" + this.readonly + ", readwrite=" + this.readwrite + ", deletedSuccessfully=" + this.deletedSuccessfully + ", deletionFailed=" + this.deletionFailed + ", lastException=" + this.lastException + ")";
        }
    }
}
