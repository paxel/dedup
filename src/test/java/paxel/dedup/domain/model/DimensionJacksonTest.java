package paxel.dedup.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DimensionJacksonTest {

    @Test
    void shouldSerializeAndDeserializeCorrectly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Dimension original = new Dimension(1920, 1080);

        String json = mapper.writeValueAsString(original);
        // Expecting object notation since we removed @JsonValue
        assertThat(json).contains("\"width\":1920").contains("\"height\":1080");

        Dimension deserialized = mapper.readValue(json, Dimension.class);
        assertThat(deserialized).isEqualTo(original);
    }
}
