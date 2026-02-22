package paxel.dedup.infrastructure.adapter.out.serialization;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class JsonFrameIterator implements FrameIterator {

    private final Iterator<String> lines;
    private final InputStream stream;

    public JsonFrameIterator(InputStream stream) {
        lines = new BufferedReader(new InputStreamReader(stream)).lines().iterator();
        this.stream = stream;
    }

    @Override
    public boolean hasNext() {
        return lines.hasNext();
    }

    @Override
    public ByteBuffer next() {
        return ByteBuffer.wrap(lines.next().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
