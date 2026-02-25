package paxel.dedup.domain.model;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class BetterPrediction {

    public static final int COUNT = 1000;
    private final Queue<Instant> lastInstants = new ArrayBlockingQueue<>(COUNT + 1);
    private final Clock clock;
    private Instant lastTriggerInstant;

    public BetterPrediction() {
        this(Clock.systemUTC());
    }

    public BetterPrediction(Clock clock) {
        this.clock = clock;
    }

    public void trigger() {
        lastTriggerInstant = clock.instant();
        lastInstants.add(lastTriggerInstant);
        if (lastInstants.size() == COUNT + 1)
            lastInstants.poll();
    }

    public Duration get() {
        Instant first = lastInstants.peek();
        if (first == null || lastInstants.size() < 2)
            return null;

        // Use duration between first and last recorded trigger in the window.
        // This gives the time taken for (lastInstants.size() - 1) operations.
        return Duration.between(first, lastTriggerInstant);
    }

    public int getCount() {
        if (lastInstants.isEmpty()) return 0;
        return lastInstants.size() - 1;
    }
}
