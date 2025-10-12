package paxel.dedup.repo.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class BetterPrediction {
    private final Queue<Instant> lastInstants = new ArrayBlockingQueue<>(11);

    public void trigger() {
        lastInstants.add(Instant.now());
        if (lastInstants.size() == 11)
            lastInstants.poll();
    }

    public Duration get() {
        Instant peek = lastInstants.peek();
        if (peek == null || lastInstants.size() < 10)
            return null;
        return Duration.between(peek, Instant.now());
    }
}
