package forge.itemmanager.views;

import org.testng.Assert;
import org.testng.annotations.Test;

public class IncrementalImageLoadStateTest {
    @Test
    public void initialBatchCapsLargeCatalogAtFiftyItems() {
        final IncrementalImageLoadState state = new IncrementalImageLoadState();

        state.reset(94_503, true);

        Assert.assertEquals(state.claimNextBatch(), 50);
        Assert.assertEquals(state.getLoadedCount(), 50);
        Assert.assertTrue(state.hasMore());
    }

    @Test
    public void subsequentBatchesStopAtCatalogSize() {
        final IncrementalImageLoadState state = new IncrementalImageLoadState();

        state.reset(120, true);

        Assert.assertEquals(state.claimNextBatch(), 50);
        Assert.assertEquals(state.claimNextBatch(), 50);
        Assert.assertEquals(state.claimNextBatch(), 20);
        Assert.assertEquals(state.claimNextBatch(), 0);
        Assert.assertEquals(state.getLoadedCount(), 120);
        Assert.assertFalse(state.hasMore());
    }

    @Test
    public void eagerModeClaimsTheWholeCatalog() {
        final IncrementalImageLoadState state = new IncrementalImageLoadState();

        state.reset(120, false);

        Assert.assertEquals(state.claimNextBatch(), 120);
        Assert.assertFalse(state.hasMore());
    }

    @Test
    public void scrollingWithinHalfAViewportOfBottomRequestsMore() {
        Assert.assertFalse(IncrementalImageLoadState.isNearEnd(400, 200, 1_000));
        Assert.assertTrue(IncrementalImageLoadState.isNearEnd(700, 200, 1_000));
        Assert.assertFalse(IncrementalImageLoadState.shouldLoadMore(700, 700, 200, 1_000));
        Assert.assertFalse(IncrementalImageLoadState.shouldLoadMore(750, 700, 200, 1_000));
        Assert.assertTrue(IncrementalImageLoadState.shouldLoadMore(650, 700, 200, 1_000));
    }
}
