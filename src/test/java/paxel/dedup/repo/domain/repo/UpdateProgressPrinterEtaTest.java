package paxel.dedup.repo.domain.repo;

import org.junit.jupiter.api.Test;
import paxel.dedup.domain.model.FileHasher;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.terminal.StatisticPrinter;
import paxel.lib.Result;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateProgressPrinterEtaTest {

    /**
     * Simple mutable clock to deterministically control time in tests.
     */
    static class MutableClock extends Clock {
        private Instant current;
        private final ZoneId zone;

        MutableClock(Instant start, ZoneId zone) {
            this.current = start;
            this.zone = zone;
        }

        void tick(Duration d) {
            this.current = this.current.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    @Test
    void printsDeterministicEtaAfterThirtySecondsWhenScanFinished() {
        // Arrange a deterministic clock and printer that captures updates
        MutableClock clock = new MutableClock(Instant.EPOCH, ZoneId.of("UTC"));
        StatisticPrinter printer = new StatisticPrinter();
        AtomicReference<String> lastProgress = new AtomicReference<>("");
        // statistic printer requires a change listener; we capture the current progress line (row 1)
        printer.registerChangeListener(() -> lastProgress.set(printer.getLineAt(1)));

        // Mock RepoManager so that addPath completes immediately with a non-null RepoFile
        RepoManager repoManager = mock(RepoManager.class);
        RepoFile returned = RepoFile.builder()
                .hash("h")
                .relativePath("a.txt")
                .size(1L)
                .lastModified(0L)
                .mimeType("text/plain")
                .build();
        when(repoManager.addPath(any(Path.class), any(FileHasher.class), any()))
                .thenReturn(CompletableFuture.completedFuture(Result.ok(returned)));

        // FileHasher is not used because addPath is mocked; provide a trivial stub
        FileHasher stubHasher = new FileHasher() {
            @Override
            public CompletableFuture<Result<String, DedupError>> hash(Path path) {
                return CompletableFuture.completedFuture(Result.ok("hash"));
            }

            @Override
            public void close() {
            }
        };

        Statistics stats = new Statistics("test");
        UpdateProgressPrinter upp = new UpdateProgressPrinter(
                Collections.emptyMap(),
                printer,
                repoManager,
                stats,
                stubHasher,
                clock
        );

        // Ensure we crossed the 30s threshold and mark scan as finished before the first file completes
        clock.tick(Duration.ofSeconds(31));
        upp.scanFinished();

        // Act: process a single file (total=1, processed=1 -> remaining=0)
        upp.file(Path.of("/tmp/a.txt"));

        // Assert: progress contains 100% and a zero remaining duration with ETA equal to clock time (00:00:31 at epoch UTC)
        String progressLine = lastProgress.get();
        assertThat(progressLine).startsWith("   Progress: ");
        assertThat(progressLine).contains("100.00 %");
        // Apache DurationFormatUtils#formatDurationWords(0, ...) yields "0 seconds"
        assertThat(progressLine).contains("estimated remaining duration: 0 seconds");
        // ETA should be exactly formatted with the injected clock time
        assertThat(progressLine).contains("ETA: 00:00:31 (01.01.1970)");
    }
}
