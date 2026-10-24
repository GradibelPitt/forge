package forge.arena;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** A private Chromium app window. The Forge JVM continues to own the match. */
final class ChromiumWindow implements AutoCloseable {
    private record WindowState(boolean nativeDialog, long focusSequence, Boolean fullscreen) { }
    private final Runnable stopped;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Forge Chromium window"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean opened = new AtomicBoolean(), draining = new AtomicBoolean(), finished = new AtomicBoolean();
    private final AtomicInteger commandId = new AtomicInteger();
    private final Map<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private volatile Process process;
    private volatile WebSocket socket;
    private volatile boolean closing, connected;
    private volatile String failure = "", startupStep = "查找 Chrome 或 Edge";
    private volatile String lastScriptFailure = "";
    private volatile WindowState desired = new WindowState(false, 0, null);
    private volatile boolean fullscreen;
    private Path profile, sessionRoot;
    private String targetId, sessionId, restoreState = "normal", arenaUrl, arenaToken;
    private int windowId;
    private boolean minimized;
    private long appliedFocus = -1;
    private final Thread shutdownHook = new Thread(() -> {
        closing = true;
        Process child = process;
        if (child != null && child.isAlive()) child.destroy();
    }, "Forge Chromium shutdown");

    ChromiumWindow(Runnable stopped) { this.stopped = stopped; Runtime.getRuntime().addShutdownHook(shutdownHook); }

