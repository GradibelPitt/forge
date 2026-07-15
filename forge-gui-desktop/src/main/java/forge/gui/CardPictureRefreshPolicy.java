package forge.gui;

import java.awt.image.BufferedImage;
import java.util.Objects;

final class CardPictureRefreshPolicy {
    private CardPictureRefreshPolicy() {
    }

    static boolean shouldRefresh(final String previousImageKey, final boolean previousMayView,
            final boolean previousFlipped, final long previousCacheGeneration, final String imageKey,
            final boolean mayView, final boolean flipped, final long cacheGeneration,
            final boolean imageResolved) {
        return !imageResolved || previousMayView != mayView || previousFlipped != flipped
                || previousCacheGeneration != cacheGeneration || !Objects.equals(previousImageKey, imageKey);
    }

    static boolean shouldApplyImage(final BufferedImage previousImage, final BufferedImage image,
            final boolean previousFlipped, final boolean flipped) {
        return image != null && (image != previousImage || previousFlipped != flipped);
    }
}
