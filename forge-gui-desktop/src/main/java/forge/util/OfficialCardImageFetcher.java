package forge.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import forge.ImageKeys;
import forge.gui.FThreads;
import forge.item.PaperCard;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class OfficialCardImageFetcher {
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Chinese card images");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, Set<ImageFetcher.Callback>> PENDING = new HashMap<>();
    private static final Cache<String, Boolean> RETRY_LATER = CacheBuilder.newBuilder()
            .maximumSize(5000).expireAfterWrite(60, TimeUnit.SECONDS).build();

    private OfficialCardImageFetcher() { }

    static void fetch(PaperCard card, String imageKey, ImageFetcher.Callback callback) {
        FThreads.assertExecutedByEdt(true);
        String face = face(imageKey);
        String fullKey = "back".equals(face) ? card.getCardAltImageKey()
                : face.isEmpty() ? card.getCardImageKey() : ImageUtil.getImageKey(card, face, true);
        if (fullKey == null || fullKey.isEmpty()) {
            return;
        }
        String key = CardImagePolicy.officialImageKey(fullKey);
        if (RETRY_LATER.getIfPresent(key) != null) {
            return;
        }
        Set<ImageFetcher.Callback> observers = PENDING.get(key);
        if (observers != null) {
            observers.add(callback);
            return;
        }
        observers = new HashSet<>();
        observers.add(callback);
        PENDING.put(key, observers);
        WORKERS.execute(() -> {
            boolean saved = false;
            try {
                String url = new ScryfallCardImages().resolve(card, face);
                if (url != null) {
                    save(url, CardImagePolicy.officialImageFile(key).toPath());
                    saved = true;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("Could not fetch full card image for " + card.getName() + ": " + e.getMessage());
            } finally {
                final boolean success = saved;
                FThreads.invokeInEdtLater(() -> finish(key, success));
            }
        });
    }

    private static void finish(String key, boolean success) {
        Set<ImageFetcher.Callback> observers = PENDING.remove(key);
        if (!success) {
            RETRY_LATER.put(key, true);
            return;
        }
        if (observers != null) {
            for (ImageFetcher.Callback observer : observers) {
                if (observer != null) {
                    observer.onImageFetched();
                }
            }
        }
        // Thumbnail grids request images without a per-card callback. Repaint on the EDT
        // so their placeholder is replaced as soon as the scan is available.
        for (Window window : Window.getWindows()) {
            if (window.isShowing()) {
                window.repaint();
            }
        }
    }

    static String face(String imageKey) {
        if (imageKey.endsWith(ImageKeys.BACKFACE_POSTFIX)) { return "back"; }
        if (imageKey.endsWith(ImageKeys.SPECFACE_W)) { return "white"; }
        if (imageKey.endsWith(ImageKeys.SPECFACE_U)) { return "blue"; }
        if (imageKey.endsWith(ImageKeys.SPECFACE_B)) { return "black"; }
        if (imageKey.endsWith(ImageKeys.SPECFACE_R)) { return "red"; }
        if (imageKey.endsWith(ImageKeys.SPECFACE_G)) { return "green"; }
        return "";
    }

    static void save(String url, Path destination) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", ScryfallCardImages.USER_AGENT);
        connection.setRequestProperty("Accept", "image/*");
        BufferedImage image;
        byte[] downloaded;
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("Card image HTTP " + connection.getResponseCode());
            }
            try (InputStream stream = connection.getInputStream()) {
                downloaded = stream.readNBytes(20 * 1024 * 1024 + 1);
                if (downloaded.length > 20 * 1024 * 1024) {
                    throw new IOException("Card image exceeds 20 MB");
                }
                image = ImageIO.read(new ByteArrayInputStream(downloaded));
            }
            if (image == null || image.getWidth() < 32 || image.getHeight() < 32) {
                throw new IOException("Invalid card image");
            }
        } finally {
            connection.disconnect();
        }
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "card-image-", ".tmp");
        try {
            if (downloaded.length > 2 && downloaded[0] == (byte) 0xff && downloaded[1] == (byte) 0xd8) {
                Files.write(temporary, downloaded);
            } else {
                // PNG scans can have alpha; normalize only when JPEG conversion is necessary.
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgb.createGraphics();
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                graphics.drawImage(image, 0, 0, null);
                graphics.dispose();
                if (!ImageIO.write(rgb, "jpg", temporary.toFile())) {
                    throw new IOException("JPEG encoder unavailable");
                }
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
