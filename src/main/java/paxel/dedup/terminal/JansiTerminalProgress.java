package paxel.dedup.terminal;


import lombok.RequiredArgsConstructor;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public class JansiTerminalProgress implements TerminalProgress {
    private final ProgressPrinter progressPrinter;
    private int lastSize;

    private final AtomicReference<Instant> lastDraw = new AtomicReference<>();

    synchronized void draw(boolean force) {
        Instant last = lastDraw.get();
        Instant now = Instant.now();
        // don't draw more often than once per second
        if (!force && last != null && now.minus(300, ChronoUnit.MILLIS).isBefore(last)) {
            return;
        }
        lastDraw.set(now);
        System.out.print(Ansi.ansi().cursorUp(lastSize));
        int lines = progressPrinter.getLines();
        for (int row = 0; row < lines; row++) {
            String s = progressPrinter.getLineAt(row);
            int stringSize = s.length();
            int terminalWidth = AnsiConsole.getTerminalWidth();
            int toEndOfLine = terminalWidth - stringSize;
            if (toEndOfLine >= 0) {
                String fullLine = s + (" ".repeat(toEndOfLine));
                System.out.print(Ansi.ansi().eraseLine());
                System.out.println(Ansi.ansi().render(fullLine));
            } else {
                int i = terminalWidth / 2 - 2;
                System.out.println(s.substring(0, i) + "[..]" + s.substring(s.length() - i));
            }
        }
        lastSize = lines;

    }

    void activate() {
        try {
            AnsiConsole.systemInstall();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            // ignore for now
        }
    }


    @Override
    public void deactivate() {
        // draw last update
        draw(true);
        AnsiConsole.systemUninstall();
        System.out.println();
        System.out.println();
    }
}
