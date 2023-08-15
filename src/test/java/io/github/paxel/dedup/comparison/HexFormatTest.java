package io.github.paxel.dedup.comparison;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

class HexFormatTest {

    @Test
    @DisplayName("all zeroes")
    void format() {
        String s = new HexFormat().asString(new byte[5]);
        assertThat(s, is("0000000000"));
    }
    @Test
    @DisplayName("case is lower")
    void format2() {
        String s = new HexFormat().asString(new byte[]{(byte) 0xff,0x34, (byte) 0xca, (byte) 0xfe});
        assertThat(s, is("ff34cafe"));
    }

}