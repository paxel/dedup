package paxel.dedup.domain.model;

import lombok.Value;

@Value
public class Dimension {
    int width;
    int height;

    @Override
    public String toString() {
        return width + "x" + height;
    }

    public long area() {
        return (long) width * (long) height;
    }
}
