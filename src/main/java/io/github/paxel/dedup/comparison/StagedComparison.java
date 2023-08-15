package io.github.paxel.dedup.comparison;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StagedComparison {


    private final List<Stage> stages;

    public @NonNull StagedComparison(@NonNull List<Stage> stages) {
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
    }

    public boolean hasStage(int stage) {
        return stages.size() > stage;
    }

    public Stage getStage(int stage) {
        return stages.get(stage);
    }
}
