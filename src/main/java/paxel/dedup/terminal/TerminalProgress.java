package paxel.dedup.terminal;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public class TerminalProgress {
    private final boolean verbose;
    private final Terminal terminal;
    private TerminalPosition originalCursorPos;

    private AtomicReference<Instant> lastDraw = new AtomicReference();

    private Map<String, String> values = new LinkedHashMap<>();

    public static TerminalProgress init(boolean verbose) {
        try {
            Terminal terminal = new DefaultTerminalFactory().createTerminal();

            TerminalProgress terminalProgress = new TerminalProgress(verbose, terminal);
            terminalProgress.activate();
            return terminalProgress;
        } catch (IOException e) {
            System.err.println(e.getMessage());
            // return Mock
            return null;
        }
    }

    public void put(String key, String value) {
        values.put(key, value);
        draw(false);
    }

    private void draw(boolean force) {
        Instant last = lastDraw.get();
        Instant now = Instant.now();
        // don't draw more often than once per second
        if (!force && last != null && now.minus(500, ChronoUnit.MILLIS).isBefore(last))
            return;
        lastDraw.set(now);
        try {
            makeRoom();
            int i = 0;
            TerminalSize terminalSize = terminal.getTerminalSize();
            //   textGraphics.fillRectangle(new TerminalPosition(0, originalCursorPos.getRow()), new TerminalSize(terminalSize.getColumns(), values.size()), ' ');
            for (Map.Entry<String, String> stringStringEntry : values.entrySet()) {
                terminal.setCursorPosition(new TerminalPosition(0, originalCursorPos.getRow() + i));
                i++;
                String s = "%s: %s".formatted(stringStringEntry.getKey(), stringStringEntry.getValue());
                int i1 = terminalSize.getColumns() - s.length();
                if (i1 >= 0)
                    terminal.putString(s + (" ".repeat(i1)));
                else
                    terminal.putString(s.substring(0, terminalSize.getColumns() - 1));
            }
            terminal.setCursorPosition(new TerminalPosition(0, originalCursorPos.getRow()));
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    private void activate() {
        try {
            terminal.setCursorVisible(false);
            originalCursorPos = terminal.getCursorPosition();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            // ignore for now
        }
    }

    private void makeRoom() throws IOException {
        int i = terminal.getTerminalSize().getRows() - originalCursorPos.getRow();
        if (i < values.size()+1) {
            terminal.putString("\n".repeat(values.size()+1));
            originalCursorPos = new TerminalPosition(0, originalCursorPos.getRow() - i);
        }
    }

    public void deactivate() {
        try {
            // draw last update
            draw(true);
            terminal.setCursorPosition(0, originalCursorPos.getRow() + values.size());
            terminal.putString("\n");
            terminal.flush();
            terminal.setCursorVisible(true);
            terminal.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
