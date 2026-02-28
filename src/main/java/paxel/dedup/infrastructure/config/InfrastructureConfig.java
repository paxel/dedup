package paxel.dedup.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.domain.service.EventBus;
import paxel.dedup.domain.service.RepoService;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;

public class InfrastructureConfig {
    @Getter
    private final FileSystem fileSystem;
    @Getter
    private final DedupConfig dedupConfig;
    @Getter
    private final ObjectMapper objectMapper;
    @Getter
    private final RepoService repoService;
    @Getter
    private final EventBus eventBus;

    public InfrastructureConfig() {
        this.fileSystem = new NioFileSystemAdapter();
        this.objectMapper = new ObjectMapper();
        this.dedupConfig = DedupConfigFactory.create(this.fileSystem).value();
        this.repoService = new RepoService(this.dedupConfig, this.fileSystem);
        this.eventBus = new EventBus();
    }

    // You can add more factory methods here if you want to provide fully wired processes
}
