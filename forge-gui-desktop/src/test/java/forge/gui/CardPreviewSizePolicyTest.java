package forge.gui;

import java.awt.Dimension;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CardPreviewSizePolicyTest {
    @Test
    public void panelSizeIsConvertedToPhysicalThumbnailPixels() {
        final Dimension size = CardPreviewSizePolicy.getThumbnailSize(300, 420, 1.5f);

        Assert.assertEquals(size, new Dimension(450, 630));
    }

    @Test
    public void unlaidOutPanelUsesSmallFallbackThumbnail() {
        final Dimension size = CardPreviewSizePolicy.getThumbnailSize(0, 0, 1f);

        Assert.assertEquals(size, new Dimension(244, 340));
    }

    @Test
    public void thumbnailSizeIsPartOfPreviewRequestKey() {
        final String first = CardPreviewSizePolicy.getRequestKey("card-key", new Dimension(300, 420));
        final String resized = CardPreviewSizePolicy.getRequestKey("card-key", new Dimension(360, 504));

        Assert.assertNotEquals(resized, first);
        Assert.assertEquals(first, "card-key#preview@300x420");
    }

    @Test
    public void requestKeyIncludesDecorationState() {
        final Dimension size = new Dimension(300, 420);

        Assert.assertEquals(CardPreviewSizePolicy.getRequestKey("card-key", size, "foil@2:on"),
                "card-key#preview@300x420#foil@2:on");
    }
}
