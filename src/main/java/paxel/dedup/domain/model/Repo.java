package paxel.dedup.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.With;


@Builder
@With
public record Repo(
        String name,
        String absolutePath,
        int indices,
        Codec codec,
        boolean compressed
) {

    public enum Codec {JSON, MESSAGEPACK}

    @JsonCreator
    public static Repo create(@JsonProperty("name") String name,
                              @JsonProperty("absolutePath") String absolutePath,
                              @JsonProperty("indices") int indices,
                              @JsonProperty("codec") Codec codec,
                              @JsonProperty("compressed") Boolean compressed) {
        return new Repo(name, absolutePath, indices, codec != null ? codec : Codec.JSON, compressed != null ? compressed : false);
    }

    // Backward-compatible convenience constructor used in code/tests
    public Repo(String name, String absolutePath, int indices) {
        this(name, absolutePath, indices, Codec.JSON, false);
    }

    @Override
    public String toString() {
        return name + ": " + absolutePath;
    }
}
