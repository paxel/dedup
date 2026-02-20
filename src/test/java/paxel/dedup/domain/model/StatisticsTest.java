package paxel.dedup.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.time.temporal.ChronoUnit;

class StatisticsTest {

    @Test
    void testIncAndSet() {
        Statistics stats = new Statistics("test");
        stats.inc("count");
        stats.inc("count");
        stats.set("total", 100);

        AtomicLong countVal = new AtomicLong();
        stats.forCounter((k, v) -> {
            if (k.equals("count")) countVal.set(v);
        });
        assertThat(countVal.get()).isEqualTo(2);

        AtomicLong totalVal = new AtomicLong();
        stats.forCounter((k, v) -> {
            if (k.equals("total")) totalVal.set(v);
        });
        assertThat(totalVal.get()).isEqualTo(100);
    }

    @Test
    void testTimer() throws InterruptedException {
        Statistics stats = new Statistics("test");
        stats.start("op");
        Thread.sleep(10);
        stats.stop("op");

        stats.forTimer((k, v) -> {
            if (k.equals("op")) {
                assertThat(v).isCloseTo(Duration.ofMillis(10), Duration.ofMillis(50));
            }
        });
    }

    @Test
    void testAddStatistics() {
        Statistics stats1 = new Statistics("s1");
        stats1.inc("c1");
        stats1.start("t1");
        stats1.stop("t1");

        Statistics stats2 = new Statistics("s2");
        stats2.inc("c1");
        stats2.inc("c2");

        stats1.add(stats2);

        AtomicLong c1Val = new AtomicLong();
        stats1.forCounter((k, v) -> {
            if (k.equals("c1")) c1Val.set(v);
        });
        assertThat(c1Val.get()).isEqualTo(2);
    }
}
