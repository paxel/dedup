package paxel.dedup.repo.domain.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.domain.model.*;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.domain.port.out.LineCodec;
import paxel.dedup.infrastructure.adapter.out.serialization.FrameIteratorFactoryFactory;
import paxel.dedup.infrastructure.adapter.out.serialization.JacksonMapperLineCodec;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
public class RepoManager {
    @Getter
    private final Repo repo;
    private final Map<Integer, IndexManager> indices = new ConcurrentHashMap<>();
    private final LineCodec<RepoFile> lineCodec;
    private final FileSystem fileSystem;
    @Getter
    private final Path repoDir;
    private final BinaryFormatter binaryFormatter = new HexFormatter();


    public RepoManager(Repo repo, DedupConfig dedupConfig, LineCodec<RepoFile> lineCodec, FileSystem fileSystem) {
        this.repo = repo;
        this.fileSystem = fileSystem;
        this.lineCodec = lineCodec;
        repoDir = dedupConfig.getRepoDir().resolve(repo.name());
    }

    /**
     * Factory: open a repo and select its LineCodec once based on {@link Repo#codec()}.
     * Falls back to JSON if MessagePack implementation is unavailable at runtime.
     */
    public static RepoManager forRepo(Repo repo, DedupConfig dedupConfig, FileSystem fileSystem) {
        LineCodec<RepoFile> codec;
        if (repo.codec() == Repo.Codec.MESSAGEPACK) {
            try {
                ObjectMapper mp = new ObjectMapper(new org.msgpack.jackson.dataformat.MessagePackFactory());
                codec = new JacksonMapperLineCodec<>(mp, RepoFile.class);
            } catch (Throwable t) {
                log.warn("MessagePack codec unavailable, falling back to JSON for repo '{}'", repo.name(), t);
                codec = new JacksonMapperLineCodec<>(new ObjectMapper(), RepoFile.class);
            }
        } else {
            codec = new JacksonMapperLineCodec<>(new ObjectMapper(), RepoFile.class);
        }
        return new RepoManager(repo, dedupConfig, codec, fileSystem);
    }

    private static String nameIndexFile(int index) {
        return index + ".idx";
    }

    public Result<Statistics, DedupError> load() {
        for (IndexManager index : indices.values()) {
            Result<Boolean, DedupError> close = index.close();
            if (close.hasFailed()) {
                return close.mapError(f -> DedupError.of(ErrorType.LOAD, "Failed closing the IndexManager: " + f.describe(),
                        f.exception()));
            }
        }
        indices.clear();
        Statistics sum = new Statistics(repoDir.toString());

        for (int index = 0; index < repo.indices(); index++) {
            Path indexPath = repoDir.resolve(nameIndexFile(index));
            // Ensure index file exists for a freshly initialized repo
            try {
                if (!fileSystem.exists(indexPath)) {
                    // Ensure parent directory exists (defensive)
                    fileSystem.createDirectories(indexPath.getParent());
                    // Create an empty index file
                    fileSystem.write(indexPath, new byte[0], StandardOpenOption.CREATE);
                }
            } catch (IOException e) {
                return Result.err(DedupError.of(ErrorType.LOAD, indexPath + ": Could not initialize index file", e));
            }

            FrameIteratorFactoryFactory ffff = new FrameIteratorFactoryFactory();
            IndexManager indexManager = new IndexManager(indexPath, lineCodec, fileSystem, ffff.forReader(repo.codec()), ffff.forWriter(repo.codec()));
            Result<Statistics, DedupError> load = indexManager.load();
            if (load.hasFailed()) {
                return load;
            }
            sum.add(load.value());
            indices.put(index, indexManager);
        }

        return Result.ok(sum);
    }

    public Stream<RepoFile> stream() {
        return indices.values().stream().flatMap(IndexManager::stream);
    }

    public List<RepoFile> getByHash(String hash) {
        return indices.values().stream()
                .map(i -> i.getByHash(hash))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .toList();

    }

    public List<RepoFile> getByHashAndSize(String hash, Long size) {
        return indices.values().stream()
                .map(i -> i.getByHash(hash))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(r -> Objects.equals(r.size(), size))
                .toList();

    }

