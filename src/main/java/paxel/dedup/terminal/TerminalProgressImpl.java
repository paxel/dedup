package paxel.dedup.terminal;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public class TerminalProgressImpl implements TerminalProgress {
    private final ProgressPrinter progressPrinter;
    private final Terminal terminal;

    private TerminalPosition originalCursorPos;

    private final AtomicReference<Instant> lastDraw = new AtomicReference<>();

    void draw(boolean force) {
        Instant last = lastDraw.get();
        Instant now = Instant.now();
        // don't draw more often than once per second
        if (!force && last != null && now.minus(300, ChronoUnit.MILLIS).isBefore(last)) {
            return;
        }
        lastDraw.set(now);
        try {

            makeRoom();
            TerminalSize terminalSize = terminal.getTerminalSize();
            terminal.enableSGR(SGR.BOLD);
            terminal.setBackgroundColor(TextColor.ANSI.BLACK);
            //   textGraphics.fillRectangle(new TerminalPosition(0, originalCursorPos.getRow()), new TerminalSize(terminalSize.getColumns(), values.size()), ' ');
            for (int row = 0; row < progressPrinter.getLines(); row++) {
                terminal.setCursorPosition(new TerminalPosition(0, originalCursorPos.getRow() + row));
                String s = progressPrinter.getLineAt(row);
                int stringSize = s.length();
                int columns = terminalSize.getColumns();
                int toEndOfLine = columns - stringSize;
                terminal.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
                if (toEndOfLine >= 0) {
                    String fullLine = s + (" ".repeat(toEndOfLine));
                    terminal.putString(fullLine);
                } else {
                    int i = terminalSize.getColumns() / 2 - 2;
                    terminal.putString(s.substring(0, i) + "[..]" + s.substring(s.length() - i));
                }
            }
            KeyStroke keyStroke = terminal.pollInput();
            if (keyStroke != null && keyStroke.isCtrlDown() && keyStroke.getCharacter() == 'c') {
                System.out.println("Aborted");
                terminal.setCursorVisible(true);
                terminal.resetColorAndSGR();
                System.exit(-5);
            }
            terminal.resetColorAndSGR();
            terminal.setCursorPosition(new TerminalPosition(0, originalCursorPos.getRow()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void activate() {
        try {
            terminal.setCursorVisible(false);
            originalCursorPos = terminal.getCursorPosition();
        } catch (Exception e) {
            try {
                terminal.setCursorVisible(true);
            } catch (IOException ex) {
                // oh fuck
            }

            System.err.println(e.getMessage());
            // ignore for now
        }
    }

    private void makeRoom() throws IOException {
        int remainingLines = terminal.getTerminalSize().getRows() - originalCursorPos.getRow();
        int lines = progressPrinter.getLines();
        if (remainingLines <= lines) {
            // put to end
            terminal.setCursorPosition(new TerminalPosition(0, terminal.getTerminalSize().getRows()));
            int additionalLines = lines - remainingLines;
            for (int i = 0; i < additionalLines; i++)
                System.out.println();

            // store new position after scrolling down
            originalCursorPos = new TerminalPosition(0, originalCursorPos.getRow() - (additionalLines));
        }
    }

    @Override
    public void deactivate() {
        try {
            // draw last update
            draw(true);
            terminal.setCursorPosition(0, originalCursorPos.getRow() + progressPrinter.getLines());
            System.out.println();
            terminal.flush();
            terminal.setCursorVisible(true);
            terminal.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
