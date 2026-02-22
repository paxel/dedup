package paxel.dedup.infrastructure.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleLoggerTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private ConsoleLogger logger;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        logger = ConsoleLogger.getInstance();
        logger.setVerbose(false);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        logger.setVerbose(false);
    }

    @Test
    void debug_is_not_logged_when_verbose_is_false() {
        logger.debug("test message");
        assertThat(outContent.toString()).isEmpty();
    }

    @Test
    void debug_is_logged_when_verbose_is_true() {
        logger.setVerbose(true);
        logger.debug("test message");
        assertThat(outContent.toString()).contains("test message");
    }

    @Test
    void info_is_always_logged() {
        logger.setVerbose(false);
        logger.info("info message");
        assertThat(outContent.toString()).contains("info message");

        outContent.reset();

        logger.setVerbose(true);
        logger.info("info message 2");
        assertThat(outContent.toString()).contains("info message 2");
    }
}
