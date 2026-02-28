package paxel.dedup.terminal;

import paxel.dedup.domain.model.ProgressUpdate;

public interface ProgressPrinter {

    int getLines();

    String getLineAt(int row);

    void registerChangeListener(Runnable r);

    default void update(ProgressUpdate update) {
    }

    default void finish() {
    }
}
