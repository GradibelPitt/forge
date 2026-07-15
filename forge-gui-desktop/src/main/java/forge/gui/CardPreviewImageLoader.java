package forge.gui;

import java.awt.image.BufferedImage;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

final class CardPreviewImageLoader {
    private CardPreviewImageLoader() {
    }

    static BufferedImage loadOnce(final Supplier<BufferedImage> originalLoader,
            final UnaryOperator<BufferedImage> decorator) {
        final BufferedImage original = originalLoader.get();
        return original == null ? null : decorator.apply(original);
    }
}
