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
            List<String> names = ctx.bodyAsClass(List.class);
            if (names == null || names.isEmpty()) {
                ctx.status(400).json(Map.of("message", "No repositories specified"));
                return;
            }
            log.info("Batch update requested for repositories: {}", names);
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
                            2,
                            infrastructureConfig.getDedupConfig(),
                            false,
                            false,
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
            log.info("Update requested for repository: {}", name);
            if (activeUpdates.containsKey(name)) {
                ctx.status(409).json(java.util.Map.of("message", "Update already running for " + name));
                return;
            }
            UpdateReposProcess process = new UpdateReposProcess(
                    new CliParameter(),
                    java.util.List.of(name),
                    false,
                    2,
                    infrastructureConfig.getDedupConfig(),
                    false, // progress (Terminal/Lanterna)
                    false,  // refreshFingerprints
                    infrastructureConfig.getFileSystem() // Explicitly pass fileSystem
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
