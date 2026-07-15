package forge.view.arcane;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CardPanelImageRefreshTest {
    @Test
    public void identicalResolvedImageRequestIsSkipped() {
        Assert.assertFalse(CardImageRefreshPolicy.shouldRefresh(
                "card-key", 200, 280, 4,
                "card-key", 200, 280, 4,
                true));
    }

    @Test
    public void changedImageKeyIsRefreshed() {
        Assert.assertTrue(CardImageRefreshPolicy.shouldRefresh(
                "hidden-card", 200, 280, 4,
                "downloaded-card", 200, 280, 4,
                true));
    }

    @Test
    public void changedImageSizeIsRefreshed() {
        Assert.assertTrue(CardImageRefreshPolicy.shouldRefresh(
                "card-key", 200, 280, 4,
                "card-key", 204, 286, 4,
                true));
    }

    @Test
    public void unresolvedImageIsNeverSkipped() {
        Assert.assertTrue(CardImageRefreshPolicy.shouldRefresh(
                "card-key", 200, 280, 4,
                "card-key", 200, 280, 4,
                false));
    }

    @Test
    public void clearedImageCacheForcesRefresh() {
        Assert.assertTrue(CardImageRefreshPolicy.shouldRefresh(
                "card-key", 200, 280, 4,
                "card-key", 200, 280, 5,
                true));
    }
}
