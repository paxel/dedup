package paxel.dedup.repo.index;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public class Statistics {

    @Getter
    private final String name;
    private final Map<String, AtomicLong> counter = new ConcurrentHashMap<>();
    private final Map<String, Instant> start = new ConcurrentHashMap<>();
    private final Map<String, Duration> timer = new ConcurrentHashMap<>();

    public Statistics(String name) {
        this.name = name;
    }

    public void inc(String key) {
        counter.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    public void set(String key, long value) {
        counter.computeIfAbsent(key, k -> new AtomicLong()).set(value);
    }

    public void start(String key) {
        start.put(key, Instant.now());
    }

    public void stop(String key) {
        Instant begin = start.remove(key);
        if (begin != null) {
            timer.compute(key,
                    // calculate duration from old and new
                    (k, previous) -> {
                        Duration newDuration = Duration.between(begin, Instant.now());
                        if (previous == null) {
                            return newDuration;
                        }
                        // add new to previous
                        return previous.plus(newDuration);
                    });
        }
    }

    public void forCounter(BiConsumer<String, Long> consumer) {
        counter.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(e -> consumer.accept(e.getKey(), e.getValue().get()));
    }

    public void forTimer(BiConsumer<String, Duration> consumer) {
        timer.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(e -> consumer.accept(e.getKey(), e.getValue()));
    }
}
