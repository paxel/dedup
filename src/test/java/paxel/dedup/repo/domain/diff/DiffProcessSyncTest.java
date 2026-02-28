package paxel.dedup.repo.domain.diff;

import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.dedup.repo.domain.repo.RepoManager;
import paxel.lib.Result;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DiffProcessSyncTest {

    static class TestDedupConfig implements DedupConfig {
        private final Path repoDir;
        private final Repo a;
        private final Repo b;

        TestDedupConfig(Path repoDir, Repo a, Repo b) {
            this.repoDir = repoDir;
            this.a = a;
            this.b = b;
        }

        @Override
        public Result<List<Repo>, DedupError> getRepos() {
            return Result.ok(List.of(a, b));
        }

        @Override
        public Result<Repo, DedupError> getRepo(String name) {
            if (a.name().equals(name)) return Result.ok(a);
            if (b.name().equals(name)) return Result.ok(b);
            return Result.err(null);
        }

        @Override
        public Result<Repo, DedupError> createRepo(String name, Path path, int indices) {
            return Result.err(null);
        }

        @Override
        public Result<Repo, DedupError> changePath(String name, Path path) {
            return Result.err(null);
        }

        @Override
        public Result<Boolean, DedupError> deleteRepo(String name) {
            return Result.err(null);
        }

        @Override
        public Path getRepoDir() {
            return repoDir;
        }

        @Override
        public Result<Boolean, DedupError> renameRepo(String oldName, String newName) {
            return Result.err(null);
        }

        @Override
        public Result<Repo, DedupError> setCodec(String name, Repo.Codec codec) {
            return Result.err(null);
        }
    }

    /**
     * In-memory mock FileSystem that records file operations and stores content in maps.
     * Strictly no real I/O. Sufficient for RepoManager/IndexManager and DiffProcess sync tests.
     */
    static class MockRecordingFileSystem implements FileSystem {
        private final Set<Path> directories = ConcurrentHashMap.newKeySet();
        private final Map<Path, byte[]> files = new ConcurrentHashMap<>();
        private final Map<Path, Long> lastModified = new ConcurrentHashMap<>();
        private final List<String> operations = Collections.synchronizedList(new ArrayList<>());

        List<String> ops() {
            return operations;
        }

        @Override
        public boolean exists(Path path) {
            return directories.contains(path) || files.containsKey(path);
        }

        @Override
        public Stream<Path> list(Path dir) {
            return files.keySet().stream().filter(p -> Objects.equals(p.getParent(), dir));
        }

        @Override
        public Stream<Path> walk(Path start) {
            return files.keySet().stream().filter(p -> p.startsWith(start));
        }

        @Override
        public boolean isRegularFile(Path path) {
            return files.containsKey(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return directories.contains(path) && !files.containsKey(path);
        }

        @Override
        public boolean isSymbolicLink(Path path) {
            return false;
        }

        @Override
        public long size(Path path) throws IOException {
            byte[] b = files.get(path);
            if (b == null) throw new IOException("no such file: " + path);
            return b.length;
        }

        @Override
        public FileTime getLastModifiedTime(Path path) {
            return FileTime.fromMillis(lastModified.getOrDefault(path, 0L));
        }

        @Override
        public java.io.InputStream newInputStream(Path path, StandardOpenOption... options) {
            byte[] b = files.getOrDefault(path, new byte[0]);
            return new ByteArrayInputStream(b);
        }

        @Override
        public OutputStream newOutputStream(Path path, StandardOpenOption... options) {
            operations.add("openOut " + path);
            boolean append = false;
            for (StandardOpenOption opt : options) {
                if (opt == StandardOpenOption.APPEND) {
                    append = true;
                    break;
                }
            }
            final byte[] initial = append ? files.getOrDefault(path, new byte[0]) : new byte[0];

            return new OutputStream() {
                private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                {
                    try {
                        buffer.write(initial);
                    } catch (IOException ignored) {
                    }
                }

                private void sync() {
                    byte[] data = buffer.toByteArray();
                    files.put(path, data);
                    lastModified.put(path, System.currentTimeMillis());
                }

                @Override
                public void write(int b) throws IOException {
                    buffer.write(b);
                    sync();
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    buffer.write(b, off, len);
                    sync();
                }

                @Override
                public void flush() throws IOException {
                    sync();
                }

                @Override
                public void close() throws IOException {
                    sync();
                }
            };
        }

        @Override
        public BufferedReader newBufferedReader(Path path) {
            byte[] b = files.get(path);
            if (b == null) {
                return new BufferedReader(new StringReader(""));
            }
            return new BufferedReader(new StringReader(new String(b, StandardCharsets.UTF_8)));
        }

        @Override
        public byte[] readAllBytes(Path path) {
            return files.getOrDefault(path, new byte[0]);
        }

        @Override
        public void write(Path path, byte[] bytes, StandardOpenOption... options) {
            files.put(path, Arrays.copyOf(bytes, bytes.length));
            operations.add("write " + path);
        }

        @Override
        public void delete(Path path) {
            files.remove(path);
            operations.add("delete " + path);
        }

        @Override
        public boolean deleteIfExists(Path path) {
            boolean existed = files.remove(path) != null;
            if (existed) operations.add("delete " + path);
            return existed;
        }

        @Override
        public void createDirectories(Path path) {
            // create full hierarchy
            Path p = path;
            while (p != null && directories.add(p)) {
                p = p.getParent();
            }
            operations.add("mkdirs " + path);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) {
            byte[] data = files.get(source);
            if (data == null) data = new byte[0];
            files.put(target, Arrays.copyOf(data, data.length));
            operations.add("copy " + source + " -> " + target);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) {
            byte[] data = files.remove(source);
            if (data == null) data = new byte[0];
            files.put(target, data);
            operations.add("move " + source + " -> " + target);
        }

        // helpers for tests
        void putFile(String abs, String content) {
            Path p = Paths.get(abs);
            Path parent = p.getParent();
            if (parent != null) createDirectories(parent);
            files.put(p, content.getBytes(StandardCharsets.UTF_8));
            lastModified.put(p, 1L);
        }
    }

    @Test
    void sync_doesNotCopy_whenContentAlreadyPresentInB() {
        MockRecordingFileSystem fs = new MockRecordingFileSystem();

        Path repoDir = Paths.get("/repos");
        Path aData = Paths.get("/Adata");
        Path bData = Paths.get("/Bdata");
        fs.createDirectories(repoDir);
        fs.createDirectories(aData);
        fs.createDirectories(bData);

        Repo repoA = new Repo("A", aData.toString(), 1);
        Repo repoB = new Repo("B", bData.toString(), 1);
        DedupConfig cfg = new TestDedupConfig(repoDir, repoA, repoB);

        // Prepare indices: A has file with H1/S5 at path x.txt, B already has same content at another path y.txt
        RepoManager aMgr = RepoManager.forRepo(repoA, cfg, fs);
        RepoManager bMgr = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(aMgr.load().hasFailed()).isFalse();
        assertThat(bMgr.load().hasFailed()).isFalse();

        RepoFile aEntry = RepoFile.builder().hash("H1").size(5L).relativePath("x.txt").lastModified(1L).missing(false).mimeType("text/plain").build();
        RepoFile bEntry = RepoFile.builder().hash("H1").size(5L).relativePath("y.txt").lastModified(1L).missing(false).mimeType("text/plain").build();

        assertThat(aMgr.addRepoFile(aEntry).hasFailed()).isFalse();
        assertThat(bMgr.addRepoFile(bEntry).hasFailed()).isFalse();

        // No copy because content exists already in B (path-agnostic)
        DiffProcess process = new DiffProcess(new CliParameter(), "A", "B", cfg, null, fs);
        int rc = process.sync(true, false);
        assertThat(rc).isEqualTo(0);
        assertThat(fs.ops()).noneMatch(s -> s.startsWith("copy "));
    }

    @Test
    void sync_copiesWhenMissingInB_andUpdatesIndex() {
        MockRecordingFileSystem fs = new MockRecordingFileSystem();

        Path repoDir = Paths.get("/repos");
        Path aData = Paths.get("/Adata");
        Path bData = Paths.get("/Bdata");
        fs.createDirectories(repoDir);
        fs.createDirectories(aData.resolve("dir"));
        fs.createDirectories(bData);

        Repo repoA = new Repo("A", aData.toString(), 1);
        Repo repoB = new Repo("B", bData.toString(), 1);
        DedupConfig cfg = new TestDedupConfig(repoDir, repoA, repoB);

        RepoManager aMgr = RepoManager.forRepo(repoA, cfg, fs);
        RepoManager bMgr = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(aMgr.load().hasFailed()).isFalse();
        assertThat(bMgr.load().hasFailed()).isFalse();

        // Create source file content in in-memory FS and index entry; B has no such content
        fs.putFile(aData.resolve("dir/a.txt").toString(), "hello");
        RepoFile aEntry = RepoFile.builder().hash("H2").size(5L).relativePath("dir/a.txt").lastModified(1L).missing(false).mimeType("text/plain").build();
        assertThat(aMgr.addRepoFile(aEntry).hasFailed()).isFalse();

        DiffProcess process = new DiffProcess(new CliParameter(), "A", "B", cfg, null, fs);
        int rc = process.sync(true, false);
        assertThat(rc).isEqualTo(0);

        // Verify copy was invoked and target index updated (missing=false)
        assertThat(fs.ops()).anyMatch(s -> s.equals("copy " + aData.resolve("dir/a.txt") + " -> " + bData.resolve("dir/a.txt")));

        RepoManager bReload = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(bReload.load().hasFailed()).isFalse();
        RepoFile inB = bReload.getByPath("dir/a.txt");
        assertThat(inB).isNotNull();
        assertThat(inB.missing()).isFalse();
        assertThat(inB.hash()).isEqualTo("H2");
        assertThat(inB.size()).isEqualTo(5L);
    }

    @Test
    void sync_deletesWhenMarkedMissingInA_andUpdatesIndex() {
        MockRecordingFileSystem fs = new MockRecordingFileSystem();

        Path repoDir = Paths.get("/repos");
        Path aData = Paths.get("/Adata");
        Path bData = Paths.get("/Bdata");
        fs.createDirectories(repoDir);
        fs.createDirectories(aData);
        fs.createDirectories(bData);

        Repo repoA = new Repo("A", aData.toString(), 1);
        Repo repoB = new Repo("B", bData.toString(), 1);
        DedupConfig cfg = new TestDedupConfig(repoDir, repoA, repoB);

        RepoManager aMgr = RepoManager.forRepo(repoA, cfg, fs);
        RepoManager bMgr = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(aMgr.load().hasFailed()).isFalse();
        assertThat(bMgr.load().hasFailed()).isFalse();

        // B has present file with content H3/S3 at del.txt; A marks this content as missing
        fs.putFile(bData.resolve("del.txt").toString(), "xyz");
        RepoFile bEntry = RepoFile.builder().hash("H3").size(3L).relativePath("del.txt").lastModified(1L).missing(false).mimeType("text/plain").build();
        RepoFile aMissing = RepoFile.builder().hash("H3").size(3L).relativePath("somewhere.txt").lastModified(1L).missing(true).mimeType("text/plain").build();
        assertThat(bMgr.addRepoFile(bEntry).hasFailed()).isFalse();
        assertThat(aMgr.addRepoFile(aMissing).hasFailed()).isFalse();

        DiffProcess process = new DiffProcess(new CliParameter(), "A", "B", cfg, null, fs);
        int rc = process.sync(false, true);
        assertThat(rc).isEqualTo(0);
        assertThat(fs.ops()).anyMatch(s -> s.equals("delete " + bData.resolve("del.txt")));
        RepoManager bReload = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(bReload.load().hasFailed()).isFalse();
        RepoFile inB = bReload.getByPath("del.txt");
        assertThat(inB).isNotNull();
        assertThat(inB.missing()).isTrue();
        assertThat(inB.hash()).isEqualTo("H3");
        assertThat(inB.size()).isEqualTo(3L);
    }

    @Test
    void sync_skipsCopy_whenTargetPathAlreadyOccupiedByDifferentContent() {
        MockRecordingFileSystem fs = new MockRecordingFileSystem();

        Path repoDir = Paths.get("/repos");
        Path aData = Paths.get("/Adata");
        Path bData = Paths.get("/Bdata");
        fs.createDirectories(repoDir);
        fs.createDirectories(aData);
        fs.createDirectories(bData);

        Repo repoA = new Repo("A", aData.toString(), 1);
        Repo repoB = new Repo("B", bData.toString(), 1);
        DedupConfig cfg = new TestDedupConfig(repoDir, repoA, repoB);

        RepoManager aMgr = RepoManager.forRepo(repoA, cfg, fs);
        RepoManager bMgr = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(aMgr.load().hasFailed()).isFalse();
        assertThat(bMgr.load().hasFailed()).isFalse();

        // A has H2/S5 at x.txt. B doesn't have H2/S5 anywhere, but it already has some other file at x.txt
        fs.putFile(aData.resolve("x.txt").toString(), "hello");
        fs.putFile(bData.resolve("x.txt").toString(), "different");
        RepoFile aEntry = RepoFile.builder().hash("H2").size(5L).relativePath("x.txt").lastModified(1L).missing(false).mimeType("text/plain").build();
        RepoFile bEntryAtSamePath = RepoFile.builder().hash("H_OTHER").size(9L).relativePath("x.txt").lastModified(1L).missing(false).mimeType("text/plain").build();

        assertThat(aMgr.addRepoFile(aEntry).hasFailed()).isFalse();
        assertThat(bMgr.addRepoFile(bEntryAtSamePath).hasFailed()).isFalse();

        DiffProcess process = new DiffProcess(new CliParameter(), "A", "B", cfg, null, fs);
        int rc = process.sync(true, false);
        assertThat(rc).isEqualTo(0);

        // Should NOT copy because path is occupied
        assertThat(fs.ops()).noneMatch(s -> s.startsWith("copy "));

        // B's index for x.txt should still be the old one
        RepoManager bReload = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(bReload.load().hasFailed()).isFalse();
        RepoFile inB = bReload.getByPath("x.txt");
        assertThat(inB.hash()).isEqualTo("H_OTHER");
    }

    @Test
    void sync_obeysMimeFilter() {
        MockRecordingFileSystem fs = new MockRecordingFileSystem();

        Path repoDir = Paths.get("/repos");
        Path aData = Paths.get("/Adata");
        Path bData = Paths.get("/Bdata");
        fs.createDirectories(repoDir);
        fs.createDirectories(aData);
        fs.createDirectories(bData);

        Repo repoA = new Repo("A", aData.toString(), 1);
        Repo repoB = new Repo("B", bData.toString(), 1);
        DedupConfig cfg = new TestDedupConfig(repoDir, repoA, repoB);

        RepoManager aMgr = RepoManager.forRepo(repoA, cfg, fs);
        RepoManager bMgr = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(aMgr.load().hasFailed()).isFalse();
        assertThat(bMgr.load().hasFailed()).isFalse();

        // A has an image and a text file; B has nothing.
        fs.putFile(aData.resolve("img.png").toString(), "imagedata");
        fs.putFile(aData.resolve("doc.txt").toString(), "textdata");
        RepoFile aImg = RepoFile.builder().hash("H_IMG").size(9L).relativePath("img.png").lastModified(1L).missing(false).mimeType("image/png").build();
        RepoFile aTxt = RepoFile.builder().hash("H_TXT").size(8L).relativePath("doc.txt").lastModified(1L).missing(false).mimeType("text/plain").build();
        assertThat(aMgr.addRepoFile(aImg).hasFailed()).isFalse();
        assertThat(aMgr.addRepoFile(aTxt).hasFailed()).isFalse();

        // Sync with mime:image filter
        DiffProcess process = new DiffProcess(new CliParameter(), "A", "B", cfg, "mime:image", fs);
        int rc = process.sync(true, false);
        assertThat(rc).isEqualTo(0);

        // Should copy img.png but NOT doc.txt
        assertThat(fs.ops()).anyMatch(s -> s.contains("img.png"));
        assertThat(fs.ops()).noneMatch(s -> s.contains("doc.txt"));

        // Index in B should only have the image
        RepoManager bReload = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(bReload.load().hasFailed()).isFalse();
        assertThat(bReload.getByPath("img.png")).isNotNull();
        assertThat(bReload.getByPath("doc.txt")).isNull();
    }

    @Test
    void sync_obeysMimeFilter_forDelete() {
        MockRecordingFileSystem fs = new MockRecordingFileSystem();

        Path repoDir = Paths.get("/repos");
        Path aData = Paths.get("/Adata");
        Path bData = Paths.get("/Bdata");
        fs.createDirectories(repoDir);
        fs.createDirectories(aData);
        fs.createDirectories(bData);

        Repo repoA = new Repo("A", aData.toString(), 1);
        Repo repoB = new Repo("B", bData.toString(), 1);
        DedupConfig cfg = new TestDedupConfig(repoDir, repoA, repoB);

        RepoManager aMgr = RepoManager.forRepo(repoA, cfg, fs);
        RepoManager bMgr = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(aMgr.load().hasFailed()).isFalse();
        assertThat(bMgr.load().hasFailed()).isFalse();

        // B has an image and a text file. A marks both as missing.
        fs.putFile(bData.resolve("img.png").toString(), "imagedata");
        fs.putFile(bData.resolve("doc.txt").toString(), "textdata");
        RepoFile bImg = RepoFile.builder().hash("H_IMG").size(10L).relativePath("img.png").lastModified(1L).missing(false).mimeType("image/png").build();
        RepoFile bTxt = RepoFile.builder().hash("H_TXT").size(12L).relativePath("doc.txt").lastModified(1L).missing(false).mimeType("text/plain").build();
        assertThat(bMgr.addRepoFile(bImg).hasFailed()).isFalse();
        assertThat(bMgr.addRepoFile(bTxt).hasFailed()).isFalse();

        RepoFile aImgMissing = RepoFile.builder().hash("H_IMG").size(10L).relativePath("img.png").lastModified(1L).missing(true).mimeType("image/png").build();
        RepoFile aTxtMissing = RepoFile.builder().hash("H_TXT").size(12L).relativePath("doc.txt").lastModified(1L).missing(true).mimeType("text/plain").build();
        assertThat(aMgr.addRepoFile(aImgMissing).hasFailed()).isFalse();
        assertThat(aMgr.addRepoFile(aTxtMissing).hasFailed()).isFalse();

        // Sync with mime:image filter and deleteMissing=true
        DiffProcess process = new DiffProcess(new CliParameter(), "A", "B", cfg, "mime:image", fs);
        int rc = process.sync(false, true);
        assertThat(rc).isEqualTo(0);

        // Should delete img.png but NOT doc.txt
        assertThat(fs.ops()).anyMatch(s -> s.equals("delete " + bData.resolve("img.png")));
        assertThat(fs.ops()).noneMatch(s -> s.equals("delete " + bData.resolve("doc.txt")));

        // Index in B should have image marked missing, but text still present
        RepoManager bReload = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(bReload.load().hasFailed()).isFalse();

        RepoFile inBImg = bReload.getByPath("img.png");
        assertThat(inBImg).isNotNull();
        assertThat(inBImg.missing()).as("img.png should be missing").isTrue();

        RepoFile inBTxt = bReload.getByPath("doc.txt");
        assertThat(inBTxt).isNotNull();
        assertThat(inBTxt.missing()).as("doc.txt should NOT be missing").isFalse();
    }

    @Test
    void sync_obeysSizeFilter() {
        MockRecordingFileSystem fs = new MockRecordingFileSystem();

        Path repoDir = Paths.get("/repos");
        Path aData = Paths.get("/Adata");
        Path bData = Paths.get("/Bdata");
        fs.createDirectories(repoDir);
        fs.createDirectories(aData);
        fs.createDirectories(bData);

        Repo repoA = new Repo("A", aData.toString(), 1);
        Repo repoB = new Repo("B", bData.toString(), 1);
        DedupConfig cfg = new TestDedupConfig(repoDir, repoA, repoB);

        RepoManager aMgr = RepoManager.forRepo(repoA, cfg, fs);
        RepoManager bMgr = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(aMgr.load().hasFailed()).isFalse();
        assertThat(bMgr.load().hasFailed()).isFalse();

        // A has files with different sizes
        fs.putFile(aData.resolve("small.txt").toString(), "small");
        fs.putFile(aData.resolve("large.txt").toString(), "very large content");
        RepoFile aSmall = RepoFile.builder().hash("H_SMALL").size(5L).relativePath("small.txt").lastModified(1L).missing(false).mimeType("text/plain").build();
        RepoFile aLarge = RepoFile.builder().hash("H_LARGE").size(18L).relativePath("large.txt").lastModified(1L).missing(false).mimeType("text/plain").build();
        assertThat(aMgr.addRepoFile(aSmall).hasFailed()).isFalse();
        assertThat(aMgr.addRepoFile(aLarge).hasFailed()).isFalse();

        // Sync with size:5 filter
        DiffProcess process = new DiffProcess(new CliParameter(), "A", "B", cfg, "size:5", fs);
        int rc = process.sync(true, false);
        assertThat(rc).isEqualTo(0);

        // Should copy small.txt but NOT large.txt
        assertThat(fs.ops()).anyMatch(s -> s.contains("small.txt"));
        assertThat(fs.ops()).noneMatch(s -> s.contains("large.txt"));
    }
}
