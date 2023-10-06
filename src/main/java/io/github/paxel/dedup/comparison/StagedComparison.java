package io.github.paxel.dedup.comparison;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StagedComparison {


    private final List<Stage> stages;

    public StagedComparison(List<Stage> stages) {
        this.stages = List.copyOf(stages);
    }

    public boolean hasStage(int stage) {
        return stages.size() > stage;
    }

    public Stage getStage(int stage) {
        return stages.get(stage);
    }
}
