package paxel.dedup.infrastructure.adapter.out.serialization;

import paxel.dedup.domain.model.Repo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FrameIteratorFactoryFactory {
    public Function<InputStream, FrameIterator> forReader(Repo.Codec codec, boolean compressed) {
        return stream -> {
            try {
                if (compressed) {
                    if (!stream.markSupported()) {
                        stream = new java.io.BufferedInputStream(stream);
                    }
                    stream.mark(1);
                    int read = stream.read();
                    if (read == -1) {
                        return new EmptyFrameIterator();
                    }
                    stream.reset();
                }
                InputStream wrapped = compressed ? new GZIPInputStream(stream) : stream;
                return switch (codec) {
                    case JSON -> new JsonFrameIterator(wrapped);
                    case MESSAGEPACK -> new MsgPackFrameIterator(wrapped);
                };
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize compressed stream", e);
            }
        };
    }

    public Function<OutputStream, FrameWriter> forWriter(Repo.Codec codec, boolean compressed) {
        return stream -> {
            try {
                OutputStream wrapped = compressed ? new GZIPOutputStream(stream) : stream;
                return switch (codec) {
                    case JSON -> new JsonFrameWriter(wrapped);
                    case MESSAGEPACK -> new MsgPackFrameWriter(wrapped);
                };
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize compressed stream", e);
            }
        };
    }
}
