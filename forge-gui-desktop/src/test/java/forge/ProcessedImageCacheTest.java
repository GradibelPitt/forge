package forge;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ProcessedImageCacheTest {
    @BeforeClass
    public void initializeGui() {
        if (forge.gui.GuiBase.getInterface() == null) {
            forge.gui.GuiBase.setInterface(new GuiDesktop());
        }
    }

    @Test
    public void resolvedProcessedImageIsCreatedOnlyOnce() {
        final Map<String, BufferedImage> cache = new HashMap<>();
        final AtomicInteger processCount = new AtomicInteger();

        final BufferedImage first = ProcessedImageCache.getOrCreate(cache, "card#rounded", true,
                () -> new BufferedImage(processCount.incrementAndGet(), 1, BufferedImage.TYPE_INT_ARGB));
        final BufferedImage second = ProcessedImageCache.getOrCreate(cache, "card#rounded", true,
                () -> new BufferedImage(processCount.incrementAndGet(), 1, BufferedImage.TYPE_INT_ARGB));

        Assert.assertSame(second, first);
        Assert.assertEquals(processCount.get(), 1);
    }

    @Test
    public void unresolvedProcessedImageIsNeverCached() {
        final Map<String, BufferedImage> cache = new HashMap<>();
        final AtomicInteger processCount = new AtomicInteger();

        ProcessedImageCache.getOrCreate(cache, "card#rounded", false,
                () -> new BufferedImage(processCount.incrementAndGet(), 1, BufferedImage.TYPE_INT_ARGB));
        ProcessedImageCache.getOrCreate(cache, "card#rounded", false,
                () -> new BufferedImage(processCount.incrementAndGet(), 1, BufferedImage.TYPE_INT_ARGB));

        Assert.assertTrue(cache.isEmpty());
        Assert.assertEquals(processCount.get(), 2);
    }

    @Test
    public void decoratedPreviewIsReusedByImageCacheFacade() {
        final AtomicInteger processCount = new AtomicInteger();
        final BufferedImage expected = new BufferedImage(20, 28, BufferedImage.TYPE_INT_ARGB);
        final String key = "test-preview#foil@2";

        final BufferedImage first = ImageCache.getOrCreateProcessedImage(key, () -> {
            processCount.incrementAndGet();
            return expected;
        });
        final BufferedImage second = ImageCache.getOrCreateProcessedImage(key, () -> {
            processCount.incrementAndGet();
            return new BufferedImage(20, 28, BufferedImage.TYPE_INT_ARGB);
        });

        Assert.assertSame(first, expected);
        Assert.assertSame(second, expected);
        Assert.assertEquals(processCount.get(), 1);
        ImageCache.clear();
    }
}
