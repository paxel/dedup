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
        Codec codec
) {

    public enum Codec {JSON, MESSAGEPACK}

    @JsonCreator
    public Repo(@JsonProperty("name") String name,
                @JsonProperty("absolutePath") String absolutePath,
                @JsonProperty("indices") int indices,
                @JsonProperty("codec") Codec codec) {
        this.name = name;
        this.absolutePath = absolutePath;
        this.indices = indices;
        // Backward compatibility: default to JSON when field missing/null
        this.codec = codec != null ? codec : Codec.JSON;
    }

    // Backward-compatible convenience constructor used in code/tests
    public Repo(String name, String absolutePath, int indices) {
        this(name, absolutePath, indices, Codec.JSON);
    }

    @Override
    public String toString() {
        return name + ": " + absolutePath;
    }
}
