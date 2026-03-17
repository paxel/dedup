package paxel.dedup.domain.service;

import lombok.extern.slf4j.Slf4j;
import paxel.dedup.domain.model.ProgressUpdate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Slf4j
public class EventBus {

    public record DedupEvent(String type, Object payload) {
    }

    private final List<Consumer<DedupEvent>> listeners = new CopyOnWriteArrayList<>();

    // Stores the last state per repository and event type
    private final Map<String, Map<String, DedupEvent>> lastStates = new ConcurrentHashMap<>();

    public void subscribe(Consumer<DedupEvent> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<DedupEvent> listener) {
        listeners.remove(listener);
    }

    public void publish(String type, Object payload) {
        DedupEvent event = new DedupEvent(type, payload);
        updateLastState(type, payload, event);
        listeners.forEach(listener -> {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error notifying event listener", e);
            }
        });
    }

    private void updateLastState(String type, Object payload, DedupEvent event) {
        String repo = null;
        if (payload instanceof ProgressUpdate pu) {
            repo = pu.getRepo();
        } else if (payload instanceof Map<?, ?> map) {
            Object repoObj = map.get("repo");
            if (repoObj instanceof String s) {
                repo = s;
            }
        }

        if (repo != null) {
            if (type.equals("finished") || type.equals("dupes-finished") || type.equals("error")) {
                lastStates.remove(repo);
            } else {
                lastStates.computeIfAbsent(repo, k -> new ConcurrentHashMap<>()).put(type, event);
            }
        }
    }

    public Collection<DedupEvent> getLastEvents() {
        return lastStates.values().stream()
                .flatMap(map -> map.values().stream())
                .toList();
    }
}
