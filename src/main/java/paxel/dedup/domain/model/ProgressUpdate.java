package paxel.dedup.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class ProgressUpdate {
    String repo;
    String path;
    String currentFile;
    String status; // replaces "progress" string
    Double progressPercent;
    Long filesProcessed;
    Long filesTotal;
    Long hashedProcessed;
    Long hashedTotal;
    Long unchangedProcessed;
    Long unchangedTotal;
    Long directoriesProcessed;
    Long directoriesTotal;
    Long deletedProcessed;
    Long deletedTotal;
    String duration;
    String eta;
    String errors;
    Map<String, Long> mimeDistribution;
}
