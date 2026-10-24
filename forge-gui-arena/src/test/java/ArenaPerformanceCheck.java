import com.google.gson.*;
import forge.arena.ArenaSurface;
import forge.neo.ui.TableScreen;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Measures bridge delivery and bounded images, independently of renderer presentation. */
public final class ArenaPerformanceCheck {
    static CompletableFuture<Void> run(ArenaSurface surface, TableScreen table) {
        return CompletableFuture.runAsync(() -> {
            try {
                var session = new ArenaBridgeCheck.Session(surface);
                ArenaBridgeCheck.refresh(surface);
                var state = session.get("/state");
                String etag = state.headers().firstValue("ETag").orElseThrow();
                var idle = session.client.send(session.request("/state").header("If-None-Match", etag).build(), HttpResponse.BodyHandlers.ofString());
                if (idle.statusCode() != 304 || !idle.body().isEmpty()) throw new AssertionError("Unchanged state retransmitted");
                ArenaBridgeCheck.fx(() -> table.setPrompt("Performance check: changed prompt"));
                ArenaBridgeCheck.refresh(surface);
                var changed = session.client.send(session.request("/state").header("If-None-Match", etag).build(), HttpResponse.BodyHandlers.ofString());
                if (changed.statusCode() != 200 || !changed.body().contains("Performance check: changed prompt")) throw new AssertionError("Changed prompt not delivered");

                String path = "/image?name=Lightning%20Bolt";
                var image = session.client.send(session.request(path).build(), HttpResponse.BodyHandlers.ofByteArray());
                var face = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image.body()));
                if (face == null || face.getWidth() > 320 || !image.headers().firstValue("Cache-Control").orElse("").contains("max-age"))
                    throw new AssertionError("Board image is not bounded/cacheable");
                var preview = session.client.send(session.request(path + "&size=preview").build(), HttpResponse.BodyHandlers.ofByteArray());
                var full = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(preview.body()));
                if (full == null || full.getWidth() < 600) throw new AssertionError("High quality preview lost");
                System.out.println("ARENA_BRIDGE_IDLE_AND_IMAGE_CACHE_OK board=" + face.getWidth() + " preview=" + full.getWidth());

                var callbacks = new AtomicInteger();
                ArenaBridgeCheck.fx(() -> {
                    table.getActionBar().setButtons("Confirm", "Cancel", true, true);
                    table.getActionBar().setOnOk(callbacks::incrementAndGet);
                });
                ArenaBridgeCheck.refresh(surface);
                var delays = new ArrayList<Double>();
                for (int n = 0; n < 30; n++) {
                    var command = session.command("ok", new JsonObject());
                    long sent = System.nanoTime();
                    String status = session.send(command);
                    delays.add((System.nanoTime() - sent) / 1_000_000d);
                    if (!status.equals("accepted") || callbacks.get() != n + 1) throw new AssertionError("Native input lost: " + status);
                    if (n % 5 == 0) ArenaBridgeCheck.fx(table::refreshAll);
                }
                Collections.sort(delays);
                double p95 = delays.get((int)(delays.size() * .95)), max = delays.get(delays.size() - 1);
                System.out.printf(Locale.ROOT, "ARENA_BRIDGE_COMMAND_LATENCY count=%d p95=%.1fms max=%.1fms rendererNotTested=true%n", callbacks.get(), p95, max);
                if (p95 > 250 || max > 750) throw new AssertionError("Native command delivery stalled");
                ArenaBridgeCheck.fx(() -> ArenaBridgeCheck.assertNoLegacyBoard(table));
            } catch (Exception error) { throw new CompletionException(error); }
        });
    }
}
