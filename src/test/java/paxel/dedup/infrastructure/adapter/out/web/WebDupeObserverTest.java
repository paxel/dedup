package paxel.dedup.infrastructure.adapter.out.web;

import org.junit.jupiter.api.Test;
import paxel.dedup.domain.service.EventBus;
import paxel.dedup.repo.domain.repo.DuplicateRepoProcess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WebDupeObserverTest {

    @Test
    void shouldReportUsingInjectedRepoName() {
        // Arrange
        EventBus eventBus = new EventBus();
        List<EventBus.DedupEvent> events = new ArrayList<>();
        eventBus.subscribe(events::add);

        AtomicReference<String> storedRepoName = new AtomicReference<>();
        AtomicReference<List<List<DuplicateRepoProcess.RepoRepoFile>>> storedGroups = new AtomicReference<>();

        WebDupeObserver observer = new WebDupeObserver("batch", eventBus, (name, groups) -> {
            storedRepoName.set(name);
            storedGroups.set(groups);
        });

        List<List<DuplicateRepoProcess.RepoRepoFile>> groups = List.of(List.of());

        // Act
        observer.onGroupsReady("repo1", groups);
        observer.onFinished("repo1", groups.size());

        // Assert
        // 1. Result is stored under "batch" (injected), not "repo1" (argument)
        assertThat(storedRepoName.get()).isEqualTo("batch");
        assertThat(storedGroups.get()).isSameAs(groups);

        // 2. Events are published with repo = "batch"
        assertThat(events).hasSize(2);

        EventBus.DedupEvent dupesFinishedEvent = events.get(0);
        assertThat(dupesFinishedEvent.type()).isEqualTo("dupes-finished");
        assertThat(dupesFinishedEvent.payload()).isInstanceOf(Map.class);
        Map<?, ?> dupesFinishedMap = (Map<?, ?>) dupesFinishedEvent.payload();
        assertThat(dupesFinishedMap.get("repo")).isEqualTo("batch");

        EventBus.DedupEvent dupeFinishedEvent = events.get(1);
        assertThat(dupeFinishedEvent.type()).isEqualTo("dupe-finished");
        assertThat(dupeFinishedEvent.payload()).isInstanceOf(Map.class);
        Map<?, ?> dupeFinishedMap = (Map<?, ?>) dupeFinishedEvent.payload();
        assertThat(dupeFinishedMap.get("repo")).isEqualTo("batch");
    }
}
