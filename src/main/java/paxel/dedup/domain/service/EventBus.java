package paxel.dedup.domain.service;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Slf4j
public class EventBus {

    public record DedupEvent(String type, Object payload) {
    }

    private final List<Consumer<DedupEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<DedupEvent> listener) {
        listeners.add(listener);
    }

    public void publish(String type, Object payload) {
        DedupEvent event = new DedupEvent(type, payload);
        listeners.forEach(listener -> {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error notifying event listener", e);
            }
        });
    }
}
