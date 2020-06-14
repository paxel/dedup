package io.github.paxel.dedup;

import com.beust.jcommander.Parameter;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class DedupConfig {

    @Parameter(names = "-safe", description = "Comma-separated list of paths, that will not be handled by the action.")
    private List<String> safe = Collections.emptyList();

    @Parameter(names = "-unsafe", description = "Comma-separated list of group names to be handled by the action")
    private List<String> unsafe = Collections.emptyList();

    @Parameter(names = "-v", description = "Verbose statistics")
    private boolean verbose;

    @Parameter(names = "-vv", description = "Super verbose statistics")
    private boolean realVerbose;

    @Parameter(names = "-action", description = "What to do with the duplicates? PRINT or DELETE")
    private String action;

    public String getAction() {
        return action;
    }

    public List<String> getSafe() {
        return safe;
    }

    public List<String> getUnsafe() {
        return unsafe;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isRealVerbose() {
        return realVerbose;
    }

    @Override
    public String toString() {
        return "DedupConfig{" + "safe=" + safe + ", unsafe=" + unsafe + ", verbose=" + verbose + ", realVerbose=" + realVerbose + ", action=" + action + '}';
    }

}
