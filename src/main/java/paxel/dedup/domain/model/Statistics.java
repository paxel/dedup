package paxel.dedup.domain.model;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
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

    public long inc(String key) {
        return counter.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
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
        counter.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> consumer.accept(e.getKey(), e.getValue().get()));
    }

    public void forTimer(BiConsumer<String, Duration> consumer) {
        timer.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> consumer.accept(e.getKey(), e.getValue()));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" {");
        boolean first = true;
        for (var entry : counter.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append("=").append(entry.getValue().get());
            first = false;
        }
        for (var entry : timer.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append("=").append(formatDuration(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String formatDuration(Duration d) {
        long totalSeconds = d.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long millis = d.toMillisPart();
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        }
        if (seconds > 0) {
            return String.format("%d.%03ds", seconds, millis);
        }
        return String.format("%dms", d.toMillis());
    }

    public void add(Statistics value) {
        value.forCounter((key, newCount) -> counter.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(newCount));
        value.forTimer((key, newDuration) -> timer.compute(key, (k, prevDuration) -> {
            if (prevDuration == null)
                return newDuration;
            return prevDuration.plus(newDuration);
        }));
    }
}