    void open(String url) {
        if (closing || !opened.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try { launch(url); }
            catch (Exception error) { fail("无法" + startupStep + "。请重试打开对局窗口。"); }
        });
    }

    synchronized void update(boolean nativeDialog, long focusSequence) {
        WindowState next = new WindowState(nativeDialog, focusSequence, desired.fullscreen());
        if (next.equals(desired)) return;
        desired = next;
        drain();
    }

    boolean running() { Process child = process; return !closing && child != null && child.isAlive(); }
    String failure() { return failure; }
    void reload() {
        if (!connected || closing) return;
        enqueue(() -> {
            if (!connected || closing) return;
            try {
                JsonObject parameters = new JsonObject(); parameters.addProperty("ignoreCache", false);
                call("Page.reload", parameters, sessionId);
                call("Page.bringToFront", new JsonObject(), sessionId);
            } catch (Exception error) { fail("无法重新载入对局画面。请重新打开对局窗口。"); }
        });
    }
    synchronized void setFullscreen(boolean value) {
        if (Boolean.valueOf(value).equals(desired.fullscreen())) return;
        desired = new WindowState(desired.nativeDialog(), desired.focusSequence(), value);
        drain();
    }
    boolean isFullscreen() { Boolean requested = desired.fullscreen(); return requested == null ? fullscreen : requested; }

    private void launch(String url) throws Exception {
        URI arena = URI.create(url);
        if (!"http".equals(arena.getScheme()) || !"127.0.0.1".equals(arena.getHost()) || arena.getPort() < 1 ||
                arena.getUserInfo() != null || !"/".equals(arena.getPath()) || arena.getFragment() == null) {
            throw new IOException("Invalid local match address");
        }
        arenaUrl = url; arenaToken = arena.getFragment();
        Path executable = browser();
        startupStep = "创建独立浏览器会话";
        sessionRoot = Path.of(System.getProperty("forge.arena.sessionDir",
                Path.of(System.getProperty("java.io.tmpdir"), "ForgeArena").toString()));
        Files.createDirectories(sessionRoot);
        sessionRoot = sessionRoot.toRealPath();
        profile = Files.createTempDirectory(sessionRoot, "match-").toRealPath();
        if (closing) { finish(false); return; }
        String bootstrap = arena.resolve("/desktop/blank").toString();
        List<String> arguments = new ArrayList<>(List.of(executable.toString(), "--user-data-dir=" + profile,
                "--remote-debugging-port=0", "--remote-debugging-address=127.0.0.1", "--app=" + bootstrap,
                "--no-first-run", "--no-default-browser-check", "--disable-background-mode", "--window-size=1600,1000"));
        startupStep = "启动 Chrome 或 Edge";
        process = new ProcessBuilder(arguments).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        process.onExit().thenRun(() -> enqueue(() -> finish(!closing)));
        Path portFile = profile.resolve("DevToolsActivePort");
        startupStep = "连接浏览器控制通道";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        List<String> address = List.of();
        while (!closing && process.isAlive() && System.nanoTime() < deadline) {
            if (Files.isRegularFile(portFile)) {
                address = Files.readAllLines(portFile);
                if (address.size() >= 2 && !address.get(0).isBlank() && address.get(1).startsWith("/devtools/browser/")) break;
            }
            Thread.sleep(100);
        }
        if (closing) { finish(false); return; }
        if (address.size() < 2) throw new IOException("Browser control was not available");
        int port = Integer.parseInt(address.get(0));
        if (port < 1 || port > 65535 || !address.get(1).matches("/devtools/browser/[a-zA-Z0-9-]+")) throw new IOException("Invalid browser control address");
        URI endpoint = URI.create("ws://127.0.0.1:" + port + address.get(1));
        socket = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(3)).buildAsync(endpoint, new Listener()).get(5, TimeUnit.SECONDS);
        startupStep = "识别独立对局窗口";
        long targetDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!closing && targetId == null && System.nanoTime() < targetDeadline) {
            JsonObject targets = call("Target.getTargets", new JsonObject(), null);
            for (var entry : targets.getAsJsonArray("targetInfos")) {
                JsonObject target = entry.getAsJsonObject();
                if ("page".equals(target.get("type").getAsString()) && bootstrap.equals(target.get("url").getAsString())) {
                    targetId = target.get("targetId").getAsString(); break;
                }
            }
            if (targetId == null) Thread.sleep(100);
        }
        if (targetId == null) throw new IOException("Private app window was not found");
        startupStep = "接管独立对局窗口";
        windowId = call("Browser.getWindowForTarget", object("targetId", targetId), null).get("windowId").getAsInt();
        JsonObject attach = object("targetId", targetId); attach.addProperty("flatten", true);
        sessionId = call("Target.attachToTarget", attach, null).get("sessionId").getAsString();
        call("Inspector.enable", new JsonObject(), sessionId);
        call("Runtime.enable", new JsonObject(), sessionId);
        JsonObject discover = new JsonObject(); discover.addProperty("discover", true);
        call("Target.setDiscoverTargets", discover, null);
        startupStep = "载入对局画面";
        JsonObject result = call("Page.navigate", object("url", url), sessionId);
        if (result.has("errorText")) throw new IOException("Match page could not load");
        connected = true;
        apply();
        drain();
    }

    private static Path browser() throws IOException {
        String override = System.getProperty("forge.arena.browser.executable");
        if (override != null && !override.isBlank()) {
            Path executable = Path.of(override).toAbsolutePath().normalize();
            if (Files.isRegularFile(executable)) return executable;
            throw new IOException("Configured browser does not exist");
        }
        List<Path> choices = new ArrayList<>();
        for (String directory : List.of("ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA")) {
            String root = System.getenv(directory);
            if (root != null) choices.add(Path.of(root, "Google", "Chrome", "Application", "chrome.exe"));
        }
        for (String directory : List.of("ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA")) {
            String root = System.getenv(directory);
            if (root != null) choices.add(Path.of(root, "Microsoft", "Edge", "Application", "msedge.exe"));
        }
        for (Path candidate : choices) if (Files.isRegularFile(candidate)) return candidate;
        throw new IOException("Chrome or Microsoft Edge is required");
    }

    private void drain() {
        if (!connected || closing || !draining.compareAndSet(false, true)) return;
        enqueue(() -> {
            WindowState applied = desired;
            try { apply(); }
            catch (Exception error) { fail("对局窗口控制通道已中断。请重试打开对局窗口。"); }
            finally { draining.set(false); if (!applied.equals(desired)) drain(); }
        });
    }

    private void apply() throws Exception {
        if (closing) return;
        WindowState state = desired;
        if (state.nativeDialog() != minimized) {
            if (state.nativeDialog()) {
                JsonObject current = call("Browser.getWindowBounds", windowParameters(), null).getAsJsonObject("bounds");
                String previous = current.has("windowState") ? current.get("windowState").getAsString() : "normal";
                restoreState = "minimized".equals(previous) ? "normal" : previous;
                fullscreen = "fullscreen".equals(restoreState);
            }
            String destination = restoreState;
            if (state.fullscreen() != null) {
                if (state.fullscreen()) destination = "fullscreen";
                else if ("fullscreen".equals(destination)) destination = "normal";
            }
            setWindowState(state.nativeDialog() ? "minimized" : destination);
            if (!state.nativeDialog()) fullscreen = "fullscreen".equals(destination);
            minimized = state.nativeDialog();
        }
        if (!state.nativeDialog() && state.fullscreen() != null && state.fullscreen() != fullscreen) {
            setWindowState(state.fullscreen() ? "fullscreen" : "normal");
            fullscreen = state.fullscreen();
        }
        if (!state.nativeDialog() && appliedFocus != state.focusSequence()) {
            call("Page.bringToFront", new JsonObject(), sessionId);
            appliedFocus = state.focusSequence();
        }
    }

    private void setWindowState(String state) throws Exception {
        JsonObject parameters = windowParameters();
        parameters.add("bounds", object("windowState", "normal"));
        call("Browser.setWindowBounds", parameters, null);
        if (!"normal".equals(state)) {
            parameters.add("bounds", object("windowState", state));
            call("Browser.setWindowBounds", parameters, null);
        }
    }

    private JsonObject windowParameters() { JsonObject result = new JsonObject(); result.addProperty("windowId", windowId); return result; }
    private static JsonObject object(String key, String value) { JsonObject result = new JsonObject(); result.addProperty(key, value); return result; }

    private JsonObject call(String method, JsonObject parameters, String session) throws Exception {
        if (socket == null) throw new IOException("Browser control is disconnected");
        int id = commandId.incrementAndGet();
        JsonObject message = new JsonObject(); message.addProperty("id", id); message.addProperty("method", method); message.add("params", parameters);
        if (session != null) message.addProperty("sessionId", session);
        CompletableFuture<JsonObject> response = new CompletableFuture<>(); pending.put(id, response);
        try {
            socket.sendText(message.toString(), true).get(3, TimeUnit.SECONDS);
            return response.get(4, TimeUnit.SECONDS);
        } finally { pending.remove(id); }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder message = new StringBuilder();
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                try {
                    JsonObject value = JsonParser.parseString(message.toString()).getAsJsonObject();
                    if (value.has("id")) {
                        CompletableFuture<JsonObject> response = pending.get(value.get("id").getAsInt());
                        if (response != null) {
                            if (value.has("error")) response.completeExceptionally(new IOException("Browser command failed"));
                            else response.complete(value.has("result") ? value.getAsJsonObject("result") : new JsonObject());
                        }
                    } else if (value.has("method")) {
                        String method = value.get("method").getAsString();
                        JsonObject parameters = value.has("params") ? value.getAsJsonObject("params") : new JsonObject();
                        boolean currentTarget = parameters.has("targetId") && parameters.get("targetId").getAsString().equals(targetId);
                        boolean currentSession = value.has("sessionId") && value.get("sessionId").getAsString().equals(sessionId);
                        if ("Runtime.exceptionThrown".equals(method) && currentSession && parameters.has("exceptionDetails")) {
                            reportScriptFailure(parameters.getAsJsonObject("exceptionDetails"));
                        }
                        if ("Target.targetDestroyed".equals(method) && currentTarget) enqueue(() -> finish(!closing));
                        if (("Target.targetCrashed".equals(method) && currentTarget) ||
                                ("Inspector.targetCrashed".equals(method) && currentSession)) {
                            enqueue(() -> fail("对局画面进程已停止。请重试打开对局窗口。"));
                        }
                    }
                } catch (RuntimeException invalid) { disconnected(); }
                finally { message.setLength(0); }
            }
            webSocket.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket webSocket, int status, String reason) { disconnected(); return null; }
        @Override public void onError(WebSocket webSocket, Throwable error) { disconnected(); }
        private void disconnected() {
            pending.values().forEach(future -> future.completeExceptionally(new IOException("Browser disconnected")));
            enqueue(() -> finish(!closing));
        }
    }

    private void reportScriptFailure(JsonObject details) {
        String summary = details.has("text") ? details.get("text").getAsString() : "JavaScript exception";
        if (details.has("exception") && details.getAsJsonObject("exception").has("description")) {
            summary += ": " + details.getAsJsonObject("exception").get("description").getAsString();
        }
        if (arenaUrl != null) summary = summary.replace(arenaUrl, "[arena URL]");
        if (arenaToken != null && !arenaToken.isEmpty()) summary = summary.replace(arenaToken, "[redacted]");
        summary = summary.replaceAll("(?i)((?:https?|wss?|file)://[^\\s<>\"'?#]+)(?:[?#][^\\s<>\"']*)?", "$1")
                .replace('\r', ' ').replace('\n', ' ');
        if (summary.length() > 600) summary = summary.substring(0, 600);
        if (!summary.equals(lastScriptFailure)) {
            lastScriptFailure = summary;
            System.err.println("[arena] JavaScript error: " + summary);
        }
    }

    private void enqueue(Runnable action) {
        if (!worker.isShutdown()) try { worker.execute(action); } catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }

    private void fail(String message) { failure = message; if (!closing) System.err.println("[arena] " + message); finish(!closing); }

    private void finish(boolean unexpected) {
        if (!finished.compareAndSet(false, true)) return;
        closing = true; connected = false;
        if (socket != null) {
            try { call("Browser.close", new JsonObject(), null); } catch (Exception ignored) { }
            socket.abort();
        }
        Process child = process;
        if (child != null && child.isAlive()) {
            try {
                if (!child.waitFor(1500, TimeUnit.MILLISECONDS)) child.destroy();
                if (!child.waitFor(1500, TimeUnit.MILLISECONDS)) child.destroyForcibly();
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); child.destroy(); }
        }
        if (profile != null && profile.getParent().equals(sessionRoot) && profile.getFileName().toString().startsWith("match-")) {
            for (int attempt = 0; attempt < 5 && Files.exists(profile); attempt++) {
                try (var files = Files.walk(profile)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) try { Files.deleteIfExists(file); } catch (IOException ignored) { }
                } catch (IOException ignored) { }
                if (Files.exists(profile)) try { Thread.sleep(250); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        try { Runtime.getRuntime().removeShutdownHook(shutdownHook); } catch (IllegalStateException shuttingDown) { }
        worker.shutdown();
        if (unexpected) stopped.run();
    }

    @Override public void close() { closing = true; enqueue(() -> finish(false)); }
}
