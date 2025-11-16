package paxel.dedup.terminal;

import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

public class JLineTerminalProgressImpl implements TerminalProgress {
    private final ProgressPrinter progressPrinter;
    private final Terminal terminal;
    private final PrintWriter writer;
    private int linesPrinted;


    private final AtomicReference<Instant> lastDraw = new AtomicReference<>();

    public JLineTerminalProgressImpl(ProgressPrinter progressPrinter, Terminal terminal) {
        this.progressPrinter = progressPrinter;
        this.terminal = terminal;
        writer = terminal.writer();
    }

    synchronized void draw(boolean force) {
        Instant last = lastDraw.get();
        Instant now = Instant.now();
        // don't draw more often than once per second
        if (!force && last != null && now.minus(300, ChronoUnit.MILLIS).isBefore(last)) {
            return;
        }
        lastDraw.set(now);
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.flush();
        try {
            for (int row = 0; row < progressPrinter.getLines(); row++) {
                String s = progressPrinter.getLineAt(row);
                int stringSize = s.length();
                int columns = terminal.getWidth();
                if (columns > 0) {
                    int toEndOfLine = columns - stringSize;
                    if (toEndOfLine >= 0) {
                        String fullLine = s + (" ".repeat(toEndOfLine));
                        writer.println(fullLine);
                    } else {
                        int i = columns / 2 - 2;
                        writer.println(s.substring(0, i) + "[..]" + s.substring(s.length() - i));
                    }
                } else {
                    writer.println(s);
                }
            }
            linesPrinted = progressPrinter.getLines();

        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    void activate() {
    }


    @Override
    public void deactivate() {
        try {
            writer.println(".... Done");
            writer.println();
            terminal.flush();
            terminal.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
