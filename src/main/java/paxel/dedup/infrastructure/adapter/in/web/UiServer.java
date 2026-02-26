package paxel.dedup.infrastructure.adapter.in.web;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.domain.service.EventBus;
import paxel.dedup.domain.service.RepoService;
import paxel.dedup.infrastructure.config.InfrastructureConfig;

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
        });

        setupRoutes();
        setupWebSockets();
    }

    private void setupRoutes() {
        app.get("/api/repos", ctx -> {
            ctx.json(repoService.getRepos().value());
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
