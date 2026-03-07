package paxel.dedup.infrastructure.adapter.in.web;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.service.EventBus;
import paxel.dedup.domain.service.RepoService;
import paxel.dedup.infrastructure.config.InfrastructureConfig;
import paxel.dedup.repo.domain.repo.UpdateReposProcess;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class UiServer {

    private final Javalin app;
    private final RepoService repoService;
    private final EventBus eventBus;
    private final paxel.dedup.domain.port.out.FileSystem fileSystem;
    private final InfrastructureConfig infrastructureConfig;

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
        app.get("/api/repos", ctx -> ctx.json(repoService.getRepos().value()));

        app.post("/api/repos", ctx -> {
            Repo repo = ctx.bodyAsClass(Repo.class);
            var result = repoService.createRepo(repo.name(), java.nio.file.Paths.get(repo.absolutePath()), repo.indices(), repo.codec(), repo.compressed());
            if (result.isSuccess()) {
                ctx.status(201).json(result.value());
            } else {
                ctx.status(400).json(result.error());
            }
        });

        app.get("/api/utils/browse", ctx -> {
            String currentPath = ctx.queryParam("path");
            Path root = currentPath != null && !currentPath.isBlank() ? Paths.get(currentPath) : Paths.get(System.getProperty("user.home"));

            if (!fileSystem.exists(root) || !fileSystem.isDirectory(root)) {
                root = Paths.get(System.getProperty("user.home"));
            }

            final Path finalRoot = root.toAbsolutePath().normalize();
            try (var stream = fileSystem.list(finalRoot)) {
                List<Map<String, Object>> items = stream
                        .filter(fileSystem::isDirectory)
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

        app.post("/api/repos/{name}/update", ctx -> {
            String name = ctx.pathParam("name");
            log.info("Update requested for repository: {}", name);
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Starting background update process for: {}", name);
                    UpdateReposProcess process = new UpdateReposProcess(
                            new CliParameter(),
                            List.of(name),
                            false,
                            2,
                            infrastructureConfig.getDedupConfig(),
                            false, // progress (Terminal/Lanterna)
                            false,  // refreshFingerprints
                            infrastructureConfig.getFileSystem() // Explicitly pass fileSystem
                    ).withEventBus(eventBus);
                    var result = process.update();
                    if (result.hasFailed()) {
                        log.error("Update failed for {}: {}", name, result.error().describe());
                        eventBus.publish("error", Map.of("repo", name, "message", result.error().describe()));
                    } else {
                        log.info("Update completed successfully for: {}", name);
                    }
                } catch (Exception e) {
                    log.error("Critical error during update for {}", name, e);
                    eventBus.publish("error", Map.of("repo", name, "message", e.getMessage()));
                }
            });
            ctx.status(202).json(java.util.Map.of("message", "Update started for " + name));
        });

        app.get("/api/repos/{name}/dupes", ctx -> {
            String name = ctx.pathParam("name");
            var repoResult = repoService.getRepo(name);
            if (repoResult.hasFailed()) {
                ctx.status(404).json(repoResult.error());
                return;
            }

            // Using DuplicateRepoProcess to find dupes
            // We use threshold 0 (exact) and PRINT mode (quiet since we want the result list)
            var process = new paxel.dedup.repo.domain.repo.DuplicateRepoProcess(
                    new CliParameter(),
                    List.of(name),
                    false,
                    infrastructureConfig.getDedupConfig(),
                    0, // threshold
                    paxel.dedup.repo.domain.repo.DuplicateRepoProcess.DupePrintMode.QUIET,
                    null, null, null, false, false
            );

            // This is a bit tricky as dupes() returns a Result<Integer, DedupError>
            // We need to access the groups it found.
            // DuplicateRepoProcess doesn't expose the groups directly, it prints them or generates reports.
            // I might need to refactor DuplicateRepoProcess to return groups.
            ctx.json(process.findGroups());
        });
    }

    private void setupWebSockets() {
        app.ws("/events", ws -> {
            ws.onConnect(ctx -> {
                log.info("WebSocket connected");
                ctx.session.setIdleTimeout(java.time.Duration.ofMinutes(15));
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
        app.stop();
    }
}
