package paxel.dedup.terminal;

public interface ProgressPrinter {

    int getLines();

    String getLineAt(int row);

    void registerChangeListener(Runnable r);
}
