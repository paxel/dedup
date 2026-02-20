package paxel.dedup.repo.domain.repo;
import lombok.RequiredArgsConstructor;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.CloseError;
import paxel.dedup.domain.model.errors.LoadError;
import paxel.dedup.domain.model.errors.WriteError;
import paxel.dedup.domain.model.TunneledIoException;
import paxel.dedup.domain.port.out.LineCodec;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.lib.Result;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Base64;

@RequiredArgsConstructor
public class IndexManager {
    public static final String FILES = "files";
    public static final String MISSING = "missing";
    public static final String DUPLICATES = "duplicates";
    public static final String LOAD = "load";
    public static final String LINES = "Lines";
    public static final String CHANGES = "Changes";

    private final Map<String, RepoFile> paths = new ConcurrentHashMap<>();
    private final AtomicReference<BufferedOutputStream> out = new AtomicReference<>();
    private final Map<String, Set<String>> hashes = new ConcurrentHashMap<>();


    private final Path indexFile;
    private final LineCodec<RepoFile> lineCodec;
    private final FileSystem fileSystem;

    public Stream<RepoFile> stream() {
        return paths.values().stream();
    }

    public Result<Statistics, LoadError> load() {
        // MVP: we load everything to memory
        Statistics statistics = new Statistics(indexFile.toAbsolutePath().toString());
        statistics.start(LOAD);
        try (BufferedReader bufferedReader = fileSystem.newBufferedReader(indexFile)) {
            for (; ; ) {
                String s = bufferedReader.readLine();
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
            return Result.err(LoadError.ioException(indexFile, e));
        }
    }

    private RepoFile readValid(String s) throws IOException {
        // Interpret the line as UTF-8 JSON bytes and delegate to codec
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        RepoFile repoFile = lineCodec.decode(ByteBuffer.wrap(data));
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

    public synchronized Result<Void, WriteError> add(RepoFile repoFile) {
        try {

            BufferedOutputStream outputStream = out.updateAndGet(o -> {
                if (o != null)
                    return o;
                else {
                    try {
                        //        return new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(indexFile, StandardOpenOption.APPEND)));
                        return new BufferedOutputStream(fileSystem.newOutputStream(indexFile, StandardOpenOption.APPEND));
                    } catch (IOException e) {
                        throw new TunneledIoException(e);
                    }
                }
            });

            ByteBuffer encoded = lineCodec.encode(repoFile);
            byte[] arr;
            if (encoded.hasArray()) {
                int offset = encoded.arrayOffset() + encoded.position();
                int len = encoded.remaining();
                arr = new byte[len];
                System.arraycopy(encoded.array(), offset, arr, 0, len);
            } else {
                arr = new byte[encoded.remaining()];
                encoded.duplicate().get(arr);
            }
            // Write the JSON line directly (UTF-8), followed by newline
            outputStream.write(arr);
            outputStream.write('\n');
            outputStream.flush();
            return Result.ok(null);
        } catch (TunneledIoException e) {
            return Result.err(WriteError.ioException(indexFile, e.getCause()));
        } catch (IOException e) {
            return Result.err(WriteError.ioException(indexFile, e));
        }
    }

    public Result<Boolean, CloseError> close() {
        OutputStream outputStream = out.getAndSet(null);
        if (outputStream == null) {
            return Result.ok(false);
        }
        try {
            outputStream.close();
            return Result.ok(true);
        } catch (IOException e) {
            return Result.err(CloseError.ioException(indexFile, e));
        }
    }
}
