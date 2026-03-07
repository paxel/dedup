package paxel.dedup.infrastructure.adapter.out.serialization;

import paxel.dedup.domain.model.Repo;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

public class FrameIteratorFactoryFactory {
    public Function<InputStream, FrameIterator> forReader(Repo.Codec codec) {
        return stream -> {
            return switch (codec) {
                case JSON -> new JsonFrameIterator(stream);
                case MESSAGEPACK -> new MsgPackFrameIterator(stream);
            };
        };
    }

    public Function<OutputStream, FrameWriter> forWriter(Repo.Codec codec) {
        return stream -> {
            return switch (codec) {
                case JSON -> new JsonFrameWriter(stream);
                case MESSAGEPACK -> new MsgPackFrameWriter(stream);
            };
        };
    }
}
