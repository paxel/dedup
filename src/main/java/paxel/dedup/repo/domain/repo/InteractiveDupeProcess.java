package paxel.dedup.repo.domain.repo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import paxel.dedup.domain.model.Dimension;
import paxel.dedup.domain.model.MimetypeProvider;
import paxel.dedup.domain.model.Statistics;
import paxel.dedup.domain.model.errors.DedupError;
import paxel.dedup.domain.port.out.FileSystem;
import paxel.dedup.infrastructure.config.DedupConfig;
import paxel.lib.Result;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
@RequiredArgsConstructor
public class InteractiveDupeProcess {

    private final DedupConfig dedupConfig;
    private final FileSystem fileSystem;
    private final Integer threshold;
    private final CountDownLatch finishedLatch = new CountDownLatch(1);

    public void start(List<List<DuplicateRepoProcess.RepoRepoFile>> groups) {
        if (groups.isEmpty()) {
            log.info("No duplicates to show in interactive mode.");
            return;
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            int port = server.getAddress().getPort();

            server.createContext("/", new MainHandler(groups));
            server.createContext("/image", new ImageHandler());
            server.createContext("/delete", new DeleteHandler(groups, server));

            server.setExecutor(null);
            server.start();

            String url = "http://localhost:" + port + "/";
            log.info("Interactive mode started at {}", url);
            log.info("Waiting for user actions... (Press Ctrl+C in terminal to abort)");

            System.out.println("\nInteractive mode started at: " + url);
            System.out.println("If the browser did not open automatically, please copy and paste the URL above into your browser.\n");
            openBrowser(url);

            finishedLatch.await();
            log.info("Interactive mode finished.");
        } catch (IOException | InterruptedException e) {
            log.error("Failed to start interactive mode", e);
        }
    }

