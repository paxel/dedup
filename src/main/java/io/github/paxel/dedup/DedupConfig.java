package io.github.paxel.dedup;

import com.beust.jcommander.Parameter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

/**
 *
 */
@Getter
@EqualsAndHashCode
@ToString
public class DedupConfig {

    @Parameter(names = {"-s","--safe"}, description = "Comma-separated list of paths, that will not be modified.")
    private List<String> safe = Collections.emptyList();

    @Parameter(names = {"-u","--unsafe"}, description = "Comma-separated list of group names to be handled by the action")
    private List<String> unsafe = Collections.emptyList();

    @Parameter(names = "-v", description = "Verbose statistics")
    private boolean verbose;

    @Parameter(names = "-vv", description = "Super verbose statistics")
    private boolean realVerbose;

    @Parameter(names = "-action", description = "What to do with the duplicates? PRINT or DELETE")
    private String action;

}
