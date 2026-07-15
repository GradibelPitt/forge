package forge.gui;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CardPreviewImageLoaderTest {
    @Test
    public void originalImageIsLoadedExactlyOnce() {
        final AtomicInteger loadCount = new AtomicInteger();
        final AtomicInteger decorateCount = new AtomicInteger();
        final BufferedImage expected = new BufferedImage(10, 14, BufferedImage.TYPE_INT_ARGB);

        final BufferedImage actual = CardPreviewImageLoader.loadOnce(() -> {
            loadCount.incrementAndGet();
            return expected;
        }, image -> {
            decorateCount.incrementAndGet();
            return image;
        });

        Assert.assertSame(actual, expected);
        Assert.assertEquals(loadCount.get(), 1);
        Assert.assertEquals(decorateCount.get(), 1);
    }

    @Test
    public void missingImageIsNotDecorated() {
        final AtomicInteger decorateCount = new AtomicInteger();

        final BufferedImage actual = CardPreviewImageLoader.loadOnce(() -> null, image -> {
            decorateCount.incrementAndGet();
            return image;
        });

        Assert.assertNull(actual);
        Assert.assertEquals(decorateCount.get(), 0);
    }
}
