/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop_app.automation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class DesktopAutomationServer {
    private static final String AUTH_HEADER = "X-Bisq-Automation-Token";

    public enum ReadinessState {
        BOOTING,
        READY,
        SHUTTING_DOWN
    }

    public record Config(boolean enabled,
                         String bindHost,
                         int bindPort,
                         long fxTimeoutMs,
                         double defaultWidth,
                         double defaultHeight,
                         String token,
                         String artifactsDir) {
        public static Config from(com.typesafe.config.Config config) {
            return new Config(
                    config.getBoolean("enabled"),
                    config.getString("bind.host"),
                    config.getInt("bind.port"),
                    config.getLong("fx.timeoutMs"),
                    config.getDouble("window.width"),
                    config.getDouble("window.height"),
                    config.getString("token"),
                    config.getString("artifacts.dir")
            );
        }
    }

    private final Stage stage;
    private final HttpServer server;
    private final ExecutorService executor;
    @Nullable
    private final String token;
    private final Path artifactsDir;
    private final long fxTimeoutMs;
    private final double defaultWidth;
    private final double defaultHeight;
    private final AtomicReference<ReadinessState> readinessState = new AtomicReference<>(ReadinessState.BOOTING);

    private DesktopAutomationServer(Stage stage,
                                    HttpServer server,
                                    ExecutorService executor,
                                    @Nullable String token,
                                    Path artifactsDir,
                                    long fxTimeoutMs,
                                    double defaultWidth,
                                    double defaultHeight) {
        this.stage = stage;
        this.server = server;
        this.executor = executor;
        this.token = token;
        this.artifactsDir = artifactsDir;
        this.fxTimeoutMs = fxTimeoutMs;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    @Nullable
    public static DesktopAutomationServer maybeStart(Stage stage, Config config) {
        if (!config.enabled()) {
            return null;
        }

        String bindHost = config.bindHost();
        InetAddress bindAddress;
        try {
            bindAddress = InetAddress.getByName(bindHost);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid bind host: " + bindHost, e);
        }
        if (!bindAddress.isLoopbackAddress()) {
            throw new IllegalArgumentException("Desktop automation server must bind to loopback only. Invalid host: " + bindHost);
        }
        int bindPort = config.bindPort();
        long fxTimeoutMs = config.fxTimeoutMs();
        double defaultWidth = config.defaultWidth();
        double defaultHeight = config.defaultHeight();
        validateConfig(bindPort, fxTimeoutMs, defaultWidth, defaultHeight);
        String rawToken = config.token().trim();
        if (rawToken.isEmpty()) {
            throw new IllegalStateException("Desktop automation token is required when automation is enabled.");
        }
        String token = rawToken;
        String artifactsDirRaw = config.artifactsDir().trim();
        if (artifactsDirRaw.isEmpty()) {
            artifactsDirRaw = Path.of(System.getProperty("java.io.tmpdir"), "bisq2-ui-harness", "artifacts").toString();
        }
        Path artifactsDir = Path.of(artifactsDirRaw);

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, bindPort), 0);
            int maxThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
            ExecutorService executor = Executors.newFixedThreadPool(maxThreads, r -> {
                Thread t = new Thread(r);
                t.setName("desktop-automation-http");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            DesktopAutomationServer desktopAutomationServer = new DesktopAutomationServer(stage, server, executor,
                    token, artifactsDir, fxTimeoutMs, defaultWidth, defaultHeight);
            desktopAutomationServer.start();
            return desktopAutomationServer;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start DesktopAutomationServer", e);
        }
    }

    public void stop() {
        readinessState.set(ReadinessState.SHUTTING_DOWN);
        try {
            server.stop(0);
        } catch (Exception e) {
            log.warn("Error while stopping DesktopAutomationServer", e);
        }
        executor.shutdownNow();
    }

    private void start() throws Exception {
        readinessState.set(ReadinessState.BOOTING);
        Files.createDirectories(artifactsDir);
        applyDefaultWindowSize();

        server.createContext("/health", this::handleHealth);
        server.createContext("/nodes", this::handleNodes);
        server.createContext("/screenshot", this::handleScreenshot);
        server.createContext("/action/click", this::handleClick);
        server.createContext("/action/type", this::handleType);
        server.createContext("/action/pressKey", this::handlePressKey);
        server.createContext("/wait/node", this::handleWaitNode);

        server.start();
        readinessState.set(ReadinessState.READY);
        log.info("DesktopAutomationServer listening on {}:{} (artifacts={}, tokenProtected={})",
                server.getAddress().getHostString(),
                server.getAddress().getPort(),
                artifactsDir,
                token != null);
    }

    private void applyDefaultWindowSize() throws Exception {
        callOnFxThread(() -> {
            stage.setWidth(defaultWidth);
            stage.setHeight(defaultHeight);
            if (!stage.isShowing()) {
                stage.show();
            }
            stage.toFront();
            stage.requestFocus();
            return null;
        });
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        if (!isAuthorized(exchange)) {
            sendText(exchange, 401, "Unauthorized");
            return;
        }
        final boolean showing;
        final boolean sceneReady;
        try {
            boolean[] uiState = callOnFxThread(() -> new boolean[]{stage.isShowing(), stage.getScene() != null});
            showing = uiState[0];
            sceneReady = uiState[1];
        } catch (Exception e) {
            log.error("Failed to read UI state", e);
            sendText(exchange, 500, "Failed to read UI state: " + e.getMessage());
            return;
        }
        ReadinessState readiness = readinessState.get();
        String body = "{\"status\":\"ok\",\"showing\":" + showing
                + ",\"sceneReady\":" + sceneReady
                + ",\"readiness\":\"" + readiness + "\""
                + ",\"ts\":\"" + Instant.now() + "\"}";
        sendJson(exchange, 200, body);
    }

    private void handleNodes(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        if (!isAuthorized(exchange)) {
            sendText(exchange, 401, "Unauthorized");
            return;
        }
        if (!requireReady(exchange)) {
            return;
        }
        try {
            String body = callOnFxThread(this::buildNodeDump);
            sendText(exchange, 200, body);
        } catch (Exception e) {
            log.error("Failed to dump nodes", e);
            sendText(exchange, 500, "Failed to dump nodes: " + e.getMessage());
        }
    }

    private void handleScreenshot(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        if (!isAuthorized(exchange)) {
            sendText(exchange, 401, "Unauthorized");
            return;
        }
        if (!requireReady(exchange)) {
            return;
        }

        Map<String, String> query = parseQueryOrRespondBadRequest(exchange);
        if (query == null) {
            return;
        }
        String requestedName = query.getOrDefault("name", "shot");
        String sanitized = sanitizeFilePart(requestedName);
        String fileName = Instant.now().toEpochMilli() + "-" + sanitized + ".png";
        Path screenshotPath = artifactsDir.resolve(fileName).normalize();
        if (!screenshotPath.startsWith(artifactsDir)) {
            sendText(exchange, 400, "Invalid screenshot name");
            return;
        }

        try {
            WritableImage image = callOnFxThread(() -> {
                Scene scene = stage.getScene();
                if (scene == null || scene.getRoot() == null) {
                    return null;
                }
                return scene.snapshot(null);
            });

            if (image == null) {
                sendText(exchange, 409, "Scene not ready for screenshot");
                return;
            }

            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", screenshotPath.toFile());
            String body = "{\"status\":\"ok\",\"path\":\"" + jsonEscape(screenshotPath.toString()) + "\"}";
            sendJson(exchange, 200, body);
        } catch (Exception e) {
            log.error("Failed to create screenshot", e);
            sendText(exchange, 500, "Failed to create screenshot: " + e.getMessage());
        }
    }

    private void handleClick(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        if (!isAuthorized(exchange)) {
            sendText(exchange, 401, "Unauthorized");
            return;
        }
        if (!requireReady(exchange)) {
            return;
        }

        Map<String, String> query = parseQueryOrRespondBadRequest(exchange);
        if (query == null) {
            return;
        }
        String id = query.get("id");
        if (id == null || id.isBlank()) {
            sendText(exchange, 400, "Missing required query parameter: id");
            return;
        }

        try {
            boolean clicked = callOnFxThread(() -> clickById(id));
            if (!clicked) {
                sendText(exchange, 404, "Node not found or not clickable: #" + id);
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        } catch (Exception e) {
            log.error("Failed click action for id={}", id, e);
            sendText(exchange, 500, "Click failed: " + e.getMessage());
        }
    }

    private void handleType(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        if (!isAuthorized(exchange)) {
            sendText(exchange, 401, "Unauthorized");
            return;
        }
        if (!requireReady(exchange)) {
            return;
        }

        Map<String, String> query = parseQueryOrRespondBadRequest(exchange);
        if (query == null) {
            return;
        }
        String id = query.get("id");
        String text = query.get("text");
        if (id == null || id.isBlank()) {
            sendText(exchange, 400, "Missing required query parameter: id");
            return;
        }
        if (text == null) {
            sendText(exchange, 400, "Missing required query parameter: text");
            return;
        }

        try {
            boolean typed = callOnFxThread(() -> typeById(id, text));
            if (!typed) {
                sendText(exchange, 404, "Text input not found: #" + id);
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        } catch (Exception e) {
            log.error("Failed type action for id={}", id, e);
            sendText(exchange, 500, "Type failed: " + e.getMessage());
        }
    }

    private void handlePressKey(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        if (!isAuthorized(exchange)) {
            sendText(exchange, 401, "Unauthorized");
            return;
        }
        if (!requireReady(exchange)) {
            return;
        }

        Map<String, String> query = parseQueryOrRespondBadRequest(exchange);
        if (query == null) {
            return;
        }
        String key = query.get("key");
        String id = query.get("id");
        boolean shiftDown = parseBoolean(query.get("shift"), false);
        boolean controlDown = parseBoolean(query.get("ctrl"), false);
        boolean altDown = parseBoolean(query.get("alt"), false);
        boolean metaDown = parseBoolean(query.get("meta"), false);

        if (key == null || key.isBlank()) {
            sendText(exchange, 400, "Missing required query parameter: key");
            return;
        }

        try {
            boolean pressed = callOnFxThread(() ->
                    pressKey(key, id, shiftDown, controlDown, altDown, metaDown));
            if (!pressed) {
                sendText(exchange, 404, "Unable to dispatch key press");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        } catch (IllegalArgumentException e) {
            sendText(exchange, 400, e.getMessage());
        } catch (Exception e) {
            log.error("Failed pressKey action for key={}", key, e);
            sendText(exchange, 500, "pressKey failed: " + e.getMessage());
        }
    }

    private void handleWaitNode(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        if (!isAuthorized(exchange)) {
            sendText(exchange, 401, "Unauthorized");
            return;
        }
        if (!requireReady(exchange)) {
            return;
        }

        Map<String, String> query = parseQueryOrRespondBadRequest(exchange);
        if (query == null) {
            return;
        }
        String id = query.get("id");
        long timeoutMs = parseLong(query.get("timeoutMs"), 5000L);
        boolean requireVisible = parseBoolean(query.get("visible"), false);

        if (id == null || id.isBlank()) {
            sendText(exchange, 400, "Missing required query parameter: id");
            return;
        }
        if (timeoutMs < 1) {
            sendText(exchange, 400, "timeoutMs must be >= 1");
            return;
        }

        try {
            boolean found = waitForNode(id, timeoutMs, requireVisible);
            if (!found) {
                sendText(exchange, 408, "Timed out waiting for node #" + id);
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        } catch (Exception e) {
            log.error("Failed wait-node for id={}", id, e);
            sendText(exchange, 500, "wait-node failed: " + e.getMessage());
        }
    }

    private String buildNodeDump() {
        Scene scene = stage.getScene();
        if (scene == null || scene.getRoot() == null) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        collectNodeLines(scene.getRoot(), lines);
        return String.join("\n", lines);
    }

    private void collectNodeLines(Node node, List<String> lines) {
        String id = node.getId();
        if (id != null && !id.isBlank()) {
            String type = node.getClass().getSimpleName();
            String text = extractNodeText(node);
            lines.add(escapeTabsAndNewlines(id) + "\t" + type + "\t" + escapeTabsAndNewlines(text));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectNodeLines(child, lines);
            }
        }
    }

    private String extractNodeText(Node node) {
        if (node instanceof Labeled labeled) {
            return Objects.toString(labeled.getText(), "");
        }
        if (node instanceof TextInputControl textInputControl) {
            return Objects.toString(textInputControl.getText(), "");
        }
        if (node instanceof Text text) {
            return Objects.toString(text.getText(), "");
        }
        return "";
    }

    private boolean clickById(String id) {
        Node node = lookupNodeById(id);
        if (node == null) {
            return false;
        }
        return dispatchClick(node);
    }

    static boolean dispatchClick(Node node) {
        if (node instanceof ButtonBase buttonBase) {
            buttonBase.fire();
            return true;
        }
        node.requestFocus();
        node.fireEvent(createMouseEvent(MouseEvent.MOUSE_PRESSED, true));
        node.fireEvent(createMouseEvent(MouseEvent.MOUSE_RELEASED, false));
        node.fireEvent(createMouseEvent(MouseEvent.MOUSE_CLICKED, false));
        return true;
    }

    private static MouseEvent createMouseEvent(javafx.event.EventType<MouseEvent> eventType,
                                               boolean primaryButtonDown) {
        return new MouseEvent(eventType,
                0, 0,
                0, 0,
                MouseButton.PRIMARY,
                1,
                false, false, false, false,
                primaryButtonDown, false, false,
                false, false, false,
                null);
    }

    private boolean typeById(String id, String text) {
        Node node = lookupNodeById(id);
        if (!(node instanceof TextInputControl textInputControl)) {
            return false;
        }
        textInputControl.requestFocus();
        textInputControl.setText(text);
        return true;
    }

    private boolean pressKey(String key,
                             @Nullable String id,
                             boolean shiftDown,
                             boolean controlDown,
                             boolean altDown,
                             boolean metaDown) {
        Scene scene = stage.getScene();
        if (scene == null) {
            return false;
        }

        KeyCode keyCode;
        try {
            keyCode = KeyCode.valueOf(key.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown key code: " + key);
        }

        Node target;
        if (id != null && !id.isBlank()) {
            target = lookupNodeById(id);
            if (target == null) {
                return false;
            }
            target.requestFocus();
        } else {
            target = scene.getFocusOwner();
            if (target == null) {
                target = scene.getRoot();
                target.requestFocus();
            }
        }

        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", keyCode,
                shiftDown, controlDown, altDown, metaDown));
        target.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", keyCode,
                shiftDown, controlDown, altDown, metaDown));
        return true;
    }

    private boolean waitForNode(String id, long timeoutMs, boolean requireVisible) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean available = callOnFxThread(() -> isNodeAvailable(id, requireVisible));
            if (available) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
        return false;
    }

    private boolean isNodeAvailable(String id, boolean requireVisible) {
        Node node = lookupNodeById(id);
        if (node == null) {
            return false;
        }
        if (!requireVisible) {
            return true;
        }
        return node.isVisible() && node.getScene() != null;
    }

    @Nullable
    private Node lookupNodeById(String id) {
        Scene scene = stage.getScene();
        if (scene == null) {
            return null;
        }
        String selector = id.startsWith("#") ? id : "#" + id;
        return scene.lookup(selector);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        if (token == null) {
            return true;
        }
        String received = exchange.getRequestHeaders().getFirst(AUTH_HEADER);
        return token.equals(received);
    }

    private static boolean isMethod(HttpExchange exchange, String method) {
        return method.equalsIgnoreCase(exchange.getRequestMethod());
    }

    private boolean requireReady(HttpExchange exchange) throws IOException {
        ReadinessState readiness = readinessState.get();
        if (readiness == ReadinessState.READY) {
            return true;
        }
        sendJson(exchange, 409, "{\"status\":\"not_ready\",\"readiness\":\"" + readiness + "\"}");
        return false;
    }

    private static Map<String, String> parseQuery(URI uri) {
        String raw = uri.getRawQuery();
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key;
            String value;
            if (idx >= 0) {
                key = pair.substring(0, idx);
                value = pair.substring(idx + 1);
            } else {
                key = pair;
                value = "";
            }
            result.put(urlDecode(key), urlDecode(value));
        }
        return result;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Nullable
    private Map<String, String> parseQueryOrRespondBadRequest(HttpExchange exchange) throws IOException {
        try {
            return parseQuery(exchange.getRequestURI());
        } catch (IllegalArgumentException e) {
            sendText(exchange, 400, "Malformed query string");
            return null;
        }
    }

    private static boolean parseBoolean(@Nullable String raw, boolean defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return defaultValue;
        }
        return normalized.equals("1") ||
                normalized.equals("true") ||
                normalized.equals("yes") ||
                normalized.equals("on");
    }

    private static long parseLong(@Nullable String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String sanitizeFilePart(String input) {
        String sanitized = input.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return "shot";
        }
        return sanitized;
    }

    private static String escapeTabsAndNewlines(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String jsonEscape(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private <T> T callOnFxThread(Callable<T> callable) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return callable.call();
        }
        FutureTask<T> task = new FutureTask<>(callable);
        Platform.runLater(task);
        try {
            return task.get(fxTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(false);
            throw e;
        } catch (InterruptedException e) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private static void validateConfig(int bindPort,
                                       long fxTimeoutMs,
                                       double defaultWidth,
                                       double defaultHeight) {
        if (bindPort < 1 || bindPort > 65_535) {
            throw new IllegalArgumentException("Invalid automation bind port: " + bindPort);
        }
        if (fxTimeoutMs <= 0) {
            throw new IllegalArgumentException("Invalid automation fx timeout: " + fxTimeoutMs);
        }
        if (!Double.isFinite(defaultWidth) || defaultWidth <= 0) {
            throw new IllegalArgumentException("Invalid automation window width: " + defaultWidth);
        }
        if (!Double.isFinite(defaultHeight) || defaultHeight <= 0) {
            throw new IllegalArgumentException("Invalid automation window height: " + defaultHeight);
        }
    }

}
