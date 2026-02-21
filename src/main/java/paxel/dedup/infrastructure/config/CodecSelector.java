package paxel.dedup.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.port.out.LineCodec;
import paxel.dedup.infrastructure.adapter.out.serialization.JsonLineCodec;
import paxel.dedup.infrastructure.adapter.out.serialization.MessagePackRepoFileCodec;

/**
 * Chooses the per-repo LineCodec based on an optional settings file next to {@code dedup_repo.yml}.
 * If no settings file is present or codec is unknown, defaults to Jackson codec.
 */
@RequiredArgsConstructor
public class CodecSelector {
    private final DedupConfig dedupConfig;

    public LineCodec<RepoFile> forRepo(Repo repo) {
        Repo.Codec codec = repo.codec();
        if (codec == Repo.Codec.MESSAGEPACK) {
            try {
                return new MessagePackRepoFileCodec();
            } catch (Throwable t) {
                // Fallback if implementation is not available at runtime
                System.err.println("MessagePack codec unavailable, falling back to JSON for repo '" + repo.name() + "'");
            }
        }
        // default and fallback
        return new JsonLineCodec<>(new ObjectMapper(), RepoFile.class);
    }
}
