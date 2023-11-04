package io.github.paxel.dedup;

import com.beust.jcommander.Parameter;

import java.util.Collections;
import java.util.List;

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

    @Parameter(names = {"-t", "--target-dir"}, description = "target dir for the move action")
    private String targetDir;

    @Parameter(help = true)
    private boolean help;

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

    protected boolean canEqual(final Object other) {
        return other instanceof DedupConfig;
    }

    @Override
    public String toString() {
        return "DedupConfig{safe=%s, unsafe=%s, verbose=%s, realVerbose=%s, action=%s, targetDir='%s'}"
                .formatted(safe, unsafe, verbose, realVerbose, action, targetDir);
    }
}
