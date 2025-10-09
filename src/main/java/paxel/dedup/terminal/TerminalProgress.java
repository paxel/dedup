package paxel.dedup.terminal;

import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;

public interface TerminalProgress {
    static TerminalProgress init(ProgressPrinter progressPrinter) {
        try {
            Terminal terminal = new DefaultTerminalFactory().createTerminal();

            TerminalProgressImpl terminalProgress = new TerminalProgressImpl(progressPrinter, terminal);
            progressPrinter.registerChangeListener(() -> terminalProgress.draw(false));
            terminalProgress.activate();
            return terminalProgress;
        } catch (IOException e) {

            System.err.println(e.getMessage());

            // the dummy will not print anything
            return new TerminalProgress() {
                @Override
                public void deactivate() {

                }
            };
        }
    }

    void deactivate();
}
