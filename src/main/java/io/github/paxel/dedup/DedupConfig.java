package io.github.paxel.dedup;

import com.beust.jcommander.Parameter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 *
 */
public class DedupConfig {

    @Parameter(names = {"-s", "--safe"}, description = "Comma-separated list of paths, that will not be modified.")
    private List<String> safe = Collections.emptyList();

    @Parameter(names = {"-u", "--unsafe"}, description = "Comma-separated list of group names to be handled by the action")
    private List<String> unsafe = Collections.emptyList();

    @Parameter(names = "-v", description = "Verbose statistics")
    private boolean verbose;

    @Parameter(names = "-vv", description = "Super verbose statistics")
    private boolean realVerbose;

    @Parameter(names = "--action", description = "What to do with the duplicates? ")
    private Action action = Action.PRINT;

    @Parameter(names = "--target-dir", description = "target dir for the move action")
    private String targetDir;

    public List<String> getSafe() {
        return this.safe;
    }

    public List<String> getUnsafe() {
        return this.unsafe;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public boolean isRealVerbose() {
        return this.realVerbose;
    }

    public Action getAction() {
        return this.action;
    }

    public String getTargetDir() {
        return this.targetDir;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof DedupConfig other)) return false;
        if (!other.canEqual(this)) return false;
        final Object this$safe = this.getSafe();
        final Object other$safe = other.getSafe();
        if (!Objects.equals(this$safe, other$safe)) return false;
        final Object this$unsafe = this.getUnsafe();
        final Object other$unsafe = other.getUnsafe();
        if (!Objects.equals(this$unsafe, other$unsafe)) return false;
        if (this.isVerbose() != other.isVerbose()) return false;
        if (this.isRealVerbose() != other.isRealVerbose()) return false;
        final Object this$action = this.getAction();
        final Object other$action = other.getAction();
        if (!Objects.equals(this$action, other$action)) return false;
        final Object this$targetDir = this.getTargetDir();
        final Object other$targetDir = other.getTargetDir();
        return Objects.equals(this$targetDir, other$targetDir);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof DedupConfig;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $safe = this.getSafe();
        result = result * PRIME + ($safe == null ? 43 : $safe.hashCode());
        final Object $unsafe = this.getUnsafe();
        result = result * PRIME + ($unsafe == null ? 43 : $unsafe.hashCode());
        result = result * PRIME + (this.isVerbose() ? 79 : 97);
        result = result * PRIME + (this.isRealVerbose() ? 79 : 97);
        final Object $action = this.getAction();
        result = result * PRIME + ($action == null ? 43 : $action.hashCode());
        final Object $targetDir = this.getTargetDir();
        result = result * PRIME + ($targetDir == null ? 43 : $targetDir.hashCode());
        return result;
    }

    public String toString() {
        return "DedupConfig(safe=" + this.getSafe() + ", unsafe=" + this.getUnsafe() + ", verbose=" + this.isVerbose() + ", realVerbose=" + this.isRealVerbose() + ", action=" + this.getAction() + ", targetDir=" + this.getTargetDir() + ")";
    }
}
