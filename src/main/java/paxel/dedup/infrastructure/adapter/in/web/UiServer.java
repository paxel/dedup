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

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class UiServer {

    private final Javalin app;
    private final RepoService repoService;
    private final EventBus eventBus;
    private final InfrastructureConfig infrastructureConfig;

    public UiServer(InfrastructureConfig infrastructureConfig) {
        this.infrastructureConfig = infrastructureConfig;
        this.repoService = infrastructureConfig.getRepoService();
        this.eventBus = infrastructureConfig.getEventBus();
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
        app.get("/api/repos", ctx -> {
            ctx.json(repoService.getRepos().value());
        });

        app.post("/api/repos", ctx -> {
            Repo repo = ctx.bodyAsClass(Repo.class);
            var result = repoService.createRepo(repo.name(), java.nio.file.Paths.get(repo.absolutePath()), repo.indices());
            if (result.isSuccess()) {
                ctx.status(201).json(result.value());
            } else {
                ctx.status(400).json(result.error());
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

        app.post("/api/repos/{name}/update", ctx -> {
            String name = ctx.pathParam("name");
            CompletableFuture.runAsync(() -> {
                UpdateReposProcess process = new UpdateReposProcess(
                        new CliParameter(),
                        List.of(name),
                        false,
                        2,
                        infrastructureConfig.getDedupConfig(),
                        false, // progress (Terminal/Lanterna)
                        false  // refreshFingerprints
                ).withEventBus(eventBus);
                process.update();
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
                eventBus.subscribe(event -> {
                    try {
                        ctx.send(infrastructureConfig.getObjectMapper().writeValueAsString(event));
                    } catch (Exception e) {
                        log.error("Error sending event via WebSocket", e);
                    }
                });
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
