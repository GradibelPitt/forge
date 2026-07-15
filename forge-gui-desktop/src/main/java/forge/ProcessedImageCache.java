package forge;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Supplier;

final class ProcessedImageCache {
    private ProcessedImageCache() {
    }

    static String roundedKey(final String imageKey, final int radius) {
        return imageKey + "#rounded@" + radius;
    }

    static BufferedImage getOrCreate(final Map<String, BufferedImage> cache, final String key,
            final boolean cacheable, final Supplier<BufferedImage> processor) {
        if (cacheable) {
            return cache.computeIfAbsent(key, unused -> processor.get());
        }
        return processor.get();
    }
}
