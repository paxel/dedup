package paxel.dedup.infrastructure.adapter.out.serialization;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class MsgPackFrameWriter implements FrameWriter {
    private final DataOutputStream out;

    public MsgPackFrameWriter(OutputStream out) {
        this.out = new DataOutputStream(out);
    }

    @Override
    public void write(ByteBuffer frame) throws IOException {
        int len = frame.remaining();
        if (len > 65535) {
            throw new IOException("Frame too large for short length: " + len);
        }
        out.writeShort(len);
        byte[] arr;
        if (frame.hasArray()) {
            arr = frame.array();
            int offset = frame.arrayOffset() + frame.position();
            out.write(arr, offset, len);
        } else {
            arr = new byte[len];
            frame.duplicate().get(arr);
            out.write(arr);
        }
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
