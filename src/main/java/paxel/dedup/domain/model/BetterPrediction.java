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

    public BetterPrediction() {
        this(Clock.systemUTC());
    }

    public BetterPrediction(Clock clock) {
        this.clock = clock;
    }

    public void trigger() {
        lastInstants.add(clock.instant());
        if (lastInstants.size() == COUNT + 1)
            lastInstants.poll();
    }

    public Duration get() {
        Instant peek = lastInstants.peek();
        if (peek == null || lastInstants.size() < COUNT)
            return null;
        // Use current clock instant; callers may prefer exact last trigger span,
        // but for compatibility we keep measuring up to "now".
        return Duration.between(peek, clock.instant());
    }
}
