package forge;

import java.awt.image.BufferedImage;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.util.ImageFetcher;
import forge.util.SwingImageFetcher;
import org.tinylog.Logger;

public abstract class CachedCardImage implements ImageFetcher.Callback {
    final CardView card;
    final Iterable<PlayerView> viewers;
    final int width;
    final int height;
    private final String imageKey;
    private boolean imageResolved;

    static final SwingImageFetcher fetcher = new SwingImageFetcher();

    public CachedCardImage(final CardView card, final Iterable<PlayerView> viewers, final int width, final int height) {
        this(card, viewers, width, height, card.getCurrentState().getImageKey(viewers));
    }

    public CachedCardImage(final CardView card, final Iterable<PlayerView> viewers, final int width, final int height,
            final String imageKey) {
        this.card = card;
        this.viewers = viewers;
        this.width = width;
        this.height = height;
        this.imageKey = imageKey;
        if (ImageCache.isSupportedImageSize(width, height)) {
            BufferedImage image = ImageCache.scaleImage(imageKey, width, height, false, card);
            imageResolved = image != null;
            if (image == null) {
                Logger.debug("Fetch due to missing key: " + imageKey + " for " + card);
                fetcher.fetchImage(imageKey, this);
            }
        }
    }

    public BufferedImage getImage() {
        return ImageCache.scaleImage(imageKey, width, height, true, card);
    }

    public final boolean isImageResolved() {
        return imageResolved;
    }

    protected final void markImageResolved() {
        imageResolved = true;
    }

    public abstract void onImageFetched();
}
