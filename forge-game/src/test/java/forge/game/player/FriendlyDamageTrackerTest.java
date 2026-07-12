package forge.game.player;

import org.testng.Assert;
import org.testng.annotations.Test;

public class FriendlyDamageTrackerTest {
    @Test
    public void recordsDistinctEntityIdsAndClearsAtTurnEnd() {
        final FriendlyDamageTracker tracker = new FriendlyDamageTracker();

        Assert.assertTrue(tracker.record(10));
        Assert.assertFalse(tracker.record(10));
        Assert.assertTrue(tracker.record(11));
        Assert.assertEquals(tracker.size(), 2);

        tracker.clear();
        Assert.assertEquals(tracker.size(), 0);
    }
}
