package paxel.dedup.infrastructure.adapter.out.serialization;

import paxel.dedup.domain.model.Repo;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

public class FrameIteratorFactoryFactory {
    public Function<InputStream, FrameIterator> forReader(Repo.Codec codec) {
        return switch (codec) {
            case JSON -> JsonFrameIterator::new;
            case MESSAGEPACK -> MsgPackFrameIterator::new;
        };
    }

    public Function<OutputStream, FrameWriter> forWriter(Repo.Codec codec) {
        return switch (codec) {
            case JSON -> JsonFrameWriter::new;
            case MESSAGEPACK -> MsgPackFrameWriter::new;
        };
    }
}