    private void openBrowser(String url) {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                log.info("Headless environment detected, skipping automatic browser opening.");
                return;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec("open " + url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    Runtime.getRuntime().exec("xdg-open " + url);
                }
            }
        } catch (Exception e) {
            log.warn("Could not open browser automatically: {}", e.getMessage());
        }
    }

    private class MainHandler implements HttpHandler {
        private final List<List<DuplicateRepoProcess.RepoRepoFile>> groups;

        public MainHandler(List<List<DuplicateRepoProcess.RepoRepoFile>> groups) {
            this.groups = groups;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head>");
            html.append("<title>Dedup Interactive Mode</title>");
            html.append("<style>");
            html.append("body { font-family: sans-serif; margin: 20px; background: #f0f0f0; }");
            html.append("h1 { color: #333; }");
            html.append(".groups-container { display: flex; flex-direction: column; gap: 30px; }");
            html.append(".group { background: white; padding: 15px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
            html.append(".group-header { margin-bottom: 10px; font-weight: bold; border-bottom: 1px solid #eee; padding-bottom: 5px; }");
            html.append(".files-scroll { display: flex; overflow-x: auto; gap: 15px; padding-bottom: 10px; }");
            html.append(".file-card { flex: 0 0 250px; border: 1px solid #ddd; padding: 10px; border-radius: 4px; display: flex; flex-direction: column; }");
            html.append(".file-card img { max-width: 100%; height: 200px; object-fit: contain; background: #eee; margin-bottom: 10px; }");
            html.append(".filmstrip { display: flex; gap: 2px; margin-bottom: 10px; }");
            html.append(".filmstrip img { flex: 1; height: 100px; object-fit: contain; background: #eee; }");
            html.append(".file-info { font-size: 0.8em; flex-grow: 1; }");
            html.append(".file-card.selected { border-color: #4CAF50; background-color: #e8f5e9; }");
            html.append(".filmstrip img { max-width: calc(33.3% - 2px); height: auto; }");
            html.append(".controls { margin-top: 10px; }");
            html.append(".delete-btn { background-color: #f44336; color: white; border: none; padding: 15px 32px; text-align: center; text-decoration: none; display: inline-block; font-size: 16px; margin: 4px 2px; cursor: pointer; border-radius: 8px; font-weight: bold; }");
            html.append(".sticky-footer { position: fixed; bottom: 0; left: 0; width: 100%; background: white; padding: 15px; box-shadow: 0 -2px 10px rgba(0,0,0,0.1); text-align: center; z-index: 100; }");
            html.append("</style></head><body>");

            html.append("<h1>Duplicate/Similar Files</h1>");
            html.append("<p>Select the files you want to <strong>KEEP</strong>. Unselected files will be deleted when you click the button below.</p>");

            html.append("<form id='deleteForm' action='/delete' method='POST'>");
            html.append("<div class='groups-container'>");

            for (int g = 0; g < groups.size(); g++) {
                List<DuplicateRepoProcess.RepoRepoFile> group = groups.get(g);
                html.append("<div class='group'>");
                html.append("<div class='group-header'>Group ").append(g + 1);
                if (threshold != null && threshold > 0) {
                    html.append(" (Similarity)");
                } else {
                    html.append(" (Exact Match)");
                }
                html.append("</div>");
                html.append("<div class='files-scroll'>");

                for (int f = 0; f < group.size(); f++) {
                    DuplicateRepoProcess.RepoRepoFile rrf = group.get(f);
                    String path = rrf.repo().absolutePath() + "/" + rrf.file().relativePath();
                    String encodedPath = Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8));

                    // Preselect the first one (biggest/oldest)
                    boolean preselected = (f == 0);
                    String selectedClass = preselected ? "selected" : "";
                    String checkedAttr = preselected ? "checked" : "";

                    html.append("<div class='file-card ").append(selectedClass).append("' id='card-").append(g).append("-").append(f).append("'>");

                    String mime = rrf.file().mimeType();
                    boolean isVideo = mime != null && mime.startsWith("video/");
                    boolean isAudio = mime != null && mime.startsWith("audio/");
                    boolean isPdf = mime != null && "application/pdf".equals(mime);
                    if (isVideo) {
                        // Generate a small filmstrip preview
                        try {
                            java.util.List<String> frames = new VideoFilmstripGenerator(fileSystem)
                                    .generateBase64Filmstrip(java.nio.file.Paths.get(path));
                            if (!frames.isEmpty()) {
                                html.append("<div class='filmstrip'>");
                                for (String b64 : frames) {
                                    html.append("<img src='data:image/jpeg;base64,").append(b64).append("' alt='frame'>");
                                }
                                html.append("</div>");
                            } else {
                                html.append("<div class='filmstrip'><div>No preview available</div></div>");
                            }
                        } catch (Exception e) {
                            html.append("<div class='filmstrip'><div>Preview error</div></div>");
                        }
                    } else if (isPdf) {
                        try {
                            String b64 = new PdfThumbnailGenerator(fileSystem)
                                    .generateBase64Thumbnail(java.nio.file.Paths.get(path));
                            if (b64 != null) {
                                html.append("<img src='data:image/jpeg;base64,").append(b64).append("' alt='pdf thumbnail'>");
                            } else {
                                html.append("<div class='filmstrip'><div>No preview available</div></div>");
                            }
                        } catch (Exception e) {
                            html.append("<div class='filmstrip'><div>Preview error</div></div>");
                        }
                    } else if (isAudio) {
                        html.append("<div style='margin-bottom: 10px; text-align: center;'>");
                        html.append("<audio controls style='width: 100%;'><source src='/image?path=").append(encodedPath).append("' type='").append(mime).append("'></audio>");
                        html.append("</div>");
                    } else {
                        html.append("<img src='/image?path=").append(encodedPath).append("' alt='thumbnail'>");
                    }

                    html.append("<div class='file-info'>");
                    html.append("<strong>Repo:</strong> ").append(rrf.repo().name()).append("<br>");
                    html.append("<strong>Path:</strong> ").append(rrf.file().relativePath()).append("<br>");
                    html.append("<strong>Size:</strong> ").append(formatSize(rrf.file().size())).append("<br>");
                    Dimension is = rrf.file().imageSize();
                    if (is != null)
                        html.append("<strong>Image:</strong> ").append(is).append("<br>");

                    if (rrf.file().attributes() != null && !rrf.file().attributes().isEmpty()) {
                        rrf.file().attributes().forEach((k, v) ->
                                html.append("<strong>").append(k).append(":</strong> ").append(v).append("<br>")
                        );
                    }

                    html.append("<strong>Modified:</strong> ").append(formatDate(rrf.file().lastModified())).append("<br>");
                    html.append("<a href='/image?path=").append(encodedPath).append("' target='_blank'>Open original</a>");
                    html.append("</div>");
                    html.append("<div class='controls'>");
                    html.append("<label><input type='checkbox' name='keep' value='").append(g).append(":").append(f).append("' ").append(checkedAttr);
                    html.append(" onchange='updateCardStyle(").append(g).append(", ").append(f).append(")'> Keep this file</label>");
                    html.append("</div>");
                    html.append("</div>");
                }
                html.append("</div></div>");
            }
            html.append("</div>");
            html.append("<div style='height: 100px;'></div>"); // Spacer for sticky footer
            html.append("<div class='sticky-footer'>");
            html.append("<button type='submit' class='delete-btn' onclick='return confirm(\"Are you sure you want to delete all UNSELECTED files?\")'>DELETE UNSELECTED FILES</button>");
            html.append("</div>");
            html.append("</form>");

            html.append("<script>");
            html.append("function updateCardStyle(g, f) {");
            html.append("  var card = document.getElementById('card-' + g + '-' + f);");
            html.append("  var checkbox = card.querySelector('input[type=\"checkbox\"]');");
            html.append("  if (checkbox.checked) card.classList.add('selected'); else card.classList.remove('selected');");
            html.append("}");
            html.append("</script>");

            html.append("</body></html>");

            byte[] response = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }

        private String formatSize(long size) {
            if (size < 1024) return size + " B";
            int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
            return String.format("%.1f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
        }

        private String formatDate(long lastModified) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(lastModified));
        }
    }

    private class ImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("path=")) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            String base64Path = query.substring(5);
            String pathStr = new String(Base64.getDecoder().decode(base64Path), StandardCharsets.UTF_8);
            Path path = Paths.get(pathStr);

            if (!fileSystem.exists(path)) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            String contentType = new MimetypeProvider().get(path).getValueOr("application/octet-stream");

            exchange.getResponseHeaders().set("Content-Type", contentType);
            // Use Transfer-Encoding: chunked or just readAllBytes if it's small? 
            // For videos, readAllBytes is BAD.
            long size = fileSystem.size(path);
            exchange.sendResponseHeaders(200, size);
            try (InputStream is = fileSystem.newInputStream(path);
                 OutputStream os = exchange.getResponseBody()) {
                is.transferTo(os);
            }
        }
    }

    private class DeleteHandler implements HttpHandler {
        private final List<List<DuplicateRepoProcess.RepoRepoFile>> groups;
        private final HttpServer server;

        public DeleteHandler(List<List<DuplicateRepoProcess.RepoRepoFile>> groups, HttpServer server) {
            this.groups = groups;
            this.server = server;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }

            // Read POST body
            Scanner s = new Scanner(exchange.getRequestBody(), StandardCharsets.UTF_8).useDelimiter("\\A");
            String body = s.hasNext() ? s.next() : "";

            Set<String> keepSet = new HashSet<>();
            String[] params = body.split("&");
            for (String param : params) {
                if (param.startsWith("keep=")) {
                    keepSet.add(URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8));
                }
            }

            int deletedCount = 0;
            for (int g = 0; g < groups.size(); g++) {
                List<DuplicateRepoProcess.RepoRepoFile> group = groups.get(g);
                for (int f = 0; f < group.size(); f++) {
                    String id = g + ":" + f;
                    if (!keepSet.contains(id)) {
                        DuplicateRepoProcess.RepoRepoFile rrf = group.get(f);
                        Path absolutePath = Paths.get(rrf.repo().absolutePath(), rrf.file().relativePath());
                        if (fileSystem.exists(absolutePath)) {
                            try {
                                fileSystem.delete(absolutePath);
                                log.info("Deleted: {}", absolutePath);
                                updateRepoIndex(rrf);
                                deletedCount++;
                            } catch (IOException e) {
                                log.error("Failed to delete {}: {}", absolutePath, e.getMessage());
                            }
                        }
                    }
                }
            }

            StringBuilder response = new StringBuilder();
            response.append("<!DOCTYPE html><html><body>");
            response.append("<h1>Deletion Complete</h1>");
            response.append("<p>Successfully deleted ").append(deletedCount).append(" files.</p>");
            response.append("<p>You can close this window now. The CLI will finish shortly.</p>");
            response.append("</body></html>");

            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

            exchange.close();

            // Finish the process
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    server.stop(0);
                    finishedLatch.countDown();
                } catch (InterruptedException ignored) {
                }
            }).start();
        }

        private void updateRepoIndex(DuplicateRepoProcess.RepoRepoFile rrf) {
            RepoManager rm = RepoManager.forRepo(rrf.repo(), dedupConfig, fileSystem);
            Result<Statistics, DedupError> loadResult = rm.load();
            if (loadResult.isSuccess()) {
                rm.addRepoFile(rrf.file().withMissing(true));
                rm.close();
            } else {
                log.error("Failed to load repo index for {} during update after interactive delete", rrf.repo().name());
            }
        }
    }
}
