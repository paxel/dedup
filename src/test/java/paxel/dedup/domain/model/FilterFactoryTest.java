package paxel.dedup.domain.model;

import org.junit.jupiter.api.Test;
import java.util.function.Predicate;
import static org.assertj.core.api.Assertions.assertThat;

class FilterFactoryTest {

    private final FilterFactory factory = new FilterFactory();

    @Test
    void testBlankFilter() {
        Predicate<RepoFile> filter = factory.createFilter("");
        RepoFile file = RepoFile.builder().relativePath("test.txt").mimeType("text/plain").build();
        assertThat(filter.test(file)).isTrue();
    }

    @Test
    void testMimeFilter() {
        Predicate<RepoFile> filter = factory.createFilter("mime:text");
        RepoFile file1 = RepoFile.builder().mimeType("text/plain").build();
        RepoFile file2 = RepoFile.builder().mimeType("image/png").build();
        RepoFile file3 = RepoFile.builder().mimeType(null).build();

        assertThat(filter.test(file1)).isTrue();
        assertThat(filter.test(file2)).isFalse();
        assertThat(filter.test(file3)).isFalse();
    }

    @Test
    void testNameFilter() {
        Predicate<RepoFile> filter = factory.createFilter("name:test");
        RepoFile file1 = RepoFile.builder().relativePath("mytest.txt").build();
        RepoFile file2 = RepoFile.builder().relativePath("other.txt").build();
        RepoFile file3 = RepoFile.builder().relativePath(null).build();

        assertThat(filter.test(file1)).isTrue();
        assertThat(filter.test(file2)).isFalse();
        assertThat(filter.test(file3)).isFalse();
    }

    @Test
    void testUnknownFilter() {
        Predicate<RepoFile> filter = factory.createFilter("unknown:something");
        RepoFile file = RepoFile.builder().relativePath("test.txt").build();
        assertThat(filter.test(file)).isFalse();
    }
}
