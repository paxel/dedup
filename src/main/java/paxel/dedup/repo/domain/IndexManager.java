package paxel.dedup.repo.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.RequiredArgsConstructor;
import paxel.dedup.model.RepoFile;
import paxel.dedup.model.Statistics;
import paxel.dedup.model.errors.CloseError;
import paxel.dedup.model.errors.LoadError;
import paxel.dedup.model.errors.WriteError;
import paxel.lib.Result;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ObjectReader objectReader;
    private final ObjectWriter objectWriter;
    private final boolean verbose;


    public Result<Statistics, LoadError> load() {
        // MVP: we load everything to memory
        Statistics statistics = new Statistics(indexFile.toAbsolutePath().toString());
        statistics.start(LOAD);
        try (BufferedReader bufferedReader = Files.newBufferedReader(indexFile)) {
            for (; ; ) {
                String s = bufferedReader.readLine();
                if (s == null)
                    break;
                statistics.inc(LINES);
                RepoFile repoFile = objectReader.readValue(s, RepoFile.class);
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

    public Result<Void, WriteError> add(RepoFile repoFile) {
        try {

            BufferedOutputStream outputStream = out.updateAndGet(o -> {
                if (o != null)
                    return o;
                else {
                    try {
                        //        return new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(indexFile, StandardOpenOption.APPEND)));
                        return new BufferedOutputStream(Files.newOutputStream(indexFile, StandardOpenOption.APPEND));
                    } catch (IOException e) {
                        throw new TunneledIoException(e);
                    }
                }
            });

            outputStream.write((objectWriter.writeValueAsString(repoFile) + "\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return Result.ok(null);
        } catch (TunneledIoException e) {
            return Result.err(WriteError.ioException(indexFile, e.getCause()));
        } catch (JsonProcessingException e) {
            return Result.err(WriteError.jsonException(repoFile, e));
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