    public RepoFile getByPath(String relative) {
        List<RepoFile> list = indices.values().stream()
                .map(i -> i.getByPath(relative))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RepoFile::lastModified))
                .toList();
        if (list.isEmpty())
            return null;
        return list.getLast();
    }

    public Result<RepoFile, DedupError> addRepoFile(RepoFile repoFile) {
        return indices.get((int) (repoFile.size() % repo.indices()))
                .add(repoFile)
                .map(f -> repoFile, Function.identity());
    }

    public CompletableFuture<Result<RepoFile, DedupError>> addPath(Path absolutePath, FileHasher fileHasher, MimetypeProvider mimetypeProvider) {
        if (!fileSystem.exists(absolutePath)) {
            return CompletableFuture.completedFuture(Result.ok(null));
        }
        Path relativize = Paths.get(repo.absolutePath()).relativize(absolutePath);
        RepoFile oldRepoFile = getByPath(relativize.toString());

        Result<Long, DedupError> sizeResult = getSize(absolutePath);
        if (sizeResult.hasFailed()) {
            return CompletableFuture.completedFuture(sizeResult.mapError(f -> DedupError.of(ErrorType.WRITE, f.describe(), f.exception())));
        }
        Result<FileTime, DedupError> lastModifiedResult = getLastModifiedTime(absolutePath);
        if (lastModifiedResult.hasFailed()) {
            return CompletableFuture.completedFuture(lastModifiedResult.mapError(f -> DedupError.of(ErrorType.WRITE, f.describe(), f.exception())));
        }

        Long size = sizeResult.value();
        FileTime fileTime = lastModifiedResult.value();

        if (oldRepoFile != null) {
            if (Objects.equals(oldRepoFile.size(), size)) {
                if (fileTime.toMillis() <= oldRepoFile.lastModified()) {
                    if (!oldRepoFile.missing()) {
                        return CompletableFuture.completedFuture(Result.ok(null));
                    } else {
                        // repapeared
                        return CompletableFuture.completedFuture(addRepoFile(oldRepoFile.withMissing(false)));
                    }
                }
            }
        }

        return calcHash(absolutePath, size, fileHasher).thenApply(hashResult -> {
            if (hashResult.hasFailed())
                return hashResult.mapError(l -> DedupError.of(ErrorType.WRITE, absolutePath + ": hashing failed", l.exception()));

            String mimeType = mimetypeProvider.get(absolutePath).getValueOr(null);
            String fingerprint = null;
            String videoHash = null;
            String pdfHash = null;
            Dimension imageSize = null;
            Map<String, String> attributes = Map.of();
            if (mimeType != null) {
                if (mimeType.startsWith("image/")) {
                    ImageFingerprinter.FingerprintResult fr = new ImageFingerprinter().calculate(absolutePath);
                    fingerprint = fr.fingerprint();
                    imageSize = fr.imageSize();
                } else if (mimeType.startsWith("video/")) {
                    attributes = new MetadataExtractor(fileSystem).extract(absolutePath);
                    videoHash = new VideoFingerprinter().calculateTemporalHash(absolutePath);
                } else if (mimeType.equals("application/pdf")) {
                    attributes = new MetadataExtractor(fileSystem).extract(absolutePath);
                    pdfHash = new PdfFingerprinter(fileSystem).calculatePdfHash(absolutePath);
                } else if (mimeType.startsWith("audio/")) {
                    attributes = new MetadataExtractor(fileSystem).extract(absolutePath);
                }
            }

            RepoFile repoFile = RepoFile.builder()
                    .size(size)
                    .relativePath(relativize.toString())
                    .lastModified(fileTime.toMillis())
                    .hash(hashResult.value())
                    .mimeType(mimeType)
                    .fingerprint(fingerprint)
                    .videoHash(videoHash)
                    .pdfHash(pdfHash)
                    .imageSize(imageSize)
                    .attributes(attributes)
                    .build();

            return addRepoFile(repoFile);
        });
    }

    private CompletableFuture<Result<String, DedupError>> calcHash(Path absolutePath, long size, FileHasher fileHasher) {
        if (size < 20) {
            try {
                return CompletableFuture.completedFuture(Result.ok(binaryFormatter.format(fileSystem.readAllBytes(absolutePath))));
            } catch (IOException e) {
                return CompletableFuture.completedFuture(Result.err(DedupError.of(ErrorType.LOAD, absolutePath + ": " + e, e)));
            }
        }
        return fileHasher.hash(absolutePath);
    }

    private Result<FileTime, DedupError> getLastModifiedTime(Path absolutePath) {
        try {
            return Result.ok(fileSystem.getLastModifiedTime(absolutePath));
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.LOAD, absolutePath + ": Could not get last modified", e));
        }
    }

    private Result<Long, DedupError> getSize(Path absolutePath) {
        try {
            return Result.ok(fileSystem.size(absolutePath));
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.LOAD, absolutePath + ": Could not get size", e));
        }
    }

    public void close() {
        for (IndexManager index : indices.values()) {
            index.close();
        }
    }

}