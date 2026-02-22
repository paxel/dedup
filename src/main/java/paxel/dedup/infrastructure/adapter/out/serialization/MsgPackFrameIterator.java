package paxel.dedup.infrastructure.adapter.out.serialization;

import lombok.SneakyThrows;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

public class MsgPackFrameIterator implements FrameIterator {


    private final DataInputStream in;

    @SneakyThrows
    public MsgPackFrameIterator(InputStream inputStream) {
        this.in = new DataInputStream(inputStream);
    }

    @Override
    public boolean hasNext() {
        try {
            return in.available() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public ByteBuffer next() {
        try {
            short i = in.readShort();
            int size = Short.toUnsignedInt(i);
            return ByteBuffer.wrap(in.readNBytes(size));
        } catch (IOException e) {
            throw new NoSuchElementException(e);
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
