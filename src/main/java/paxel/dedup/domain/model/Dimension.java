package paxel.dedup.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class Dimension {
    int width;
    int height;

    @JsonCreator
    public Dimension(
            @JsonProperty("width") int width,
            @JsonProperty("height") int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }

    public long area() {
        return (long) width * (long) height;
    }
}
