package forge.arena;

import com.sun.net.httpserver.HttpExchange;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.swing.SwingUtilities;

/** Bounds web texture sizes and keeps image work out of the state/command workers. */
final class ArenaImages implements AutoCloseable {
    record Reference(String key, long version) { }
    private record Variant(Reference source, int width) { }
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    private final Map<Variant, byte[]> cache = new LinkedHashMap<>(64, .75f, true);
    private long bytes;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), r -> { var t = new Thread(r, "Forge arena images"); t.setDaemon(true); return t; });

    void serve(HttpExchange exchange, Reference source, boolean preview) {
        try {
            workers.execute(() -> {
                try {
                    byte[] data = image(new Variant(source, preview ? 1000 : 320));
                    if (data == null) { empty(exchange, 404); return; }
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    exchange.getResponseHeaders().set("Cache-Control", "private, max-age=3600");
                    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                    exchange.sendResponseHeaders(200, data.length);
                    exchange.getResponseBody().write(data);
                } catch (Exception e) {
                    try { empty(exchange, 503); } catch (IOException ignored) { }
                } finally { exchange.close(); }
            });
        } catch (RejectedExecutionException e) {
            try { empty(exchange, 503); } catch (IOException ignored) { }
            exchange.close();
        }
    }

    private static void empty(HttpExchange exchange, int status) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, -1);
    }

    private byte[] image(Variant variant) throws Exception {
        synchronized (cache) {
            byte[] hit = cache.get(variant);
            if (hit != null) return hit;
        }
        // Desktop ImageCache owns Swing resources; resampling and encoding do not.
        var task = new FutureTask<>(() -> forge.ImageCache.getOriginalImage(variant.source.key(), false, null));
        SwingUtilities.invokeLater(task);
        BufferedImage face = task.get(6, TimeUnit.SECONDS);
        if (face == null) return null;
        int width = Math.min(face.getWidth(), variant.width);
        int height = Math.max(1, (int)Math.round((double)face.getHeight() * width / face.getWidth()));
        var scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = scaled.createGraphics();
        try {
            graphics.setColor(new Color(11, 15, 23)); graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(face, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        var output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(.94f);
            writer.write(null, new javax.imageio.IIOImage(scaled, null, null), parameters);
        } finally { writer.dispose(); }
        byte[] result = output.toByteArray();
        synchronized (cache) {
            byte[] existing = cache.put(variant, result);
            bytes += result.length - (existing == null ? 0 : existing.length);
            var entries = cache.entrySet().iterator();
            while ((bytes > MAX_BYTES || cache.size() > 256) && entries.hasNext()) {
                bytes -= entries.next().getValue().length; entries.remove();
            }
        }
        return result;
    }

    @Override public void close() {
        workers.shutdownNow();
        synchronized (cache) { cache.clear(); bytes = 0; }
    }
}
