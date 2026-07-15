package forge.gui;

import java.awt.Dimension;

final class CardPreviewSizePolicy {
    private static final int FALLBACK_WIDTH = 244;
    private static final int FALLBACK_HEIGHT = 340;

    private CardPreviewSizePolicy() {
    }

    static Dimension getThumbnailSize(final int panelWidth, final int panelHeight, final float screenScale) {
        final int logicalWidth = panelWidth >= 3 ? panelWidth : FALLBACK_WIDTH;
        final int logicalHeight = panelHeight >= 3 ? panelHeight : FALLBACK_HEIGHT;
        final float scale = screenScale > 0 ? screenScale : 1f;
        return new Dimension(Math.max(3, Math.round(logicalWidth * scale)),
                Math.max(3, Math.round(logicalHeight * scale)));
    }

    static String getRequestKey(final String imageKey, final Dimension thumbnailSize) {
        return getRequestKey(imageKey, thumbnailSize, null);
    }

    static String getRequestKey(final String imageKey, final Dimension thumbnailSize,
            final String decorationState) {
        if (imageKey == null) {
            return null;
        }
        final String previewKey = imageKey + "#preview@" + thumbnailSize.width + "x" + thumbnailSize.height;
        return decorationState == null || decorationState.isEmpty()
                ? previewKey : previewKey + "#" + decorationState;
    }
}
