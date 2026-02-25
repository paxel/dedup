package paxel.dedup.domain.model;

import lombok.Getter;

@Getter
public class SyncCounters {
    private long equal;
    private long newCount;
    private long deletedCount;
    private long copied;
    private long removed;
    private long skipped;
    private long errors;

    public void incrementEqual() {
        equal++;
    }

    public void incrementNew() {
        newCount++;
    }

    public void incrementDeleted() {
        deletedCount++;
    }

    public void incrementCopied() {
        copied++;
    }

    public void incrementRemoved() {
        removed++;
    }

    public void incrementSkipped() {
        skipped++;
    }

    public void incrementErrors() {
        errors++;
    }

    public String summary() {
        return String.format("Sync summary: equal=%d, new=%d, deleted=%d, copied=%d, removed=%d, skipped=%d, errors=%d",
                equal, newCount, deletedCount, copied, removed, skipped, errors);
    }
}
