package paxel.dedup.domain.model;

public interface BinaryFormatter {
    String format(byte[] hashBytes);
}
