package paxel.dedup.infrastructure.adapter.in.web;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.service.EventBus;
import paxel.dedup.domain.service.RepoService;
import paxel.dedup.infrastructure.adapter.out.web.WebDupeObserver;
import paxel.dedup.infrastructure.adapter.out.web.WebUpdateObserver;
import paxel.dedup.infrastructure.config.InfrastructureConfig;
import paxel.dedup.repo.domain.diff.DiffProcess;
import paxel.dedup.repo.domain.files.FilesProcess;
import paxel.dedup.repo.domain.repo.DuplicateRepoProcess;
import paxel.dedup.repo.domain.repo.UpdateReposProcess;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class UiServer {

    private final Javalin app;
    private final RepoService repoService;
    private final EventBus eventBus;
    private final paxel.dedup.domain.port.out.FileSystem fileSystem;
    private final InfrastructureConfig infrastructureConfig;
    private final java.util.concurrent.ExecutorService updateExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ExecutorService dupeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, UpdateReposProcess> activeUpdates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DuplicateRepoProcess> activeDupeProcesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<?>> activeDiffFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Thread> activeDiffThreads = new ConcurrentHashMap<>();

    public UiServer(InfrastructureConfig infrastructureConfig) {
        this.infrastructureConfig = infrastructureConfig;
        this.repoService = infrastructureConfig.getRepoService();
        this.eventBus = infrastructureConfig.getEventBus();
        this.fileSystem = infrastructureConfig.getFileSystem();
        this.app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(infrastructureConfig.getObjectMapper(), false));
            config.showJavalinBanner = false;
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/static";
                staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
            });
            config.spaRoot.addFile("/", "/static/index.html");
        });

        setupRoutes();
        setupWebSockets();
    }

    private void setupRoutes() {
        app.get("/api/config", ctx -> {
            ctx.json(Map.of("verbose", infrastructureConfig.getCliParameter().isVerbose()));
        });

        app.get("/api/repos", ctx -> ctx.json(repoService.getRepos().value()));

        app.post("/api/repos", ctx -> {
            Repo repo = ctx.bodyAsClass(Repo.class);
            var result = repoService.createRepo(repo.name(), java.nio.file.Paths.get(repo.absolutePath()), repo.indices(), repo.codec());
            if (result.isSuccess()) {
                ctx.status(201).json(result.value());
            } else {
                ctx.status(400).json(result.error());
            }
        });

        app.get("/api/utils/browse", ctx -> {
            String currentPath = ctx.queryParam("path");
            boolean showHidden = Boolean.parseBoolean(ctx.queryParam("showHidden"));
            Path root = currentPath != null && !currentPath.isBlank() ? Paths.get(currentPath) : Paths.get(System.getProperty("user.home"));

            if (!fileSystem.exists(root) || !fileSystem.isDirectory(root)) {
                root = Paths.get(System.getProperty("user.home"));
            }

            final Path finalRoot = root.toAbsolutePath().normalize();
            try (var stream = fileSystem.list(finalRoot)) {
                List<Map<String, Object>> items = stream
                        .filter(fileSystem::isDirectory)
                        .filter(p -> showHidden || !p.getFileName().toString().startsWith("."))
                        .map(p -> {
                            try {
                                Map<String, Object> item = new java.util.HashMap<>();
                                item.put("name", p.getFileName().toString());
                                item.put("path", p.toAbsolutePath().normalize().toString());
                                item.put("isDirectory", true);
                                return item;
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .sorted(java.util.Comparator.comparing(m -> (String) m.get("name")))
                        .collect(java.util.stream.Collectors.toList());

                Map<String, Object> response = new java.util.HashMap<>();
                response.put("currentPath", finalRoot.toString());
                response.put("parentPath", finalRoot.getParent() != null ? finalRoot.getParent().toString() : null);
                response.put("items", items);
                ctx.json(response);
            } catch (Exception e) {
                log.error("Error browsing directory: {}", finalRoot, e);
                ctx.status(500).json(Map.of("message", "Error browsing directory: " + e.getMessage()));
            }
        });

        app.get("/api/files/preview", ctx -> {
            String pathStr = ctx.queryParam("path");
            if (pathStr == null || pathStr.isBlank()) {
                ctx.status(400).json(Map.of("message", "Path is required"));
                return;
            }
            Path path = Paths.get(pathStr);
            if (!fileSystem.exists(path)) {
                ctx.status(404).json(Map.of("message", "File not found"));
                return;
            }

            String mimeType = java.nio.file.Files.probeContentType(path);
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            if (mimeType.startsWith("image/")) {
                ctx.contentType(mimeType);
                try (var is = fileSystem.newInputStream(path)) {
                    ctx.result(is.readAllBytes());
                }
            } else if (mimeType.startsWith("video/")) {
                var generator = new paxel.dedup.repo.domain.repo.VideoFilmstripGenerator(fileSystem);
                List<String> frames = generator.generateBase64Filmstrip(path);
                ctx.json(Map.of("type", "video", "frames", frames));
            } else if ("application/pdf".equals(mimeType)) {
                var generator = new paxel.dedup.repo.domain.repo.PdfThumbnailGenerator(fileSystem);
                String frame = generator.generateBase64Thumbnail(path);
                ctx.json(Map.of("type", "pdf", "frame", frame));
            } else {
                ctx.status(415).json(Map.of("message", "Unsupported preview type: " + mimeType));
            }
        });

        app.post("/api/files/delete", ctx -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = ctx.bodyAsClass(List.class);
            int deletedCount = 0;
            List<String> errors = new java.util.ArrayList<>();
            for (Map<String, Object> item : items) {
                String repoName = (String) item.get("repoName");
                String repoPath = (String) item.get("repoPath");
                String relativePath = (String) item.get("relativePath");
                long size = item.get("size") instanceof Number n ? n.longValue() : 0L;
                if (repoName == null || repoPath == null || relativePath == null) {
                    errors.add("Missing fields in delete request item");
                    continue;
                }
                Path absolutePath = Paths.get(repoPath, relativePath);
                if (!fileSystem.exists(absolutePath)) {
                    errors.add("File not found: " + absolutePath);
                    continue;
                }
                try {
                    fileSystem.delete(absolutePath);
                    log.info("Deleted: {}", absolutePath);
                    // Update repo index to mark file as missing
                    var repoResult = infrastructureConfig.getDedupConfig().getRepo(repoName);
                    if (repoResult.isSuccess()) {
                        var rm = paxel.dedup.repo.domain.repo.RepoManager.forRepo(repoResult.value(), infrastructureConfig.getDedupConfig(), fileSystem);
                        var loadResult = rm.load();
                        if (loadResult.isSuccess()) {
                            var existing = rm.getByPath(relativePath);
                            if (existing != null) {
                                rm.addRepoFile(existing.withMissing(true));
                            }
                            rm.close();
                        }
                    }
                    deletedCount++;
                } catch (Exception e) {
                    log.error("Failed to delete {}: {}", absolutePath, e.getMessage());
                    errors.add("Failed to delete " + absolutePath + ": " + e.getMessage());
                }
            }
            ctx.json(Map.of("deleted", deletedCount, "errors", errors));
        });

        app.delete("/api/repos/{name}", ctx -> {
            String name = ctx.pathParam("name");
            var result = repoService.deleteRepo(name);
            if (result.isSuccess()) {
                ctx.status(204);
            } else {
                ctx.status(400).json(result.error());
            }
        });

        app.post("/api/repos/{name}/prune", ctx -> {
            String name = ctx.pathParam("name");
            CompletableFuture.runAsync(() -> {
                var result = repoService.pruneRepo(name);
                if (result.hasFailed()) {
                    log.error("Prune failed for {}: {}", name, result.error().describe());
                    eventBus.publish("error", Map.of("repo", name, "message", result.error().describe()));
                } else {
                    log.info("Prune completed successfully for: {}", name);
                    eventBus.publish("finished", Map.of("repo", name, "message", "Prune completed"));
                }
            });
            ctx.status(202).json(Map.of("message", "Prune started for " + name));
        });

        app.post("/api/repos/{name}/relocate", ctx -> {
            String name = ctx.pathParam("name");
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String newPath = body.get("path");
            if (newPath == null || newPath.isBlank()) {
                ctx.status(400).json(Map.of("message", "New path is required"));
                return;
            }
            var result = repoService.relocateRepo(name, newPath);
            if (result.isSuccess()) {
                ctx.json(result.value());
            } else {
                ctx.status(400).json(result.error());
            }
        });

        app.post("/api/repos/{name}/cp", ctx -> {
            String name = ctx.pathParam("name");
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String destinationName = body.get("destinationName");
            String path = body.get("path");
            if (destinationName == null || destinationName.isBlank() || path == null || path.isBlank()) {
                ctx.status(400).json(Map.of("message", "Destination name and path are required"));
                return;
            }
            var result = repoService.cloneRepo(name, destinationName, path);
            if (result.isSuccess()) {
                ctx.status(201).json(Map.of("message", "Repo cloned successfully"));
            } else {
                ctx.status(400).json(result.error());
            }
        });

        app.post("/api/repos/{name}/mv", ctx -> {
            String name = ctx.pathParam("name");
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String destinationName = body.get("destinationName");
            if (destinationName == null || destinationName.isBlank()) {
                ctx.status(400).json(Map.of("message", "Destination name is required"));
                return;
            }
            var result = repoService.moveRepo(name, destinationName);
            if (result.isSuccess()) {
                ctx.status(200).json(Map.of("message", "Repo moved successfully"));
            } else {
                ctx.status(400).json(result.error());
            }
        });

        app.post("/api/repos/{name}/copy", ctx -> {
            String name = ctx.pathParam("name");
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String target = body.get("target");
            Boolean move = Boolean.valueOf(String.valueOf(body.getOrDefault("move", "false")));
            String filter = body.get("filter");
            String appendix = body.get("appendix");

            if (target == null || target.isBlank()) {
                ctx.status(400).json(Map.of("message", "Target directory is required"));
                return;
            }

            CompletableFuture.runAsync(() -> {
                var result = repoService.copyFiles(name, target, move, filter, appendix);
                if (result.hasFailed()) {
                    log.error("{} failed for {}: {}", move ? "Move" : "Copy", name, result.error().describe());
                    eventBus.publish("error", Map.of("repo", name, "message", result.error().describe()));
                } else {
                    log.info("{} completed successfully for: {}", move ? "Move" : "Copy", name);
                    eventBus.publish("finished", Map.of("repo", name, "message", (move ? "Move" : "Copy") + " completed"));
                }
            });
            ctx.status(202).json(Map.of("message", (move ? "Move" : "Copy") + " started for " + name));
        });

        app.post("/api/repos/update-batch", ctx -> {
            var body = ctx.bodyAsClass(java.util.Map.class);
            List<String> names = (List<String>) body.get("repos");
            if (names == null || names.isEmpty()) {
                ctx.status(400).json(Map.of("message", "No repositories specified"));
                return;
            }
            int threads = body.containsKey("threads") ? ((Number) body.get("threads")).intValue() : 2;
            boolean refreshFingerprints = body.containsKey("refreshFingerprints") && Boolean.TRUE.equals(body.get("refreshFingerprints"));
            log.info("Batch update requested for repositories: {} (threads={}, refreshFingerprints={})", names, threads, refreshFingerprints);
            updateExecutor.execute(() -> {
                for (String name : names) {
                    if (activeUpdates.containsKey(name)) {
                        log.warn("Update already running for {}, skipping", name);
                        continue;
                    }
                    UpdateReposProcess process = new UpdateReposProcess(
                            new CliParameter(),
                            java.util.List.of(name),
                            false,
                            threads,
                            infrastructureConfig.getDedupConfig(),
                            false,
                            refreshFingerprints,
                            infrastructureConfig.getFileSystem()
                    );
                    process.withObserver(new WebUpdateObserver(name, name, eventBus));
                    activeUpdates.put(name, process);
                    eventBus.publish("progress", Map.of("repo", name, "reset", true));
                    try {
                        log.info("Starting sequential update for: {}", name);
                        var result = process.update();
                        if (result.hasFailed()) {
                            String desc = result.error().describe();
                            if (desc.contains("cancelled")) {
                                log.info("Update cancelled for: {}", name);
                            } else {
                                log.error("Update failed for {}: {}", name, desc);
                                eventBus.publish("error", Map.of("repo", name, "message", desc));
                            }
                        } else {
                            log.info("Update completed successfully for: {}", name);
                        }
                        eventBus.publish("finished", Map.of("repo", name));
                    } catch (Exception e) {
                        log.error("Critical error during update for {}", name, e);
                        eventBus.publish("error", Map.of("repo", name, "message", e.getMessage()));
                    } finally {
                        activeUpdates.remove(name);
                    }
                }
            });
            ctx.status(202).json(Map.of("message", "Batch update started for " + names.size() + " repositories"));
        });

        app.post("/api/repos/{name}/update", ctx -> {
            String name = ctx.pathParam("name");
            int threads = 2;
            boolean refreshFingerprints = false;
            String bodyStr = ctx.body();
            if (bodyStr != null && !bodyStr.isBlank()) {
                var body = ctx.bodyAsClass(java.util.Map.class);
                if (body.containsKey("threads")) {
                    threads = ((Number) body.get("threads")).intValue();
                }
                if (body.containsKey("refreshFingerprints")) {
                    refreshFingerprints = Boolean.TRUE.equals(body.get("refreshFingerprints"));
                }
            }
            log.info("Update requested for repository: {} (threads={}, refreshFingerprints={})", name, threads, refreshFingerprints);
            if (activeUpdates.containsKey(name)) {
                ctx.status(409).json(java.util.Map.of("message", "Update already running for " + name));
                return;
            }
            UpdateReposProcess process = new UpdateReposProcess(
                    new CliParameter(),
                    java.util.List.of(name),
                    false,
                    threads,
                    infrastructureConfig.getDedupConfig(),
                    false,
                    refreshFingerprints,
                    infrastructureConfig.getFileSystem()
            );
            process.withObserver(new WebUpdateObserver(name, name, eventBus));
            activeUpdates.put(name, process);
            eventBus.publish("progress", Map.of("repo", name, "reset", true));
            updateExecutor.execute(() -> {
                try {
                    log.info("Starting background update process for: {}", name);
                    var result = process.update();
                    if (result.hasFailed()) {
                        String desc = result.error().describe();
                        if (desc.contains("cancelled")) {
                            log.info("Update cancelled for: {}", name);
                        } else {
                            log.error("Update failed for {}: {}", name, desc);
                            eventBus.publish("error", Map.of("repo", name, "message", desc));
                        }
                    } else {
                        log.info("Update completed successfully for: {}", name);
                    }
                    eventBus.publish("finished", Map.of("repo", name));
                } catch (Exception e) {
                    log.error("Critical error during update for {}", name, e);
                    eventBus.publish("error", Map.of("repo", name, "message", e.getMessage()));
                } finally {
                    activeUpdates.remove(name);
                }
            });
            ctx.status(202).json(java.util.Map.of("message", "Update started for " + name));
        });

        app.post("/api/repos/{name}/cancel", ctx -> {
            String name = ctx.pathParam("name");
            UpdateReposProcess process = activeUpdates.get(name);
            if (process != null) {
                process.cancel();
                log.info("Cancel requested for repository: {}", name);
                ctx.status(202).json(Map.of("message", "Cancel requested for " + name));
            } else {
                ctx.status(404).json(Map.of("message", "No active update for " + name));
            }
        });

        app.get("/api/repos/{name}/dupes", ctx -> {
            String name = ctx.pathParam("name");
            Integer threshold = ctx.queryParamAsClass("threshold", Integer.class).getOrDefault(0);
            log.info("Duplicate detection requested for repository: {} with threshold: {}", name, threshold);

            if (activeDupeProcesses.containsKey("batch") || activeDupeProcesses.containsKey(name)) {
                ctx.status(409).json(Map.of("message", "Duplicate detection already running"));
                return;
            }

            var process = new DuplicateRepoProcess(
                    new CliParameter(),
                    List.of(name),
                    false,
                    infrastructureConfig.getDedupConfig(),
                    threshold,
                    DuplicateRepoProcess.DupePrintMode.QUIET,
                    null, null, null, false, false,
                    fileSystem
            );

            WebDupeObserver observer = new WebDupeObserver(name, eventBus);
            process.withObserver(observer);

            activeDupeProcesses.put(name, process);
            dupeExecutor.execute(() -> {
                try {
                    process.findGroups();
                } catch (Exception e) {
                    log.error("Error during duplicate detection for {}", name, e);
                    observer.onError(name, "Duplicate detection failed: " + e.getMessage());
                } finally {
                    activeDupeProcesses.remove(name);
                }
            });

            ctx.status(202).json(Map.of("message", "Duplicate detection started for " + name));
        });

        app.post("/api/repos/dupes", ctx -> {
            List<String> names = ctx.bodyAsClass(List.class);
            Integer threshold = ctx.queryParamAsClass("threshold", Integer.class).getOrDefault(0);
            if (names == null || names.isEmpty()) {
                ctx.status(400).json(Map.of("message", "No repositories specified"));
                return;
            }
            log.info("Batch duplicate detection requested for repositories: {} with threshold: {}", names, threshold);

            if (activeDupeProcesses.containsKey("batch")) {
                ctx.status(409).json(Map.of("message", "Batch duplicate detection already running"));
                return;
            }

            var process = new DuplicateRepoProcess(
                    new CliParameter(),
                    names,
                    false,
                    infrastructureConfig.getDedupConfig(),
                    threshold,
                    DuplicateRepoProcess.DupePrintMode.QUIET,
                    null, null, null, false, false,
                    fileSystem
            );

            WebDupeObserver observer = new WebDupeObserver("batch", eventBus);
            process.withObserver(observer);

            activeDupeProcesses.put("batch", process);
            dupeExecutor.execute(() -> {
                try {
                    process.findGroups();
                } catch (Exception e) {
                    log.error("Error during batch duplicate detection", e);
                    observer.onError("batch", "Batch duplicate detection failed: " + e.getMessage());
                } finally {
                    activeDupeProcesses.remove("batch");
                }
            });

            ctx.status(202).json(Map.of("message", "Batch duplicate detection started"));
        });

        app.post("/api/diff/cp", ctx -> {
            var body = ctx.bodyAsClass(java.util.Map.class);
            List<String> sourceRepos = (List<String>) body.get("sourceRepos");
            String referenceRepo = (String) body.get("referenceRepo");
            String targetDir = (String) body.get("targetDir");
            String filter = (String) body.get("filter");

            if (sourceRepos == null || sourceRepos.isEmpty()) {
                ctx.status(400).json(Map.of("message", "No source repositories specified"));
                return;
            }
            if (referenceRepo == null || referenceRepo.isBlank()) {
                ctx.status(400).json(Map.of("message", "No reference repository specified"));
                return;
            }
            if (targetDir == null || targetDir.isBlank()) {
                ctx.status(400).json(Map.of("message", "No target directory specified"));
                return;
            }

            log.info("Diff copy requested: sources={}, reference={}, target={}, filter={}", sourceRepos, referenceRepo, targetDir, filter);

            String diffKey = "diff-cp-" + System.currentTimeMillis();
            CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
                activeDiffThreads.put(diffKey, Thread.currentThread());
                eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Diff copy starting...", "total", sourceRepos.size(), "completed", 0));
                int completed = 0;
                for (String source : sourceRepos) {
                    if (Thread.currentThread().isInterrupted()) {
                        eventBus.publish("diff-error", Map.of("key", diffKey, "message", "Diff copy cancelled"));
                        return;
                    }
                    try {
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Scanning diff for " + source + "...", "total", sourceRepos.size(), "completed", completed));
                        DiffProcess process = new DiffProcess(
                                new CliParameter(),
                                source,
                                referenceRepo,
                                infrastructureConfig.getDedupConfig(),
                                filter,
                                fileSystem
                        );
                        int result = process.copy(targetDir, false, progress ->
                                eventBus.publish("diff-progress", Map.of(
                                        "key", diffKey,
                                        "message", "Copying " + source + ": " + progress.completed() + "/" + progress.total() + " " + progress.currentFile(),
                                        "total", progress.total(),
                                        "completed", progress.completed()
                                ))
                        );
                        completed++;
                        if (result != 0) {
                            log.error("Diff copy failed for source={} reference={}: exit code {}", source, referenceRepo, result);
                            eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Failed for " + source + " (exit " + result + ")", "total", sourceRepos.size(), "completed", completed));
                        } else {
                            log.info("Diff copy completed for source={} reference={}", source, referenceRepo);
                        }
                    } catch (Exception e) {
                        completed++;
                        log.error("Diff copy error for source={}", source, e);
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Error for " + source + ": " + e.getMessage(), "total", sourceRepos.size(), "completed", completed));
                    }
                }
                eventBus.publish("diff-finished", Map.of("key", diffKey, "message", "Diff copy completed", "total", sourceRepos.size(), "completed", completed));
            });
            activeDiffFutures.put(diffKey, future);
            future.whenComplete((v, ex) -> {
                activeDiffFutures.remove(diffKey);
                activeDiffThreads.remove(diffKey);
            });

            ctx.status(202).json(Map.of("message", "Diff copy started for " + sourceRepos.size() + " source(s)", "key", diffKey));
        });

        app.post("/api/diff/mv", ctx -> {
            var body = ctx.bodyAsClass(java.util.Map.class);
            List<String> sourceRepos = (List<String>) body.get("sourceRepos");
            String referenceRepo = (String) body.get("referenceRepo");
            String targetDir = (String) body.get("targetDir");
            String filter = (String) body.get("filter");

            if (sourceRepos == null || sourceRepos.isEmpty()) {
                ctx.status(400).json(Map.of("message", "No source repositories specified"));
                return;
            }
            if (referenceRepo == null || referenceRepo.isBlank()) {
                ctx.status(400).json(Map.of("message", "No reference repository specified"));
                return;
            }
            if (targetDir == null || targetDir.isBlank()) {
                ctx.status(400).json(Map.of("message", "No target directory specified"));
                return;
            }

            log.info("Diff move requested: sources={}, reference={}, target={}, filter={}", sourceRepos, referenceRepo, targetDir, filter);

            String diffKey = "diff-mv-" + System.currentTimeMillis();
            CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
                activeDiffThreads.put(diffKey, Thread.currentThread());
                eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Diff move starting...", "total", sourceRepos.size(), "completed", 0));
                int completed = 0;
                for (String source : sourceRepos) {
                    if (Thread.currentThread().isInterrupted()) {
                        eventBus.publish("diff-error", Map.of("key", diffKey, "message", "Diff move cancelled"));
                        return;
                    }
                    try {
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Scanning diff for " + source + "...", "total", sourceRepos.size(), "completed", completed));
                        DiffProcess process = new DiffProcess(
                                new CliParameter(),
                                source,
                                referenceRepo,
                                infrastructureConfig.getDedupConfig(),
                                filter,
                                fileSystem
                        );
                        int result = process.copy(targetDir, true, progress ->
                                eventBus.publish("diff-progress", Map.of(
                                        "key", diffKey,
                                        "message", "Moving " + source + ": " + progress.completed() + "/" + progress.total() + " " + progress.currentFile(),
                                        "total", progress.total(),
                                        "completed", progress.completed()
                                ))
                        );
                        completed++;
                        if (result != 0) {
                            log.error("Diff move failed for source={} reference={}: exit code {}", source, referenceRepo, result);
                            eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Failed for " + source + " (exit " + result + ")", "total", sourceRepos.size(), "completed", completed));
                        } else {
                            log.info("Diff move completed for source={} reference={}", source, referenceRepo);
                        }
                    } catch (Exception e) {
                        completed++;
                        log.error("Diff move error for source={}", source, e);
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Error for " + source + ": " + e.getMessage(), "total", sourceRepos.size(), "completed", completed));
                    }
                }
                eventBus.publish("diff-finished", Map.of("key", diffKey, "message", "Diff move completed", "total", sourceRepos.size(), "completed", completed));
            });
            activeDiffFutures.put(diffKey, future);
            future.whenComplete((v, ex) -> {
                activeDiffFutures.remove(diffKey);
                activeDiffThreads.remove(diffKey);
            });

            ctx.status(202).json(Map.of("message", "Diff move started for " + sourceRepos.size() + " source(s)", "key", diffKey));
        });

        app.post("/api/diff/rm", ctx -> {
            var body = ctx.bodyAsClass(java.util.Map.class);
            List<String> sourceRepos = (List<String>) body.get("sourceRepos");
            String referenceRepo = (String) body.get("referenceRepo");
            String filter = (String) body.get("filter");

            if (sourceRepos == null || sourceRepos.isEmpty()) {
                ctx.status(400).json(Map.of("message", "No source repositories specified"));
                return;
            }
            if (referenceRepo == null || referenceRepo.isBlank()) {
                ctx.status(400).json(Map.of("message", "No reference repository specified"));
                return;
            }

            log.info("Diff remove requested: sources={}, reference={}, filter={}", sourceRepos, referenceRepo, filter);

            String diffKey = "diff-rm-" + System.currentTimeMillis();
            CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
                activeDiffThreads.put(diffKey, Thread.currentThread());
                eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Diff remove starting...", "total", sourceRepos.size(), "completed", 0));
                int completed = 0;
                for (String source : sourceRepos) {
                    if (Thread.currentThread().isInterrupted()) {
                        eventBus.publish("diff-error", Map.of("key", diffKey, "message", "Diff remove cancelled"));
                        return;
                    }
                    try {
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Scanning diff for " + source + "...", "total", sourceRepos.size(), "completed", completed));
                        DiffProcess process = new DiffProcess(
                                new CliParameter(),
                                source,
                                referenceRepo,
                                infrastructureConfig.getDedupConfig(),
                                filter,
                                fileSystem
                        );
                        int result = process.delete();
                        completed++;
                        if (result != 0) {
                            log.error("Diff remove failed for source={} reference={}: exit code {}", source, referenceRepo, result);
                            eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Failed for " + source + " (exit " + result + ")", "total", sourceRepos.size(), "completed", completed));
                        } else {
                            log.info("Diff remove completed for source={} reference={}", source, referenceRepo);
                        }
                    } catch (Exception e) {
                        completed++;
                        log.error("Diff remove error for source={}", source, e);
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Error for " + source + ": " + e.getMessage(), "total", sourceRepos.size(), "completed", completed));
                    }
                }
                eventBus.publish("diff-finished", Map.of("key", diffKey, "message", "Diff remove completed", "total", sourceRepos.size(), "completed", completed));
            });
            activeDiffFutures.put(diffKey, future);
            future.whenComplete((v, ex) -> {
                activeDiffFutures.remove(diffKey);
                activeDiffThreads.remove(diffKey);
            });

            ctx.status(202).json(Map.of("message", "Diff remove started for " + sourceRepos.size() + " source(s)", "key", diffKey));
        });

        app.post("/api/files/cp", ctx -> {
            var body = ctx.bodyAsClass(java.util.Map.class);
            List<String> sourceRepos = (List<String>) body.get("sourceRepos");
            String targetRepo = (String) body.get("targetRepo");
            String filter = (String) body.get("filter");

            if (sourceRepos == null || sourceRepos.isEmpty()) {
                ctx.status(400).json(Map.of("message", "No source repositories specified"));
                return;
            }
            if (targetRepo == null || targetRepo.isBlank()) {
                ctx.status(400).json(Map.of("message", "No target repo specified"));
                return;
            }

            log.info("Files copy requested: sources={}, target={}, filter={}", sourceRepos, targetRepo, filter);

            String diffKey = "files-cp-" + System.currentTimeMillis();
            CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
                activeDiffThreads.put(diffKey, Thread.currentThread());
                eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Files copy starting...", "total", sourceRepos.size(), "completed", 0));
                int completed = 0;
                for (String source : sourceRepos) {
                    if (Thread.currentThread().isInterrupted()) {
                        eventBus.publish("diff-error", Map.of("key", diffKey, "message", "Files copy cancelled"));
                        return;
                    }
                    try {
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Copying files from " + source + "...", "total", sourceRepos.size(), "completed", completed));
                        FilesProcess process = new FilesProcess(
                                new CliParameter(),
                                source,
                                infrastructureConfig.getDedupConfig(),
                                filter,
                                fileSystem
                        );
                        int result = process.copy(targetRepo, false, null, progress ->
                                eventBus.publish("diff-progress", Map.of(
                                        "key", diffKey,
                                        "message", "Copying " + source + ": " + progress.completed() + "/" + progress.total() + " " + progress.currentFile(),
                                        "total", progress.total(),
                                        "completed", progress.completed()
                                ))
                        );
                        completed++;
                        if (result != 0) {
                            log.error("Files copy failed for source={}: exit code {}", source, result);
                            eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Failed for " + source + " (exit " + result + ")", "total", sourceRepos.size(), "completed", completed));
                        } else {
                            log.info("Files copy completed for source={}", source);
                        }
                    } catch (Exception e) {
                        completed++;
                        log.error("Files copy error for source={}", source, e);
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Error for " + source + ": " + e.getMessage(), "total", sourceRepos.size(), "completed", completed));
                    }
                }
                eventBus.publish("diff-finished", Map.of("key", diffKey, "message", "Files copy completed", "total", sourceRepos.size(), "completed", completed));
            });
            activeDiffFutures.put(diffKey, future);
            future.whenComplete((v, ex) -> {
                activeDiffFutures.remove(diffKey);
                activeDiffThreads.remove(diffKey);
            });

            ctx.status(202).json(Map.of("message", "Files copy started for " + sourceRepos.size() + " repo(s)", "key", diffKey));
        });

        app.post("/api/files/mv", ctx -> {
            var body = ctx.bodyAsClass(java.util.Map.class);
            List<String> sourceRepos = (List<String>) body.get("sourceRepos");
            String targetRepo = (String) body.get("targetRepo");
            String filter = (String) body.get("filter");

            if (sourceRepos == null || sourceRepos.isEmpty()) {
                ctx.status(400).json(Map.of("message", "No source repositories specified"));
                return;
            }
            if (targetRepo == null || targetRepo.isBlank()) {
                ctx.status(400).json(Map.of("message", "No target repo specified"));
                return;
            }

            log.info("Files move requested: sources={}, target={}, filter={}", sourceRepos, targetRepo, filter);

            String diffKey = "files-mv-" + System.currentTimeMillis();
            CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
                activeDiffThreads.put(diffKey, Thread.currentThread());
                eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Files move starting...", "total", sourceRepos.size(), "completed", 0));
                int completed = 0;
                for (String source : sourceRepos) {
                    if (Thread.currentThread().isInterrupted()) {
                        eventBus.publish("diff-error", Map.of("key", diffKey, "message", "Files move cancelled"));
                        return;
                    }
                    try {
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Moving files from " + source + "...", "total", sourceRepos.size(), "completed", completed));
                        FilesProcess process = new FilesProcess(
                                new CliParameter(),
                                source,
                                infrastructureConfig.getDedupConfig(),
                                filter,
                                fileSystem
                        );
                        int result = process.copy(targetRepo, true, null, progress ->
                                eventBus.publish("diff-progress", Map.of(
                                        "key", diffKey,
                                        "message", "Moving " + source + ": " + progress.completed() + "/" + progress.total() + " " + progress.currentFile(),
                                        "total", progress.total(),
                                        "completed", progress.completed()
                                ))
                        );
                        completed++;
                        if (result != 0) {
                            log.error("Files move failed for source={}: exit code {}", source, result);
                            eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Failed for " + source + " (exit " + result + ")", "total", sourceRepos.size(), "completed", completed));
                        } else {
                            log.info("Files move completed for source={}", source);
                        }
                    } catch (Exception e) {
                        completed++;
                        log.error("Files move error for source={}", source, e);
                        eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Error for " + source + ": " + e.getMessage(), "total", sourceRepos.size(), "completed", completed));
                    }
                }
                eventBus.publish("diff-finished", Map.of("key", diffKey, "message", "Files move completed", "total", sourceRepos.size(), "completed", completed));
            });
            activeDiffFutures.put(diffKey, future);
            future.whenComplete((v, ex) -> {
                activeDiffFutures.remove(diffKey);
                activeDiffThreads.remove(diffKey);
            });

            ctx.status(202).json(Map.of("message", "Files move started for " + sourceRepos.size() + " repo(s)", "key", diffKey));
        });

        app.post("/api/diff/sync", ctx -> {
            var body = ctx.bodyAsClass(java.util.Map.class);
            String sourceRepo = (String) body.get("sourceRepo");
            String targetRepo = (String) body.get("targetRepo");
            Boolean copyNew = body.get("copyNew") != null ? (Boolean) body.get("copyNew") : true;
            Boolean deleteMissing = body.get("deleteMissing") != null ? (Boolean) body.get("deleteMissing") : false;
            String filter = (String) body.get("filter");

            if (sourceRepo == null || sourceRepo.isBlank()) {
                ctx.status(400).json(Map.of("message", "No source repository specified"));
                return;
            }
            if (targetRepo == null || targetRepo.isBlank()) {
                ctx.status(400).json(Map.of("message", "No target repository specified"));
                return;
            }

            log.info("Diff sync requested: source={}, target={}, copyNew={}, deleteMissing={}, filter={}", sourceRepo, targetRepo, copyNew, deleteMissing, filter);

            String diffKey = "diff-sync-" + System.currentTimeMillis();
            CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
                activeDiffThreads.put(diffKey, Thread.currentThread());
                eventBus.publish("diff-progress", Map.of("key", diffKey, "message", "Sync starting...", "total", 1, "completed", 0));
                try {
                    DiffProcess process = new DiffProcess(
                            new CliParameter(),
                            sourceRepo,
                            targetRepo,
                            infrastructureConfig.getDedupConfig(),
                            filter,
                            fileSystem
                    );
                    int result = process.sync(copyNew, deleteMissing, progress ->
                            eventBus.publish("diff-progress", Map.of(
                                    "key", diffKey,
                                    "message", "Syncing: " + progress.completed() + "/" + progress.total() + " " + progress.currentFile(),
                                    "total", progress.total(),
                                    "completed", progress.completed()
                            ))
                    );
                    if (result != 0) {
                        log.error("Diff sync failed: exit code {}", result);
                        eventBus.publish("diff-error", Map.of("key", diffKey, "message", "Sync failed (exit " + result + ")"));
                    } else {
                        log.info("Diff sync completed: source={}, target={}", sourceRepo, targetRepo);
                        eventBus.publish("diff-finished", Map.of("key", diffKey, "message", "Sync completed", "total", 1, "completed", 1));
                    }
                } catch (Exception e) {
                    log.error("Diff sync error", e);
                    eventBus.publish("diff-error", Map.of("key", diffKey, "message", "Sync error: " + e.getMessage()));
                }
            });
            activeDiffFutures.put(diffKey, future);
            future.whenComplete((v, ex) -> {
                activeDiffFutures.remove(diffKey);
                activeDiffThreads.remove(diffKey);
            });

            ctx.status(202).json(Map.of("message", "Sync started", "key", diffKey));
        });

        app.post("/api/diff/cancel", ctx -> {
            String key = ctx.queryParam("key");
            if (key != null) {
                Thread thread = activeDiffThreads.get(key);
                if (thread != null) {
                    thread.interrupt();
                }
                activeDiffFutures.remove(key);
                activeDiffThreads.remove(key);
                eventBus.publish("diff-finished", Map.of("key", key, "message", "Cancelled"));
                ctx.status(202).json(Map.of("message", "Cancel requested"));
                return;
            }
            // Cancel all active diff operations
            activeDiffThreads.forEach((k, t) -> t.interrupt());
            activeDiffFutures.forEach((k, f) -> {
                eventBus.publish("diff-finished", Map.of("key", k, "message", "Cancelled"));
            });
            activeDiffFutures.clear();
            activeDiffThreads.clear();
            ctx.status(202).json(Map.of("message", "All diff operations cancelled"));
        });

        app.post("/api/repos/dupes/cancel", ctx -> {
            String name = ctx.queryParam("name");
            String key = name != null ? name : "batch";
            DuplicateRepoProcess process = activeDupeProcesses.get(key);
            if (process != null) {
                process.cancel();
                ctx.status(202).json(Map.of("message", "Cancel requested for " + key));
            } else {
                ctx.status(404).json(Map.of("message", "No active duplicate detection for " + key));
            }
        });
    }

    private void setupWebSockets() {
        app.ws("/events", ws -> {
            ws.onConnect(ctx -> {
                log.info("WebSocket connected");
                ctx.session.setIdleTimeout(java.time.Duration.ofMinutes(15));
                ctx.session.setMaxTextMessageSize(1024 * 1024 * 10); // 10MB

                // Replay last events
                eventBus.getLastEvents().forEach(event -> {
                    try {
                        ctx.send(infrastructureConfig.getObjectMapper().writeValueAsString(event));
                    } catch (Exception e) {
                        log.error("Error replaying event via WebSocket", e);
                    }
                });

                java.util.function.Consumer<EventBus.DedupEvent> listener = event -> {
                    if (ctx.session.isOpen()) {
                        try {
                            ctx.send(infrastructureConfig.getObjectMapper().writeValueAsString(event));
                        } catch (Exception e) {
                            log.error("Error sending event via WebSocket", e);
                        }
                    }
                };
                ctx.attribute("listener", listener);
                eventBus.subscribe(listener);
            });
            ws.onClose(ctx -> {
                log.info("WebSocket disconnected");
                java.util.function.Consumer<EventBus.DedupEvent> listener = ctx.attribute("listener");
                if (listener != null) {
                    eventBus.unsubscribe(listener);
                }
            });
        });
    }

    public void start(int port) {
        log.info("Starting UI Server on port {}", port);
        app.start(port);
    }

    public void stop() {
        updateExecutor.shutdown();
        app.stop();
    }
}
