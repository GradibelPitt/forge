package forge.arena;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Requires an installed browser and an interactive Windows desktop. */
public final class ChromiumWindowCheck {
    private static Object field(ChromiumWindow window, String name) throws Exception {
        var field = ChromiumWindow.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(window);
    }

    private static JsonObject call(ChromiumWindow window, String method, JsonObject parameters, String session) throws Exception {
        var command = ChromiumWindow.class.getDeclaredMethod("call", String.class, JsonObject.class, String.class);
        command.setAccessible(true);
        var worker = (ExecutorService) field(window, "worker");
        return worker.submit(() -> (JsonObject) command.invoke(window, method, parameters, session)).get(6, TimeUnit.SECONDS);
    }

    private static void check(boolean value, String detail) {
        if (!value) throw new AssertionError(detail);
    }

    private static void awaitConnected(ChromiumWindow window) throws Exception {
        for (int i = 0; i < 200 && !((Boolean) field(window, "connected")); i++) {
            if (!window.failure().isBlank()) throw new AssertionError(window.failure());
            Thread.sleep(100);
        }
        check((Boolean) field(window, "connected"), "CDP did not connect");
    }

    private static void awaitWindowState(ChromiumWindow window, String expected) throws Exception {
        var parameters = new JsonObject();
        parameters.addProperty("windowId", (Integer) field(window, "windowId"));
        for (int i = 0; i < 30; i++) {
            String actual = call(window, "Browser.getWindowBounds", parameters, null)
                    .getAsJsonObject("bounds").get("windowState").getAsString();
            if (expected.equals(actual)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Window did not become " + expected);
    }

    private static String commandLine(Process child) throws Exception {
        String result = child.info().commandLine().orElse("");
        if (!result.isBlank()) return result;
        // ProcessHandle.Info does not expose command lines on every Windows JDK.
        Path powershell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        var query = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-Command", "(Get-CimInstance Win32_Process -Filter 'ProcessId=" + child.pid() + "').CommandLine")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (!query.waitFor(8, TimeUnit.SECONDS)) {
            query.destroyForcibly();
            throw new AssertionError("Could not inspect the owned browser command line");
        }
        result = new String(query.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        check(query.exitValue() == 0 && !result.isBlank(), "Browser command line was unavailable");
        return result;
    }

    private static void closeAndWait(ChromiumWindow window) throws Exception {
        window.close();
        Process child = (Process) field(window, "process");
        if (child != null) check(child.waitFor(10, TimeUnit.SECONDS), "Owned browser survived close");
    }

    public static void main(String[] args) throws Exception {
        var pageLoads = new AtomicInteger();
        var engineCommands = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) pageLoads.incrementAndGet();
            if ("/command".equals(exchange.getRequestURI().getPath())) engineCommands.incrementAndGet();
            String html = "<!doctype html><title>Arena Chromium fixture</title>"
                    + "<body style='background:#123;color:white;font:36px sans-serif'>Chromium renderer check <b id='counter'></b>"
                    + "<script>window.frameCount=0;function tick(){counter.textContent=++frameCount;requestAnimationFrame(tick)}requestAnimationFrame(tick)</script>";
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html;charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String token = "fixture-token-11111111111111111111111111111111";
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/?embedded#" + token;
        var stopped = new AtomicInteger();
        var window = new ChromiumWindow(stopped::incrementAndGet);
        var sentinel = new ChromiumWindow(stopped::incrementAndGet);
        ChromiumWindow second = null, crashed = null, missing = null;
        String browserOverride = System.getProperty("forge.arena.browser.executable");
        try {
            sentinel.open(url);
            awaitConnected(sentinel);
            window.open(url);
            awaitConnected(window);
            Process child = (Process) field(window, "process");
            Path profile = (Path) field(window, "profile");
            String arguments = commandLine(child);
            check(!arguments.contains(token), "Token appeared in browser command line");
            check(arguments.contains("/desktop/blank"), "Browser did not start on the public placeholder");

            var expression = new JsonObject();
            expression.addProperty("expression", "document.getElementById('counter') !== null && typeof window.frameCount === 'number'");
            boolean loaded = false;
            for (int i = 0; i < 50 && !loaded; i++) {
                loaded = call(window, "Runtime.evaluate", expression, (String) field(window, "sessionId"))
                        .getAsJsonObject("result").get("value").getAsBoolean();
                Thread.sleep(100);
            }
            check(loaded, "Fixture page did not load");
            expression.addProperty("expression", "setTimeout(()=>{throw new Error('Probe '+location.href+' https://example.test/resource?private=hidden-value#hidden-fragment');},0)");
            call(window, "Runtime.evaluate", expression, (String) field(window, "sessionId"));
            for (int i = 0; i < 30 && ((String) field(window, "lastScriptFailure")).isBlank(); i++) Thread.sleep(100);
            String scriptFailure = (String) field(window, "lastScriptFailure");
            check(!scriptFailure.isBlank() && scriptFailure.length() <= 600, "JavaScript exception summary was not recorded");
            check(!scriptFailure.contains(token) && !scriptFailure.contains("hidden-value") && !scriptFailure.contains("hidden-fragment"), "JavaScript exception exposed private URL data");
            check(window.running() && stopped.get() == 0, "A page exception incorrectly closed the browser");
            int loadsBefore = pageLoads.get();
            window.reload();
            for (int i = 0; i < 30 && pageLoads.get() == loadsBefore; i++) Thread.sleep(100);
            Thread.sleep(300);
            check(pageLoads.get() == loadsBefore + 1 && engineCommands.get() == 0, "Manual reload repeated or sent an engine command");
            check(window.running() && stopped.get() == 0, "Manual reload stopped the match window");
            System.out.println("CHROMIUM_WINDOW_SCRIPT_DIAGNOSTICS_OK exceptionRedacted=true exceptionNonfatal=true manualReloads=1 engineCommands=0");
            window.setFullscreen(true);
            awaitWindowState(window, "fullscreen");
            check(window.isFullscreen(), "Fullscreen state was not exposed to native settings");
            window.update(true, 1);
            awaitWindowState(window, "minimized");
            check(window.isFullscreen(), "Native settings lost the fullscreen preference while minimized");
            window.update(false, 2);
            awaitWindowState(window, "fullscreen");
            window.setFullscreen(false);
            awaitWindowState(window, "normal");
            check(!window.isFullscreen(), "Fullscreen did not turn off");
            window.update(true, 3);
            awaitWindowState(window, "minimized");
            window.setFullscreen(true);
            check(window.isFullscreen(), "Native settings did not remember a pending fullscreen change");
            window.update(false, 4);
            awaitWindowState(window, "fullscreen");
            window.setFullscreen(false);
            awaitWindowState(window, "normal");
            int before = ((AtomicInteger) field(window, "commandId")).get();
            for (int i = 0; i < 100; i++) { window.update(false, 4); window.setFullscreen(false); }
            Thread.sleep(250);
            check(before == ((AtomicInteger) field(window, "commandId")).get(), "Unchanged state resent browser commands");
            closeAndWait(window);
            check(stopped.get() == 0, "Intentional close notified stopped");
            check(sentinel.running(), "Closing one session also terminated the other session");
            for (int i = 0; i < 50 && Files.exists(profile); i++) Thread.sleep(100);
            check(!Files.exists(profile), "Private browser profile was not cleaned");
            closeAndWait(sentinel);
            System.out.println("CHROMIUM_WINDOW_LIFECYCLE_OK nativeHandoff=true fullscreenRestored=true fullscreenChangedWhileNative=true deduplicated=true tokenNotInCommandline=true profileCleaned=true unrelatedSessionSurvived=true");

            second = new ChromiumWindow(stopped::incrementAndGet);
            second.open(url);
            awaitConnected(second);
            var close = new JsonObject();
            close.addProperty("targetId", (String) field(second, "targetId"));
            try { call(second, "Target.closeTarget", close, null); } catch (Exception disconnected) { }
            for (int i = 0; i < 100 && stopped.get() == 0; i++) Thread.sleep(100);
            check(stopped.get() == 1 && !second.running(), "Unexpected close did not notify exactly once");
            System.out.println("CHROMIUM_WINDOW_UNEXPECTED_EXIT_OK notifications=1");

            crashed = new ChromiumWindow(stopped::incrementAndGet);
            crashed.open(url);
            awaitConnected(crashed);
            try { call(crashed, "Page.crash", new JsonObject(), (String) field(crashed, "sessionId")); }
            catch (Exception disconnected) { }
            for (int i = 0; i < 150 && stopped.get() < 2; i++) Thread.sleep(100);
            check(stopped.get() == 2 && !crashed.running() && !crashed.failure().isBlank(), "Renderer crash did not stop the session with a retryable failure");
            System.out.println("CHROMIUM_WINDOW_RENDERER_CRASH_OK notifications=1 retryableFailure=true");

            System.setProperty("forge.arena.browser.executable", profile.resolve("missing-browser.exe").toString());
            missing = new ChromiumWindow(stopped::incrementAndGet);
            missing.open(url);
            for (int i = 0; i < 50 && stopped.get() < 3; i++) Thread.sleep(100);
            check(stopped.get() == 3 && !missing.running() && !missing.failure().isBlank(), "Startup failure did not reach retry UI");
            check(!missing.failure().contains(token), "Failure summary exposed the token");
            System.out.println("CHROMIUM_WINDOW_STARTUP_FAILURE_OK userMessage=true tokenRedacted=true");
        } finally {
            if (browserOverride == null) System.clearProperty("forge.arena.browser.executable");
            else System.setProperty("forge.arena.browser.executable", browserOverride);
            closeAndWait(window);
            closeAndWait(sentinel);
            if (second != null) closeAndWait(second);
            if (crashed != null) closeAndWait(crashed);
            if (missing != null) closeAndWait(missing);
            server.stop(0);
        }
    }
}
