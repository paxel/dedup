package paxel.dedup.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;

public class InfrastructureConfig {
    @Getter
    private final FileSystem fileSystem;
    @Getter
    private final DedupConfig dedupConfig;
    @Getter
    private final ObjectMapper objectMapper;

    public InfrastructureConfig() {
        this.fileSystem = new NioFileSystemAdapter();
        this.objectMapper = new ObjectMapper();
        this.dedupConfig = DedupConfigFactory.create(this.fileSystem).value();
    }

    // You can add more factory methods here if you want to provide fully wired processes
}
