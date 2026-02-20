package paxel.dedup.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.domain.port.out.LineCodec;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.adapter.out.serialization.JacksonLineCodec;

public class InfrastructureConfig {
    @Getter
    private final FileSystem fileSystem;
    @Getter
    private final DedupConfig dedupConfig;
    @Getter
    private final ObjectMapper objectMapper;
    @Getter
    private final LineCodec<RepoFile> repoFileCodec;

    public InfrastructureConfig() {
        this.fileSystem = new NioFileSystemAdapter();
        this.objectMapper = new ObjectMapper();
        this.repoFileCodec = new JacksonLineCodec<>(this.objectMapper, RepoFile.class);
        this.dedupConfig = DedupConfigFactory.create(this.fileSystem).value();
    }

    // You can add more factory methods here if you want to provide fully wired processes
}
