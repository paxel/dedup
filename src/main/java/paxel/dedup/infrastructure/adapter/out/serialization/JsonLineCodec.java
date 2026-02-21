package paxel.dedup.infrastructure.adapter.out.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import paxel.dedup.domain.port.out.LineCodec;

import java.io.IOException;
import java.nio.ByteBuffer;

public class JsonLineCodec<T> implements LineCodec<T> {
    private final ObjectMapper objectMapper;
    private final Class<T> type;

    public JsonLineCodec(ObjectMapper objectMapper, Class<T> type) {
        this.objectMapper = objectMapper;
        this.type = type;
    }

    @Override
    public T decode(ByteBuffer bytes) throws IOException {
        byte[] arr;
        if (bytes.hasArray()) {
            int offset = bytes.arrayOffset() + bytes.position();
            int len = bytes.remaining();
            arr = new byte[len];
            System.arraycopy(bytes.array(), offset, arr, 0, len);
        } else {
            arr = new byte[bytes.remaining()];
            bytes.duplicate().get(arr);
        }
        return objectMapper.readValue(arr, type);
    }

    @Override
    public ByteBuffer encode(T value) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(value);
        return ByteBuffer.wrap(bytes);
    }
}
