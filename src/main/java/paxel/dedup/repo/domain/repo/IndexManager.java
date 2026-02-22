package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.TunneledIoException;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.model.errors.ErrorType;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.domain.port.out.LineCodec;
import paxel.dedup.infrastructure.adapter.out.serialization.FrameIterator;
import paxel.dedup.infrastructure.adapter.out.serialization.FrameWriter;
import paxel.dedup.infrastructure.logging.ConsoleLogger;
import paxel.lib.Result;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@RequiredArgsConstructor
public class IndexManager {
    private static final ConsoleLogger log = ConsoleLogger.getInstance();
    public static final String FILES = "files";
    public static final String MISSING = "missing";
    public static final String DUPLICATES = "duplicates";
    public static final String LOAD = "load";
    public static final String LINES = "Lines";
    public static final String CHANGES = "Changes";

    private final Map<String, RepoFile> paths = new ConcurrentHashMap<>();
    private final AtomicReference<FrameWriter> out = new AtomicReference<>();
    private final Map<String, Set<String>> hashes = new ConcurrentHashMap<>();


    private final Path indexFile;
    private final LineCodec<RepoFile> lineCodec;
    private final FileSystem fileSystem;
    private final Function<InputStream, FrameIterator> frameIteratorFactory;
    private final Function<OutputStream, FrameWriter> frameWriterFactory;

    public Stream<RepoFile> stream() {
        return paths.values().stream();
    }

    public Result<Statistics, DedupError> load() {
        // MVP: we load everything to memory
        Statistics statistics = new Statistics(indexFile.toAbsolutePath().toString());
        statistics.start(LOAD);
        try (FrameIterator frameIterator = frameIteratorFactory.apply(fileSystem.newInputStream(indexFile))) {
            while (frameIterator.hasNext()) {
                ByteBuffer s = frameIterator.next();
                if (s == null)
                    break;
                statistics.inc(LINES);
                RepoFile repoFile = readValid(s);
                if (repoFile != null) {
                    // the same hash can exist as multiple paths. so we store the paths per hash
                    hashes.computeIfAbsent(repoFile.hash(), h -> new HashSet<>()).add(repoFile.relativePath());

                    // we store only the latest path
                    // if a file has changed in time
                    RepoFile put = paths.put(repoFile.relativePath(), repoFile);
                    if (put != null) {
                        statistics.inc(CHANGES);
                        if (!put.hash().equals(repoFile.hash())) {
                            // The hash for the file has changed and we clean up the lookup
                            hashes.get(put.hash()).remove(put.relativePath());
                        }
                    }
                }
            }
            // count existing and missing files
            long files = paths.values().stream().filter(r -> !r.missing()).count();
            long missing = paths.size() - files;
            // count duplicates
            int duplicates = paths.values().stream()
                    .filter(r -> !r.missing())
                    .map(RepoFile::hash)
                    // duplicates times a hash exists
                    .collect(Collectors.toMap(Function.identity(), f -> 1, Integer::sum))
                    .values()
                    .stream()
                    // reduce by 1
                    .mapToInt(i -> i - 1)
                    // number of duplicate files
                    .sum();
            statistics.set(FILES, files);
            statistics.set(MISSING, missing);
            statistics.set(DUPLICATES, duplicates);
            statistics.stop(LOAD);
            return Result.ok(statistics);
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.LOAD, indexFile + ": load failed", e));
        }
    }

    private RepoFile readValid(ByteBuffer s) throws IOException {
        // Interpret the line as UTF-8 JSON bytes and delegate to codec
        RepoFile repoFile = lineCodec.decode(s);
        if (repoFile.size() == null)
            repoFile = repoFile.withSize(0L);
        if (repoFile.mimeType() == null)
            repoFile = repoFile.withMimeType("");
        if (repoFile.hash() == null)
            repoFile = repoFile.withHash("");
        if (repoFile.relativePath() == null)
            repoFile = repoFile.withRelativePath(".");
        return repoFile;
    }

    public List<RepoFile> getByHash(String hash) {
        Set<String> strings = hashes.get(hash);
        if (strings == null)
            return List.of();
        return strings.stream()
                .map(paths::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public RepoFile getByPath(String relative) {
        return paths.get(relative);
    }

    public synchronized Result<Void, DedupError> add(RepoFile repoFile) {
        try {

            FrameWriter frameWriter = out.updateAndGet(o -> {
                if (o != null)
                    return o;
                else {
                    try {
                        return frameWriterFactory.apply(fileSystem.newOutputStream(indexFile, StandardOpenOption.APPEND));
                    } catch (IOException e) {
                        throw new TunneledIoException(e);
                    }
                }
            });

            ByteBuffer encoded = lineCodec.encode(repoFile);
            frameWriter.write(encoded);

            // update cache
            hashes.computeIfAbsent(repoFile.hash(), h -> new HashSet<>()).add(repoFile.relativePath());
            RepoFile put = paths.put(repoFile.relativePath(), repoFile);
            if (put != null && !put.hash().equals(repoFile.hash())) {
                Set<String> strings = hashes.get(put.hash());
                if (strings != null) {
                    strings.remove(put.relativePath());
                }
            }
            return Result.ok(null);
        } catch (TunneledIoException e) {
            return Result.err(DedupError.of(ErrorType.WRITE, indexFile + ": write failed", toExceptionLocal(e.getCause())));
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.WRITE, indexFile + ": write failed", e));
        }
    }

    public Result<Boolean, DedupError> close() {
        FrameWriter frameWriter = out.getAndSet(null);
        if (frameWriter == null) {
            return Result.ok(false);
        }
        try {
            frameWriter.close();
            return Result.ok(true);
        } catch (IOException e) {
            return Result.err(DedupError.of(ErrorType.CLOSE, indexFile + ": close failed", e));
        }
    }

    private Exception toExceptionLocal(Throwable t) {
        if (t instanceof Exception e) {
            return e;
        }
        return new Exception(t);
    }
}
