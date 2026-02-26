package paxel.dedup.infrastructure.adapter.out.serialization;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

public class EmptyFrameIterator implements FrameIterator {
    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public ByteBuffer next() {
        throw new NoSuchElementException();
    }

    @Override
    public void close() throws IOException {
        // Nothing to close
    }
}
