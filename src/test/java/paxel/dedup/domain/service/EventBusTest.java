package paxel.dedup.domain.service;

import org.junit.jupiter.api.Test;
import paxel.dedup.domain.model.ProgressUpdate;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventBusTest {

    @Test
    void shouldStoreLastEventPerRepo() {
        EventBus eventBus = new EventBus();

        eventBus.publish("progress", ProgressUpdate.builder().repo("repo1").filesProcessed(10L).build());
        eventBus.publish("progress", ProgressUpdate.builder().repo("repo1").filesProcessed(20L).build());
        eventBus.publish("progress", ProgressUpdate.builder().repo("repo2").filesProcessed(5L).build());

        Collection<EventBus.DedupEvent> lastEvents = eventBus.getLastEvents();
        assertThat(lastEvents).hasSize(2);

        assertThat(lastEvents).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("progress");
            assertThat(((ProgressUpdate) event.payload()).getRepo()).isEqualTo("repo1");
            assertThat(((ProgressUpdate) event.payload()).getFilesProcessed()).isEqualTo(20L);
        });
    }

    @Test
    void shouldRemoveStateOnFinished() {
        EventBus eventBus = new EventBus();

        eventBus.publish("progress", ProgressUpdate.builder().repo("repo1").build());
        assertThat(eventBus.getLastEvents()).hasSize(1);

        eventBus.publish("finished", Map.of("repo", "repo1"));
        assertThat(eventBus.getLastEvents()).isEmpty();
    }

    @Test
    void shouldHandleMapPayloads() {
        EventBus eventBus = new EventBus();

        eventBus.publish("dupe-start", Map.of("repo", "repo1", "all", true));

        Collection<EventBus.DedupEvent> lastEvents = eventBus.getLastEvents();
        assertThat(lastEvents).hasSize(1);
        assertThat(lastEvents.iterator().next().type()).isEqualTo("dupe-start");
    }
}
