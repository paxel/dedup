package paxel.dedup.infrastructure.adapter.out.serialization;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class JsonFrameWriter implements FrameWriter {
    private final OutputStream out;

    public JsonFrameWriter(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(ByteBuffer frame) throws IOException {
        byte[] arr;
        if (frame.hasArray()) {
            arr = frame.array();
            int offset = frame.arrayOffset() + frame.position();
            int len = frame.remaining();
            out.write(arr, offset, len);
        } else {
            arr = new byte[frame.remaining()];
            frame.duplicate().get(arr);
            out.write(arr);
        }
        out.write('\n');
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
