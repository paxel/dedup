package paxel.dedup.repo.domain.repo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Dimension;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.domain.service.DupeObserver;
import paxel.dedup.infrastructure.adapter.out.filesystem.NioFileSystemAdapter;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DuplicateRepoProcess {

    public enum DupePrintMode {
        QUIET, PRINT
    }

    private final CliParameter cliParameter;
    private final List<String> names;
    private final boolean all;
    private final DedupConfig dedupConfig;
    private final Integer threshold;
    private final DupePrintMode printMode;
    private final String mdPath;
    private final String htmlPath;
    private final String movePath;
    private final boolean delete;
    private final boolean interactive;
    private final String widthFilter;
    private final String heightFilter;
    private final FileSystem fileSystem;
    private DupeObserver dupeObserver = DupeObserver.NOOP;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public DuplicateRepoProcess withObserver(DupeObserver observer) {
        this.dupeObserver = observer != null ? observer : DupeObserver.NOOP;
        return this;
    }

    public void cancel() {
        this.cancelled.set(true);
    }

    public DuplicateRepoProcess(CliParameter cliParameter, List<String> names, boolean all, DedupConfig dedupConfig, Integer threshold, DupePrintMode printMode, String mdPath, String htmlPath, String movePath, boolean delete, boolean interactive) {
        this(cliParameter, names, all, dedupConfig, threshold, printMode, mdPath, htmlPath, movePath, delete, interactive, null, null, new NioFileSystemAdapter());
    }

    public DuplicateRepoProcess(CliParameter cliParameter, List<String> names, boolean all, DedupConfig dedupConfig, Integer threshold, DupePrintMode printMode, String mdPath, String htmlPath, String movePath, boolean delete, boolean interactive, String widthFilter, String heightFilter, FileSystem fileSystem) {
        this.cliParameter = cliParameter;
        this.names = names;
        this.all = all;
        this.dedupConfig = dedupConfig;
        this.threshold = threshold;
        this.printMode = printMode;
        this.mdPath = mdPath;
        this.htmlPath = htmlPath;
        this.movePath = movePath;
        this.delete = delete;
        this.interactive = interactive;
        this.widthFilter = widthFilter;
        this.heightFilter = heightFilter;
        this.fileSystem = fileSystem;
    }

    public DuplicateRepoProcess(CliParameter cliParameter, List<String> names, boolean all, DedupConfig dedupConfig, Integer threshold, DupePrintMode printMode, String mdPath, String htmlPath, String movePath, boolean delete, boolean interactive, FileSystem fileSystem) {
        this(cliParameter, names, all, dedupConfig, threshold, printMode, mdPath, htmlPath, movePath, delete, interactive, null, null, fileSystem);
    }

    public DuplicateRepoProcess(CliParameter cliParameter, List<String> names, boolean all, DedupConfig dedupConfig, Integer threshold, DupePrintMode printMode, String mdPath, String htmlPath) {
        this(cliParameter, names, all, dedupConfig, threshold, printMode, mdPath, htmlPath, null, false, false, null, null, new NioFileSystemAdapter());
    }

    public DuplicateRepoProcess(CliParameter cliParameter, List<String> names, boolean all, DedupConfig dedupConfig, Integer threshold,
                                DupePrintMode printMode, String mdPath, String htmlPath, String movePath, boolean delete, boolean interactive,
                                String widthFilter, String heightFilter) {
        this(cliParameter, names, all, dedupConfig, threshold, printMode, mdPath, htmlPath, movePath, delete, interactive, widthFilter, heightFilter, new NioFileSystemAdapter());
    }

    public Result<Integer, DedupError> dupes() {
        Result<List<Repo>, DedupError> reposToProcess;
        if (all) {
            reposToProcess = dedupConfig.getRepos();
        } else {
            List<Repo> repos = new ArrayList<>();
            for (String name : names) {
                Result<Repo, DedupError> repoResult = dedupConfig.getRepo(name);
                if (repoResult.isSuccess()) {
                    repos.add(repoResult.value());
                }
            }
            reposToProcess = Result.ok(repos);
        }

        if (reposToProcess.hasFailed()) {
            return Result.err(reposToProcess.error());
        }

        return Result.ok(dupe(reposToProcess.value()));
    }

    public List<List<RepoRepoFile>> findGroups() {
        if (cliParameter.isVerbose()) {
            log.info("Finding groups for repos: {} (all: {}, threshold: {})", names, all, threshold);
        }
        dupeObserver.onStart(all, names);
        Result<List<Repo>, DedupError> reposToProcess;
        if (all) {
            reposToProcess = dedupConfig.getRepos();
        } else {
            List<Repo> repos = new ArrayList<>();
            for (String name : names) {
                Result<Repo, DedupError> repoResult = dedupConfig.getRepo(name);
                if (repoResult.isSuccess()) {
                    repos.add(repoResult.value());
                } else if (cliParameter.isVerbose()) {
                    log.warn("Failed to load repo: {} - {}", name, repoResult.error());
                }
            }
            reposToProcess = Result.ok(repos);
        }

        if (reposToProcess.hasFailed()) {
            if (cliParameter.isVerbose()) {
                log.error("Failed to get repos to process: {}", reposToProcess.error());
            }
            return List.of();
        }
        List<List<RepoRepoFile>> groups;
        if (threshold != null && threshold > 0) {
            if (cliParameter.isVerbose()) {
                log.info("Using similarity search with threshold {}", threshold);
            }
            groups = findSimilar(reposToProcess.value());
        } else {
            if (cliParameter.isVerbose()) {
                log.info("Using exact hash search");
            }
            groups = findExact(reposToProcess.value());
        }
        int groupCount = 0;
        if (groups != null) {
            groups = new ArrayList<>(groups);
            sortGroups(groups);
            groupCount = groups.size();
        }
        String reportedName;
        if (names.size() > 1 || all) {
            reportedName = "batch";
        } else {
            if (names.isEmpty()) {
                reportedName = "all";
            } else {
                reportedName = names.get(0);
            }
        }
        if (cliParameter.isVerbose()) {
            log.info("Groups found: {}. Reporting as: {}", groupCount, reportedName);
        }
        dupeObserver.onGroupsReady(reportedName, groups);
        dupeObserver.onFinished(reportedName, groupCount);
        return groups;
    }

    private int dupe(List<Repo> repos) {
        List<List<RepoRepoFile>> groups;
        if (threshold != null && threshold > 0) {
            groups = findSimilar(repos);
        } else {
            groups = findExact(repos);
        }

        if (groups == null) {
            return -81;
        }

        groups = new ArrayList<>(groups);
        sortGroups(groups);

        if (printMode == DupePrintMode.PRINT) {
            printGroups(groups);
        }

        if (mdPath != null) {
            generateMarkdownReport(groups);
        }

        if (htmlPath != null) {
            generateHtmlReport(groups);
        }

        if (delete) {
            deleteOthers(groups);
        }

        if (movePath != null) {
            moveOthers(groups);
        }

        if (interactive) {
            new InteractiveDupeProcess(dedupConfig, fileSystem, threshold).start(groups);
        }

        return 0;
    }

    private void sortFilesWithinGroups(List<List<RepoRepoFile>> groups) {
        for (List<RepoRepoFile> group : groups) {
            group.sort((a, b) -> {
                Dimension isA = a.file.imageSize();
                Dimension isB = b.file.imageSize();
                long areaA;
                if (isA != null) {
                    areaA = isA.area();
                } else {
                    areaA = -1;
                }
                long areaB;
                if (isB != null) {
                    areaB = isB.area();
                } else {
                    areaB = -1;
                }
                int byArea = Long.compare(areaB, areaA); // desc
                if (byArea != 0) {
                    return byArea;
                }
                int bySize = b.file.size().compareTo(a.file.size());
                if (bySize != 0) {
                    return bySize;
                }
                int byTime = Long.compare(a.file.lastModified(), b.file.lastModified());
                if (byTime != 0) {
                    return byTime;
                }
                return a.file.relativePath().compareToIgnoreCase(b.file.relativePath());
            });
        }
    }

    private long calculateWastedBytes(List<RepoRepoFile> group) {
        if (group.isEmpty()) {
            return 0L;
        }
        Long sizeObj = group.get(0).file.size();
        long size;
        if (sizeObj != null) {
            size = sizeObj;
        } else {
            size = 0L;
        }
        return (group.size() - 1) * size;
    }

    private void sortGroupsByWastedBytes(List<List<RepoRepoFile>> groups) {
        groups.sort((a, b) -> {
            long wastedA = calculateWastedBytes(a);
            long wastedB = calculateWastedBytes(b);
            return Long.compare(wastedB, wastedA); // descending
        });
    }

    private void sortGroups(List<List<RepoRepoFile>> groups) {
        if (groups == null) {
            return;
        }
        sortFilesWithinGroups(groups);
        sortGroupsByWastedBytes(groups);
    }

    private void deleteOthers(List<List<RepoRepoFile>> groups) {
        for (List<RepoRepoFile> group : groups) {
            // Keep the first one
            for (int i = 1; i < group.size(); i++) {
                RepoRepoFile rrf = group.get(i);
                java.nio.file.Path absolutePath = java.nio.file.Paths.get(rrf.repo.absolutePath(), rrf.file.relativePath());
                if (fileSystem.exists(absolutePath)) {
                    try {
                        fileSystem.delete(absolutePath);
                        log.info("Deleted duplicate: {}", absolutePath);
                        updateRepoIndex(rrf);
                    } catch (java.io.IOException e) {
                        log.error("Failed to delete {}: {}", absolutePath, e.getMessage());
                    }
                }
            }
        }
    }

    private void moveOthers(List<List<RepoRepoFile>> groups) {
        java.nio.file.Path targetDir = java.nio.file.Paths.get(movePath);
        try {
            if (!fileSystem.exists(targetDir)) {
                fileSystem.createDirectories(targetDir);
            }
        } catch (java.io.IOException e) {
            log.error("Failed to create move target directory {}: {}", movePath, e.getMessage());
            return;
        }

        for (List<RepoRepoFile> group : groups) {
            // Keep the first one
            for (int i = 1; i < group.size(); i++) {
                RepoRepoFile rrf = group.get(i);
                java.nio.file.Path sourcePath = java.nio.file.Paths.get(rrf.repo.absolutePath(), rrf.file.relativePath());
                java.nio.file.Path targetPath = targetDir.resolve(sourcePath.getFileName());

                if (fileSystem.exists(sourcePath)) {
                    if (fileSystem.exists(targetPath)) {
                        log.info("Skipping move of {} because {} already exists", sourcePath, targetPath);
                    } else {
                        try {
                            fileSystem.move(sourcePath, targetPath);
                            log.info("Moved duplicate from {} to {}", sourcePath, targetPath);
                            updateRepoIndex(rrf);
                        } catch (java.io.IOException e) {
                            log.error("Failed to move {} to {}: {}", sourcePath, targetPath, e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private void updateRepoIndex(RepoRepoFile rrf) {
        RepoManager rm = RepoManager.forRepo(rrf.repo, dedupConfig, fileSystem);
        Result<Statistics, DedupError> loadResult = rm.load();
        if (loadResult.isSuccess()) {
            rm.addRepoFile(rrf.file.withMissing(true));
            rm.close();
        } else {
            log.error("Failed to load repo index for {} during update after move/delete", rrf.repo.name());
        }
    }

    private String getAbsolutePath(RepoRepoFile rrf) {
        return java.nio.file.Paths.get(rrf.repo.absolutePath(), rrf.file.relativePath()).toAbsolutePath().toString();
    }

    private List<List<RepoRepoFile>> findExact(List<Repo> repos) {
        Map<UniqueHash, Map<String, RepoRepoFile>> all = new HashMap<>();

        for (int i = 0; i < repos.size(); i++) {
            if (cancelled.get()) return List.of();
            Repo repo = repos.get(i);
            dupeObserver.onProcessingRepo(repo.name(), i, repos.size());
            RepoManager r = RepoManager.forRepo(repo, dedupConfig, fileSystem);
            Result<Statistics, DedupError> load = r.load();
            if (load.hasFailed()) {
                dupeObserver.onError(repo.name(), "Failed to load repo index: " + load.error().describe());
                continue;
            }
            r.stream()
                    .filter(repoFile1 -> !repoFile1.missing())
                    .filter(this::matchesDimensionFilters)
                    .forEach(repoFile -> {
                        RepoRepoFile rrf = new RepoRepoFile(repo, repoFile);
                        String absPath = getAbsolutePath(rrf);
                        all.computeIfAbsent(new UniqueHash(repoFile.hash(), repoFile.size()),
                                k -> new LinkedHashMap<>()).putIfAbsent(absPath, rrf);

                        if (repoFile.videoHash() != null && repoFile.videoHash().startsWith("fallback:")) {
                            all.computeIfAbsent(new UniqueHash(repoFile.videoHash(), repoFile.size()),
                                    k -> new LinkedHashMap<>()).putIfAbsent(absPath, rrf);
                        }
                    });
        }

        return all.values().stream()
                .map(pathMap -> (List<RepoRepoFile>) new ArrayList<>(pathMap.values()))
                .filter(repoRepoFiles -> repoRepoFiles.size() > 1)
                .toList();
    }

    private List<List<RepoRepoFile>> findSimilar(List<Repo> repos) {
        List<RepoRepoFile> images = new ArrayList<>();
        List<RepoRepoFile> videos = new ArrayList<>();
        List<RepoRepoFile> pdfs = new ArrayList<>();
        List<RepoRepoFile> audios = new ArrayList<>();

        for (int i = 0; i < repos.size(); i++) {
            if (cancelled.get()) return List.of();
            Repo repo = repos.get(i);
            dupeObserver.onProcessingRepo(repo.name(), i, repos.size());
            RepoManager r = RepoManager.forRepo(repo, dedupConfig, fileSystem);
            Result<Statistics, DedupError> load = r.load();
            if (load.hasFailed()) {
                dupeObserver.onError(repo.name(), "Failed to load repo index: " + load.error().describe());
                continue;
            }
            r.stream()
                    .filter(rf -> !rf.missing())
                    .filter(this::matchesDimensionFilters)
                    .forEach(rf -> {
                        if (rf.fingerprint() != null && !rf.fingerprint().isBlank()) {
                            images.add(new RepoRepoFile(repo, rf));
                        }
                        if (rf.videoHash() != null && !rf.videoHash().isBlank()) {
                            videos.add(new RepoRepoFile(repo, rf));
                        }
                        if (rf.pdfHash() != null && !rf.pdfHash().isBlank()) {
                            pdfs.add(new RepoRepoFile(repo, rf));
                        }
                        if (rf.audioHash() != null && !rf.audioHash().isBlank()) {
                            audios.add(new RepoRepoFile(repo, rf));
                        }
                    });
        }

        List<List<RepoRepoFile>> groups = new ArrayList<>();

        // Image Similarity (Hamming Distance)
        if (!images.isEmpty()) {
            groups.addAll(groupByHamming(images, 64)); // dHash is 64-bit
        }

        // Video Similarity (Hamming Distance on 192-bit Temporal Hash)
        if (!videos.isEmpty()) {
            groups.addAll(groupByHamming(videos, 192)); // 3 * 64 bits
        }

        // PDF Similarity (Exact Match of text hash)
        if (!pdfs.isEmpty()) {
            groups.addAll(groupByExactHash(pdfs, RepoFile::pdfHash));
        }

        // Audio Similarity (Duration + Chunk Hash)
        if (!audios.isEmpty()) {
            groups.addAll(groupByAudio(audios));
        }

        if (groups.isEmpty()) {
            log.info("No similar files found.");
        }

        return groups;
    }

    private List<List<RepoRepoFile>> groupByHamming(List<RepoRepoFile> items, int bitLength) {
        List<List<RepoRepoFile>> groups = new ArrayList<>();
        Set<Integer> handled = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            if (cancelled.get()) return groups;
            if (handled.contains(i)) continue;

            if (i % 100 == 0) {
                dupeObserver.onGroupingSimilar(i, items.size(), bitLength, threshold);
            }

            List<RepoRepoFile> group = new ArrayList<>();
            RepoRepoFile first = items.get(i);
            group.add(first);
            Set<String> absPaths = new HashSet<>();
            absPaths.add(getAbsolutePath(first));

            String f1 = getRelevantFingerprint(first.file, bitLength);
            if (f1 == null) {
                handled.add(i);
                continue;
            }
            java.math.BigInteger b1;
            try {
                b1 = new java.math.BigInteger(f1, 16);
            } catch (Exception e) {
                log.error("Failed to parse fingerprint '{}' for file {}: {}", f1, first.file.relativePath(), e.getMessage());
                handled.add(i);
                continue;
            }

            for (int j = i + 1; j < items.size(); j++) {
                if (cancelled.get()) return groups;
                if (handled.contains(j)) continue;

                RepoRepoFile other = items.get(j);
                String f2 = getRelevantFingerprint(other.file, bitLength);
                if (f2 == null) {
                    handled.add(j);
                    continue;
                }
                java.math.BigInteger b2;
                try {
                    b2 = new java.math.BigInteger(f2, 16);
                } catch (Exception e) {
                    log.error("Failed to parse fingerprint '{}' for file {}: {}", f2, other.file.relativePath(), e.getMessage());
                    handled.add(j);
                    continue;
                }

                int distance = hammingDistance(b1, b2);
                double similarity = (1.0 - (double) distance / bitLength) * 100.0;

                if (similarity >= threshold) {
                    String otherAbsPath = getAbsolutePath(other);
                    if (!absPaths.contains(otherAbsPath)) {
                        group.add(other);
                        absPaths.add(otherAbsPath);
                    }
                    handled.add(j);
                }
            }
            if (group.size() > 1) {
                groups.add(group);
            }
        }
        return groups;
    }

    private String getRelevantFingerprint(RepoFile rf, int bitLength) {
        if (bitLength == 64) return rf.fingerprint();
        if (bitLength == 192) {
            String vh = rf.videoHash();
            if (vh != null && vh.startsWith("fallback:")) {
                return null;
            }
            return vh;
        }
        return null;
    }

    private List<List<RepoRepoFile>> groupByExactHash(List<RepoRepoFile> items, java.util.function.Function<RepoFile, String> hashExtractor) {
        Map<String, Map<String, RepoRepoFile>> map = new HashMap<>();
        for (RepoRepoFile item : items) {
            String hash = hashExtractor.apply(item.file);
            if (hash != null) {
                map.computeIfAbsent(hash, k -> new LinkedHashMap<>())
                        .putIfAbsent(getAbsolutePath(item), item);
            }
        }
        return map.values().stream()
                .map(pathMap -> (List<RepoRepoFile>) new ArrayList<>(pathMap.values()))
                .filter(g -> g.size() > 1).toList();
    }

    private List<List<RepoRepoFile>> groupByAudio(List<RepoRepoFile> audios) {
        List<List<RepoRepoFile>> groups = new ArrayList<>();
        Set<Integer> handled = new HashSet<>();
        for (int i = 0; i < audios.size(); i++) {
            if (handled.contains(i)) continue;

            List<RepoRepoFile> group = new ArrayList<>();
            RepoRepoFile first = audios.get(i);
            group.add(first);
            Set<String> absPaths = new HashSet<>();
            absPaths.add(getAbsolutePath(first));

            RepoFile r1 = first.file;
            double d1 = parseDuration(r1.attributes().get("duration"));

            for (int j = i + 1; j < audios.size(); j++) {
                if (handled.contains(j)) continue;

                RepoRepoFile other = audios.get(j);
                RepoFile r2 = other.file;
                if (Objects.equals(r1.audioHash(), r2.audioHash())) {
                    double d2 = parseDuration(r2.attributes().get("duration"));
                    if (Math.abs(d1 - d2) <= 2.0) { // 2s tolerance
                        String otherAbsPath = getAbsolutePath(other);
                        if (!absPaths.contains(otherAbsPath)) {
                            group.add(other);
                            absPaths.add(otherAbsPath);
                        }
                        handled.add(j);
                    }
                }
            }
            if (group.size() > 1) {
                groups.add(group);
            }
        }
        return groups;
    }

    private double parseDuration(String duration) {
        if (duration == null) return -10.0;
        try {
            return Double.parseDouble(duration);
        } catch (NumberFormatException e) {
            return -10.0;
        }
    }

    private boolean eval(String expr, int value) {
        String e = expr.trim();
        String op;
        String numStr;
        if (e.startsWith(">=")) {
            op = ">=";
            numStr = e.substring(2);
        } else if (e.startsWith("<=")) {
            op = "<=";
            numStr = e.substring(2);
        } else if (e.startsWith(">")) {
            op = ">";
            numStr = e.substring(1);
        } else if (e.startsWith("<")) {
            op = "<";
            numStr = e.substring(1);
        } else if (e.startsWith("=")) {
            op = "=";
            numStr = e.substring(1);
        } else {
            op = "=";
            numStr = e;
        }
        try {
            int target = Integer.parseInt(numStr.trim());
            return switch (op) {
                case "<" -> value < target;
                case "<=" -> value <= target;
                case ">" -> value > target;
                case ">=" -> value >= target;
                default -> value == target;
            };
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private int hammingDistance(java.math.BigInteger b1, java.math.BigInteger b2) {
        return b1.xor(b2).bitCount();
    }

    private boolean matchesDimensionFilters(RepoFile rf) {
        if (widthFilter == null && heightFilter == null) return true;
        Dimension is = rf.imageSize();
        if (is == null) return false; // exclude entries without dimensions when any filter is set
        if (widthFilter != null && !eval(widthFilter, is.getWidth())) return false;
        if (heightFilter != null && !eval(heightFilter, is.getHeight())) return false;
        return true;
    }

    private void printGroups(List<List<RepoRepoFile>> groups) {
        for (List<RepoRepoFile> group : groups) {
            if (threshold != null && threshold > 0) {
                printSimilarGroup(group);
            } else {
                printDuplicateGroup(group);
            }
        }
    }

    private void printSimilarGroup(List<RepoRepoFile> group) {
        log.info("Similar Group (Threshold: {}%):", threshold);
        for (RepoRepoFile rrf : group) {
            Dimension is = rrf.file.imageSize();
            String isInfo = is != null ? ", image: " + is : "";
            log.info("  {}: {}/{} (size: {}{}, modified: {}, fingerprint: {})", rrf.repo.name(), rrf.repo.absolutePath(), rrf.file.relativePath(), formatSize(rrf.file.size()), isInfo, formatDate(rrf.file.lastModified()), rrf.file.fingerprint());
            if (rrf.file.attributes() != null && !rrf.file.attributes().isEmpty()) {
                log.info("    Attributes: {}", rrf.file.attributes());
            }
        }
    }

    private void printDuplicateGroup(List<RepoRepoFile> repoRepoFiles) {
        log.info("{}\n {} bytes", repoRepoFiles.getFirst().file.hash(), repoRepoFiles.getFirst().file.size());
        repoRepoFiles.stream()
                .sorted((a, b) -> {
                    Dimension isA = a.file.imageSize();
                    Dimension isB = b.file.imageSize();
                    long areaA = isA != null ? isA.area() : -1;
                    long areaB = isB != null ? isB.area() : -1;
                    int byArea = Long.compare(areaB, areaA);
                    if (byArea != 0) return byArea;
                    int bySize = b.file.size().compareTo(a.file.size());
                    if (bySize != 0) return bySize;
                    return Long.compare(a.file.lastModified(), b.file.lastModified());
                })
                .forEach(repoRepoFile -> {
                    Dimension is = repoRepoFile.file.imageSize();
                    String isInfo = is != null ? ", image: " + is : "";
                    log.info("  {}\n   {}/{} (size: {}{}, modified: {})", repoRepoFile.repo.name(), repoRepoFile.repo.absolutePath(), repoRepoFile.file.relativePath(), formatSize(repoRepoFile.file.size()), isInfo, formatDate(repoRepoFile.file.lastModified()));
                    if (repoRepoFile.file.attributes() != null && !repoRepoFile.file.attributes().isEmpty()) {
                        log.info("    Attributes: {}", repoRepoFile.file.attributes());
                    }
                });
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private String formatDate(long lastModified) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(lastModified));
    }

    private void generateMarkdownReport(List<List<RepoRepoFile>> groups) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Duplicate/Similar Files Report\n\n");
        if (threshold != null && threshold > 0) {
            sb.append("Type: Similarity (Threshold: ").append(threshold).append("%)\n\n");
        } else {
            sb.append("Type: Exact Duplicates\n\n");
        }

        int groupIndex = 1;
        for (List<RepoRepoFile> group : groups) {
            sb.append("## Group ").append(groupIndex++).append("\n");
            if (threshold == null || threshold == 0) {
                sb.append("Hash: `").append(group.getFirst().file.hash()).append("` (").append(formatSize(group.getFirst().file.size())).append(")\n\n");
            }
            for (RepoRepoFile rrf : group) {
                String fullPath = java.nio.file.Paths.get(rrf.repo.absolutePath(), rrf.file.relativePath()).toAbsolutePath().toString();
                sb.append("- **Repo:** ").append(rrf.repo.name()).append("\n");
                sb.append("  - **Path:** `").append(rrf.file.relativePath()).append("`\n");
                sb.append("  - **Size:** ").append(formatSize(rrf.file.size())).append("\n");
                if (rrf.file.imageSize() != null)
                    sb.append("  - **Image:** ").append(rrf.file.imageSize()).append("\n");

                if (rrf.file.attributes() != null && !rrf.file.attributes().isEmpty()) {
                    rrf.file.attributes().forEach((k, v) -> sb.append("  - **").append(k).append(":** ").append(v).append("\n"));
                }

                sb.append("  - **Modified:** ").append(formatDate(rrf.file.lastModified())).append("\n");
                if (rrf.file.mimeType() != null && rrf.file.mimeType().startsWith("image/")) {
                    sb.append("  - ![thumbnail](").append(new java.io.File(fullPath).toURI().toString()).append(")\n");
                }
                sb.append("\n");
            }
            sb.append("---\n\n");
        }

        try {
            fileSystem.write(java.nio.file.Paths.get(mdPath), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            log.info("Markdown report generated: {}", mdPath);
        } catch (java.io.IOException e) {
            log.error("Failed to write Markdown report: {}", e.getMessage());
        }
    }

    private void generateHtmlReport(List<List<RepoRepoFile>> groups) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n<title>Duplicate/Similar Files Report</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: sans-serif; margin: 20px; }\n");
        sb.append(".group { border: 1px solid #ccc; padding: 10px; margin-bottom: 20px; border-radius: 5px; }\n");
        sb.append(".file { margin-bottom: 10px; }\n");
        sb.append("img { max-width: 200px; max-height: 200px; display: block; margin-top: 5px; }\n");
        sb.append(".filmstrip img { max-width: calc(33.3% - 2px); height: auto; }\n");
        sb.append("code { background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }\n");
        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<h1>Duplicate/Similar Files Report</h1>\n");
        if (threshold != null && threshold > 0) {
            sb.append("<p>Type: Similarity (Threshold: ").append(threshold).append("%)</p>\n");
        } else {
            sb.append("<p>Type: Exact Duplicates</p>\n");
        }

        int groupIndex = 1;
        for (List<RepoRepoFile> group : groups) {
            sb.append("<div class=\"group\">\n");
            sb.append("<h2>Group ").append(groupIndex++).append("</h2>\n");
            if (threshold == null || threshold == 0) {
                sb.append("<p>Hash: <code>").append(group.getFirst().file.hash()).append("</code> (").append(formatSize(group.getFirst().file.size())).append(")</p>\n");
            }
            for (RepoRepoFile rrf : group) {
                String fullPath = java.nio.file.Paths.get(rrf.repo.absolutePath(), rrf.file.relativePath()).toAbsolutePath().toString();
                sb.append("<div class=\"file\">\n");
                sb.append("<strong>Repo:</strong> ").append(rrf.repo.name()).append("<br>\n");
                sb.append("<strong>Path:</strong> <code>").append(rrf.file.relativePath()).append("</code><br>\n");
                sb.append("<strong>Size:</strong> ").append(formatSize(rrf.file.size())).append("<br>\n");
                if (rrf.file.imageSize() != null)
                    sb.append("<strong>Image:</strong> ").append(rrf.file.imageSize()).append("<br>\n");

                if (rrf.file.attributes() != null && !rrf.file.attributes().isEmpty()) {
                    rrf.file.attributes().forEach((k, v) ->
                            sb.append("<strong>").append(k).append(":</strong> ").append(v).append("<br>\n")
                    );
                }

                sb.append("<strong>Modified:</strong> ").append(formatDate(rrf.file.lastModified())).append("<br>\n");
                sb.append("<a href=\"").append(new java.io.File(fullPath).toURI().toString()).append("\" target=\"_blank\">Open original</a><br>\n");
                if (rrf.file.mimeType() != null && rrf.file.mimeType().startsWith("image/")) {
                    sb.append("<img src=\"").append(new java.io.File(fullPath).toURI().toString()).append("\" alt=\"thumbnail\">\n");
                }
                sb.append("</div>\n");
            }
            sb.append("</div>\n");
        }
        sb.append("</body>\n</html>");

        try {
            fileSystem.write(java.nio.file.Paths.get(htmlPath), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            log.info("HTML report generated: {}", htmlPath);
        } catch (java.io.IOException e) {
            log.error("Failed to write HTML report: {}", e.getMessage());
        }
    }

    record UniqueHash(String hash, long size) {
    }

    public record RepoRepoFile(Repo repo, @JsonProperty("repoFile") RepoFile file) {
    }
}
