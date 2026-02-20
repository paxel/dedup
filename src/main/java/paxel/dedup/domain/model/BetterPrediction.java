package paxel.dedup.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class BetterPrediction {

    public static final int COUNT = 1000;
    private final Queue<Instant> lastInstants = new ArrayBlockingQueue<>(COUNT + 1);

    public void trigger() {
        lastInstants.add(Instant.now());
        if (lastInstants.size() == COUNT + 1)
            lastInstants.poll();
    }

    public Duration get() {
        Instant peek = lastInstants.peek();
        if (peek == null || lastInstants.size() < COUNT)
            return null;
        return Duration.between(peek, Instant.now());
    }
}
