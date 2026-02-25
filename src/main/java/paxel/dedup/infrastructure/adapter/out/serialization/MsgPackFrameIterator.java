package paxel.dedup.infrastructure.adapter.out.serialization;

import lombok.SneakyThrows;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

public class MsgPackFrameIterator implements FrameIterator {


    private final DataInputStream in;
    private boolean eof = false;

    @SneakyThrows
    public MsgPackFrameIterator(InputStream inputStream) {
        this.in = new DataInputStream(inputStream.markSupported() ? inputStream : new java.io.BufferedInputStream(inputStream));
    }

    @Override
    public boolean hasNext() {
        if (eof) return false;
        try {
            in.mark(1);
            int read = in.read();
            if (read == -1) {
                eof = true;
                return false;
            }
            in.reset();
            return true;
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
