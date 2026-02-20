package paxel.dedup.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import static org.assertj.core.api.Assertions.assertThat;

class BetterPredictionTest {

    @Test
    void testGetReturnsNullWhenNotEnoughTriggers() {
        BetterPrediction prediction = new BetterPrediction();
        for (int i = 0; i < BetterPrediction.COUNT - 1; i++) {
            prediction.trigger();
        }
        assertThat(prediction.get()).isNull();
    }

    @Test
    void testGetReturnsDurationAfterEnoughTriggers() {
        BetterPrediction prediction = new BetterPrediction();
        for (int i = 0; i < BetterPrediction.COUNT; i++) {
            prediction.trigger();
        }
        Duration duration = prediction.get();
        // Meaningful assertions: once enough triggers happened, we should get a non-negative
        // duration that reflects a short window (tight loop), hence should also be reasonably bounded
        assertThat(duration).isNotNull();
        assertThat(duration.isNegative()).isFalse();
        assertThat(duration).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void testTriggerRollingWindow() {
        BetterPrediction prediction = new BetterPrediction();
        for (int i = 0; i < BetterPrediction.COUNT + 10; i++) {
            prediction.trigger();
        }
        Duration duration = prediction.get();
        // Meaningful assertions: with COUNT+10 triggers, the rolling window is full and we
        // must get a duration that is non-negative and not exceedingly large for a tight loop
        assertThat(duration).isNotNull();
        assertThat(duration.isNegative()).isFalse();
        assertThat(duration).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void testDeterministicDurationWithClock() {
        // Custom mutable clock for deterministic testing
        class MutableClock extends Clock {
            private Instant current;
            private final ZoneId zone;

            MutableClock(Instant start, ZoneId zone) {
                this.current = start;
                this.zone = zone;
            }

            void tick(Duration d) { this.current = this.current.plus(d); }

            @Override public ZoneId getZone() { return zone; }
            @Override public Clock withZone(ZoneId zone) { return new MutableClock(current, zone); }
            @Override public Instant instant() { return current; }
        }

        MutableClock clock = new MutableClock(Instant.EPOCH, ZoneId.of("UTC"));
        BetterPrediction prediction = new BetterPrediction(clock);

        // Advance time by 1 second per trigger deterministically
        Duration step = Duration.ofSeconds(1);
        for (int i = 0; i < BetterPrediction.COUNT; i++) {
            clock.tick(step);
            prediction.trigger();
        }

        Duration duration = prediction.get();
        // With COUNT timestamps spaced by 1s, and duration measured to "now",
        // expected span is (COUNT - 1) * step
        Duration expected = step.multipliedBy(BetterPrediction.COUNT - 1L);
        assertThat(duration).isNotNull();
        assertThat(duration).isEqualTo(expected);
    }
}
