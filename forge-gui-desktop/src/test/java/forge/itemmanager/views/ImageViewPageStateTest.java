package forge.itemmanager.views;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ImageViewPageStateTest {
    @Test
    public void largeResultSetStartsOnFirstFiftyItemPage() {
        final ImageViewPageState state = new ImageViewPageState();

        state.reset(518);

        Assert.assertTrue(state.isPaging());
        Assert.assertEquals(state.getPageNumber(), 1);
        Assert.assertEquals(state.getPageCount(), 11);
        Assert.assertEquals(state.getStartOffset(), 0);
        Assert.assertEquals(state.getPageItemCount(), 50);
        Assert.assertFalse(state.hasPreviousPage());
        Assert.assertTrue(state.hasNextPage());
    }

    @Test
    public void lastPageContainsOnlyRemainingItems() {
        final ImageViewPageState state = new ImageViewPageState();
        state.reset(518);

        while (state.nextPage()) {
            // Advance to the last page.
        }

        Assert.assertEquals(state.getPageNumber(), 11);
        Assert.assertEquals(state.getStartOffset(), 500);
        Assert.assertEquals(state.getPageItemCount(), 18);
        Assert.assertTrue(state.hasPreviousPage());
        Assert.assertFalse(state.hasNextPage());
    }

    @Test
    public void refreshResetsPageAndSmallResultsDoNotPage() {
        final ImageViewPageState state = new ImageViewPageState();
        state.reset(120);
        Assert.assertTrue(state.nextPage());

        state.reset(20);

        Assert.assertFalse(state.isPaging());
        Assert.assertEquals(state.getPageNumber(), 1);
        Assert.assertEquals(state.getPageCount(), 1);
        Assert.assertEquals(state.getPageItemCount(), 20);
        Assert.assertFalse(state.previousPage());
        Assert.assertFalse(state.nextPage());
    }
}
