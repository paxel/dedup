package io.github.paxel.dedup.comparison;

public class HexFormat {

    public String asString(byte[] data) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte datum : data) {
            stringBuilder.append(String.format("%02x", datum & 0xff));
        }
        return stringBuilder.toString();
    }
}
