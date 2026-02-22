package paxel.dedup.infrastructure.adapter.out.serialization;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.port.out.LineCodec;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Reflection-free MessagePack codec specialized for RepoFile.
 * Uses a small map with compact keys: h,p,s,l,d,m
 */
public class MessagePackRepoFileCodec implements LineCodec<RepoFile> {

    @Override
    public RepoFile decode(ByteBuffer bytes) throws IOException {
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

        String h = null;
        String p = null;
        Long s = null;
        long l = 0L;
        boolean d = false;
        String m = null;
        String f = null;

        try (MessageUnpacker un = MessagePack.newDefaultUnpacker(arr)) {
            int mapSize = un.unpackMapHeader();
            for (int i = 0; i < mapSize; i++) {
                String key = un.unpackString();
                switch (key) {
                    case "h" -> h = un.unpackString();
                    case "p" -> p = unpackNullableStringLocal(un);
                    case "s" -> {
                        if (un.tryUnpackNil()) s = null;
                        else s = un.unpackLong();
                    }
                    case "l" -> l = un.unpackLong();
                    case "d" -> d = un.unpackBoolean();
                    case "m" -> m = unpackNullableStringLocal(un);
                    case "f" -> f = unpackNullableStringLocal(un);
                    default -> un.skipValue();
                }
            }
        }
        return new RepoFile(h, p, s, l, d, m, f);
    }

    @Override
    public ByteBuffer encode(RepoFile value) throws IOException {
        byte[] out;
        try (var outStream = new java.io.ByteArrayOutputStream();
             MessagePacker pk = MessagePack.newDefaultPacker(outStream)) {
            int fields = 5;
            if (value.mimeType() != null && !value.mimeType().isEmpty()) {
                fields = fields + 1;
            }
            if (value.fingerprint() != null && !value.fingerprint().isEmpty()) {
                fields = fields + 1;
            }
            pk.packMapHeader(fields);
            pk.packString("h");
            pk.packString(value.hash());
            pk.packString("p");
            if (value.relativePath() == null) pk.packNil();
            else pk.packString(value.relativePath());
            pk.packString("s");
            if (value.size() == null) pk.packNil();
            else pk.packLong(value.size());
            pk.packString("l");
            pk.packLong(value.lastModified());
            pk.packString("d");
            pk.packBoolean(value.missing());
            if (value.mimeType() != null && !value.mimeType().isEmpty()) {
                pk.packString("m");
                pk.packString(value.mimeType());
            }
            if (value.fingerprint() != null && !value.fingerprint().isEmpty()) {
                pk.packString("f");
                pk.packString(value.fingerprint());
            }
            pk.flush();
            out = outStream.toByteArray();
        }
        return ByteBuffer.wrap(out);
    }

    private String unpackNullableStringLocal(MessageUnpacker un) throws IOException {
        if (un.tryUnpackNil()) {
            return null;
        }
        return un.unpackString();
    }
}
