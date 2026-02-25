package paxel.dedup.infrastructure.adapter.out.serialization;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

public interface FrameIterator extends Iterator<ByteBuffer>, AutoCloseable {


    void close() throws IOException;
}
