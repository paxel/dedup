package paxel.dedup.infrastructure.adapter.out.serialization;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface FrameWriter extends AutoCloseable {
    void write(ByteBuffer frame) throws IOException;

    @Override
    void close() throws IOException;
}
