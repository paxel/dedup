package paxel.dedup.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HexFormatterTest {

    private final HexFormatter formatter = new HexFormatter();

    @Test
    void testFormat() {
        byte[] data = new byte[]{0x00, 0x01, 0x0f, 0x10, (byte) 0xff};
        assertThat(formatter.format(data)).isEqualTo("00010f10ff");
    }

    @Test
    void testEmptyArray() {
        assertThat(formatter.format(new byte[0])).isEmpty();
    }
}
