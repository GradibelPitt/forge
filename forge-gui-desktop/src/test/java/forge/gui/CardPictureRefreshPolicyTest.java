package forge.gui;

import java.awt.image.BufferedImage;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CardPictureRefreshPolicyTest {
    @Test
    public void identicalResolvedPreviewIsSkipped() {
        Assert.assertFalse(CardPictureRefreshPolicy.shouldRefresh(
                "card-key", true, false, 4,
                "card-key", true, false, 4,
                true));
    }

    @Test
    public void alternateFaceForcesPreviewRefresh() {
        Assert.assertTrue(CardPictureRefreshPolicy.shouldRefresh(
                "card-key", true, false, 4,
                "card-key", true, true, 4,
                true));
    }

    @Test
    public void visibilityChangeForcesPreviewRefresh() {
        Assert.assertTrue(CardPictureRefreshPolicy.shouldRefresh(
                "card-key", false, false, 4,
                "card-key", true, false, 4,
                true));
    }

    @Test
    public void unresolvedPreviewIsNeverSkipped() {
        Assert.assertTrue(CardPictureRefreshPolicy.shouldRefresh(
                "card-key", true, false, 4,
                "card-key", true, false, 4,
                false));
    }

    @Test
    public void clearedCacheForcesPreviewRefresh() {
        Assert.assertTrue(CardPictureRefreshPolicy.shouldRefresh(
                "card-key", true, false, 4,
                "card-key", true, false, 5,
                true));
    }

    @Test
    public void flipChangeReappliesEvenWhenScaledImageInstanceIsUnchanged() {
        final BufferedImage image = new BufferedImage(10, 14, BufferedImage.TYPE_INT_ARGB);

        Assert.assertTrue(CardPictureRefreshPolicy.shouldApplyImage(image, image, false, true));
    }

    @Test
    public void identicalImageAndOrientationNeedNoPanelUpdate() {
        final BufferedImage image = new BufferedImage(10, 14, BufferedImage.TYPE_INT_ARGB);

        Assert.assertFalse(CardPictureRefreshPolicy.shouldApplyImage(image, image, true, true));
    }
}
