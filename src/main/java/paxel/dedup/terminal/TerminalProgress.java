package paxel.dedup.terminal;


public interface TerminalProgress {


    static TerminalProgress initLanterna(ProgressPrinter progressPrinter) {

        var terminalProgress = new JansiTerminalProgress(progressPrinter);
        progressPrinter.registerChangeListener(() -> terminalProgress.draw(false));
        terminalProgress.activate();
        return terminalProgress;
    }

    public static TerminalProgress initDummy(ProgressPrinter progressPrinter) {
        progressPrinter.registerChangeListener(() -> {
        });
        // the dummy will not print anything
        return new TerminalProgress() {
            @Override
            public void deactivate() {

            }
        };
    }

    void deactivate();
}
