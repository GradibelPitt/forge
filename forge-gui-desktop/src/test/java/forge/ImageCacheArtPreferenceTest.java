package forge;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ImageCacheArtPreferenceTest {
    private static final String FULL_KEY = "PH01/Card.full";
    private static final String BASE_FULL_KEY = "PH01/Base Card.full";
    private static final String CROP_KEY = "PH01/Card.artcrop";

    @BeforeClass
    public void initializeGui() {
        if (forge.gui.GuiBase.getInterface() == null) {
            forge.gui.GuiBase.setInterface(new GuiDesktop());
        }
    }

    @Test
    public void existingFullImageWinsWhenCropModeIsEnabled() {
        Pair<String, Boolean> selectedImage = ImageCache.resolvePreferredCardImageKey(
                FULL_KEY, BASE_FULL_KEY, true, false, FULL_KEY, key -> true);

        Assert.assertEquals(selectedImage.getLeft(), FULL_KEY);
        Assert.assertFalse(selectedImage.getRight());
    }

    @Test
    public void cropIsUsedOnlyWhenFullImageIsUnavailable() {
        Pair<String, Boolean> selectedImage = ImageCache.resolvePreferredCardImageKey(
                FULL_KEY, BASE_FULL_KEY, true, false, FULL_KEY, key -> false);

        Assert.assertEquals(selectedImage.getLeft(), CROP_KEY);
        Assert.assertTrue(selectedImage.getRight());
    }

    @Test
    public void disabledCropModeDoesNotProbeOrChangeTheFullImage() {
        AtomicInteger probes = new AtomicInteger();

        Pair<String, Boolean> selectedImage = ImageCache.resolvePreferredCardImageKey(
                FULL_KEY, BASE_FULL_KEY, false, false, FULL_KEY, key -> {
                    probes.incrementAndGet();
                    return false;
                });

        Assert.assertEquals(selectedImage.getLeft(), FULL_KEY);
        Assert.assertFalse(selectedImage.getRight());
        Assert.assertEquals(probes.get(), 0);
    }

    @Test
    public void unrebalancedFullImageWinsBeforeCrop() {
        Pair<String, Boolean> selectedImage = ImageCache.resolvePreferredCardImageKey(
                "MH2/A-Unholy Heat.full", "MH2/Unholy Heat.full", true, false,
                "MH2/A-Unholy Heat.full", "MH2/Unholy Heat.full"::equals);

        Assert.assertEquals(selectedImage.getLeft(), "MH2/Unholy Heat.full");
        Assert.assertFalse(selectedImage.getRight());
    }

    @Test
    public void rebalancedImageKeyMapsToItsOriginalCardName() {
        Assert.assertEquals(ImageCache.getUnrebalancedImageKey(
                "MH2/A-Unholy Heat.full", true), "MH2/Unholy Heat.full");
        Assert.assertNull(ImageCache.getUnrebalancedImageKey(
                "MH2/A-Unholy Heat.full", false));
    }

    @Test
    public void flipCardFallsBackToFrontFaceCrop() {
        Pair<String, Boolean> selectedImage = ImageCache.resolvePreferredCardImageKey(
                "PH01/Card Back.full", null, true, true, FULL_KEY, key -> false);

        Assert.assertEquals(selectedImage.getLeft(), CROP_KEY);
        Assert.assertTrue(selectedImage.getRight());
    }
}
