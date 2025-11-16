package paxel.dedup.terminal;

import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

public interface TerminalProgress {
    static TerminalProgress initJline(ProgressPrinter progressPrinter) {

        try {
            var terminalProgress = new JLineTerminalProgressImpl(progressPrinter, TerminalBuilder.builder().build());
            progressPrinter.registerChangeListener(() -> terminalProgress.draw(false));
            terminalProgress.activate();
            return terminalProgress;
        } catch (IOException e) {

            System.err.println(e.getMessage());

            return initDummy(progressPrinter);
        }
    }

    static TerminalProgress initLanterna(ProgressPrinter progressPrinter) {

        try {
            var terminalProgress = new LanternaTerminalProgress(progressPrinter, new DefaultTerminalFactory().createTerminal());
            progressPrinter.registerChangeListener(() -> terminalProgress.draw(false));
            terminalProgress.activate();
            return terminalProgress;
        } catch (IOException e) {

            System.err.println(e.getMessage());

            return initDummy(progressPrinter);
        }
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
