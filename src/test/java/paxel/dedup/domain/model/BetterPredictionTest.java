package paxel.dedup.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Duration;
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
        assertThat(duration).isNotNull();
        assertThat(duration.isNegative()).isFalse();
    }

    @Test
    void testTriggerRollingWindow() {
        BetterPrediction prediction = new BetterPrediction();
        for (int i = 0; i < BetterPrediction.COUNT + 10; i++) {
            prediction.trigger();
        }
        assertThat(prediction.get()).isNotNull();
    }
}
