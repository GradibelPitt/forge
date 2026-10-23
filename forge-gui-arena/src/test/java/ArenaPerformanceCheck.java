import com.google.gson.*;
import forge.arena.ArenaSurface;
import forge.neo.ui.TableScreen;
import javafx.application.Platform;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Measures the real WebView/FX queue while native image notifications arrive. */
public final class ArenaPerformanceCheck {
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(8, TimeUnit.SECONDS);
    }

    static CompletableFuture<Void> run(ArenaSurface surface, TableScreen table) {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(4000);
                var engine = surface.webView().getEngine();
                long threadId = fx(() -> Thread.currentThread().getId());
                var bean = ManagementFactory.getThreadMXBean();
                fx(() -> engine.executeScript("window.perfStats={sources:0,frames:[],last:0};" +
                        "window.perfObserver=new MutationObserver(ms=>{for(const m of ms)if(m.attributeName==='src')perfStats.sources++});" +
                        "perfObserver.observe(document.body,{subtree:true,attributes:true,attributeFilter:['src']});" +
                        "window.perfFrame=t=>{if(perfStats.last)perfStats.frames.push(t-perfStats.last);perfStats.last=t;requestAnimationFrame(perfFrame)};requestAnimationFrame(perfFrame);performance.clearResourceTimings();"));
                var delays = new ArrayList<Double>();
                long cpuStart = bean.getThreadCpuTime(threadId), start = System.nanoTime();
                for (int i = 0; i < 100; i++) {
                    long sent = System.nanoTime();
                    fx(() -> { if ((delays.size() % 4) == 0) forge.neo.card.CardImages.repaint(); return null; });
                    delays.add((System.nanoTime() - sent) / 1_000_000d);
                    Thread.sleep(80);
                }
                double cpuPercent = 100d * (bean.getThreadCpuTime(threadId) - cpuStart) / (System.nanoTime() - start);
                var stats = JsonParser.parseString(fx(() -> String.valueOf(engine.executeScript(
                        "JSON.stringify({...perfStats,images:performance.getEntriesByType('resource').filter(r=>r.name.includes('/image?')).length,cards:document.querySelectorAll('.forge-card').length})")))).getAsJsonObject();
                Collections.sort(delays);
                double p95 = delays.get((int)(delays.size() * .95)), max = delays.get(delays.size() - 1);
                var frames = new ArrayList<Double>();
                stats.getAsJsonArray("frames").forEach(v -> frames.add(v.getAsDouble()));
                Collections.sort(frames);
                double frame95 = frames.isEmpty() ? 9999 : frames.get((int)(frames.size() * .95));
                System.out.printf(Locale.ROOT, "ARENA_PERF cards=%d fxCpu=%.1f%% queueP95=%.1fms queueMax=%.1fms frameP95=%.1fms imageRequests=%d sourceMutations=%d%n",
                        stats.get("cards").getAsInt(), cpuPercent, p95, max, frame95, stats.get("images").getAsInt(), stats.get("sources").getAsInt());
                if (stats.get("sources").getAsInt() != 0) throw new AssertionError("Unchanged card images reloaded on native notifications");
                if (p95 > 100 || max > 750) throw new AssertionError("FX input queue stalled");

                URI url = URI.create(surface.url());
                String root = "http://" + url.getAuthority(), token = url.getFragment();
                var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                var stateRequest = HttpRequest.newBuilder(URI.create(root + "/state")).header("X-Forge-Token", token).build();
                var state = client.send(stateRequest, HttpResponse.BodyHandlers.ofString());
                String etag = state.headers().firstValue("ETag").orElseThrow(() -> new AssertionError("State has no revision validator"));
                var idle = client.send(HttpRequest.newBuilder(stateRequest.uri()).header("X-Forge-Token", token).header("If-None-Match", etag).build(), HttpResponse.BodyHandlers.ofString());
                if (idle.statusCode() != 304 || !idle.body().isEmpty()) throw new AssertionError("Unchanged state retransmitted");
                fx(() -> { table.setPrompt("Performance check: changed prompt"); return null; });
                Thread.sleep(300);
                var changed = client.send(HttpRequest.newBuilder(stateRequest.uri()).header("X-Forge-Token", token).header("If-None-Match", etag).build(), HttpResponse.BodyHandlers.ofString());
                if (changed.statusCode() != 200 || !changed.body().contains("Performance check: changed prompt")) throw new AssertionError("Changed prompt not delivered");
                String path = root + "/image?name=Lightning%20Bolt&token=" + token;
                var image = client.send(HttpRequest.newBuilder(URI.create(path)).build(), HttpResponse.BodyHandlers.ofByteArray());
                var face = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image.body()));
                if (face == null || face.getWidth() > 320 || !image.headers().firstValue("Cache-Control").orElse("").contains("max-age")) throw new AssertionError("Board image is not bounded/cacheable");
                var preview = client.send(HttpRequest.newBuilder(URI.create(path + "&size=preview")).build(), HttpResponse.BodyHandlers.ofByteArray());
                var full = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(preview.body()));
                if (full == null || full.getWidth() < 600) throw new AssertionError("High quality preview lost");
                System.out.println("ARENA_PERF_IDLE_REVISION_AND_IMAGE_CACHE_OK board=" + face.getWidth() + " preview=" + full.getWidth());

                var callbacks = new java.util.concurrent.atomic.AtomicInteger();
                fx(() -> { table.getActionBar().setButtons("Confirm", "Cancel", true, true); table.getActionBar().setOnOk(callbacks::incrementAndGet); return null; });
                Thread.sleep(300);
                var commands = new ArrayList<Double>();
                for (int n = 0; n < 10; n++) {
                    var view = JsonParser.parseString(client.send(stateRequest, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("room");
                    var command = new JsonObject();
                    command.addProperty("commandId", UUID.randomUUID().toString());
                    command.add("matchId", view.get("matchId")); command.add("playerId", view.get("playerId")); command.add("expectedRevision", view.get("revision"));
                    command.addProperty("operation", "ok"); command.add("parameters", new JsonObject());
                    var envelope = new JsonObject(); envelope.addProperty("type", "command"); envelope.add("command", command);
                    long sent = System.nanoTime();
                    var receipt = client.send(HttpRequest.newBuilder(URI.create(root + "/command")).header("X-Forge-Token", token).POST(HttpRequest.BodyPublishers.ofString(envelope.toString())).build(), HttpResponse.BodyHandlers.ofString());
                    commands.add((System.nanoTime() - sent) / 1_000_000d);
                    if (!receipt.body().contains("accepted") || callbacks.get() != n + 1) throw new AssertionError("Native input lost: " + receipt.body());
                }
                Collections.sort(commands);
                double commandMax = commands.get(commands.size() - 1);
                System.out.printf(Locale.ROOT, "ARENA_PERF_NATIVE_COMMANDS count=%d max=%.1fms%n", callbacks.get(), commandMax);
                if (commandMax > 250) throw new AssertionError("Native command round trip stalled");
                checkSingleImage(client, stateRequest);
            } catch (Exception e) { throw new CompletionException(e); }
        });
    }

    private static void checkSingleImage(HttpClient client, HttpRequest request) throws Exception {
        var before = JsonParser.parseString(client.send(request, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("room").getAsJsonObject("imageVersions");
        var field = forge.neo.card.CardImages.class.getDeclaredField("CACHE"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var cache = (com.google.common.cache.Cache<String, javafx.scene.image.Image>)field.get(null);
        String key = "c:Lightning Bolt|PF25|1";
        var original = cache.getIfPresent(key);
        if (original == null) throw new AssertionError("Fixture image missing from native cache");
        fx(() -> { cache.put(key, new javafx.scene.image.WritableImage(original.getPixelReader(), (int)original.getWidth(), (int)original.getHeight())); return null; });
        try {
            Thread.sleep(350);
            var after = JsonParser.parseString(client.send(request, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("room").getAsJsonObject("imageVersions");
            for (var entry : before.entrySet()) {
                boolean changed = !entry.getValue().equals(after.get(entry.getKey()));
                if (changed != entry.getKey().equals("Lightning Bolt")) throw new AssertionError("Wrong image invalidated: " + entry.getKey());
            }
            System.out.println("ARENA_SINGLE_IMAGE_INVALIDATION_OK");
        } finally { fx(() -> { cache.put(key, original); return null; }); }
    }
}
