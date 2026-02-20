package paxel.dedup.domain.port.out;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Binary record serializer/deserializer abstraction.
 * Implementations convert between a domain object and a binary representation.
 */
public interface LineCodec<T> {
    T decode(ByteBuffer bytes) throws IOException;
    ByteBuffer encode(T value) throws IOException;
}
