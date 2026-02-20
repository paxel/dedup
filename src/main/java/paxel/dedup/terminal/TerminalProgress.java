package paxel.dedup.terminal;


public interface TerminalProgress {


    static TerminalProgress initLanterna(ProgressPrinter progressPrinter) {

        var terminalProgress = new JansiTerminalProgress(progressPrinter);
        progressPrinter.registerChangeListener(() -> terminalProgress.draw(false));
        terminalProgress.activate();
        return terminalProgress;
    }

    static TerminalProgress initDummy(ProgressPrinter progressPrinter) {
        progressPrinter.registerChangeListener(() -> {
        });
        // the dummy will not print anything
        return () -> {

        };
    }

    void deactivate();
}
