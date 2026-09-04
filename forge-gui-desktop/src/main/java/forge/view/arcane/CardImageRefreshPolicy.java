package forge.view.arcane;

import java.util.Objects;

final class CardImageRefreshPolicy {
    private CardImageRefreshPolicy() {
    }

    static String selectImageKey(final boolean faceDown, final String visibleImageKey,
            final String faceDownImageKey) {
        return faceDown ? faceDownImageKey : visibleImageKey;
    }

    static boolean shouldRefresh(final String previousImageKey, final int previousWidth,
            final int previousHeight, final long previousCacheGeneration, final String imageKey,
            final int width, final int height, final long cacheGeneration, final boolean imageResolved) {
        return !imageResolved || previousWidth != width || previousHeight != height
                || previousCacheGeneration != cacheGeneration || !Objects.equals(previousImageKey, imageKey);
    }
}
