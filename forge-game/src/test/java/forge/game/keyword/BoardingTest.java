package forge.game.keyword;

import org.testng.Assert;
import org.testng.annotations.Test;

public class BoardingTest {
    @Test
    public void parsesBoardingThresholdFromKeywordScript() {
        final KeywordInterface keyword = Keyword.getInstance("Boarding:3");

        Assert.assertTrue(keyword instanceof Boarding);
        Assert.assertEquals(keyword.getAmount(), 3);
    }

    @Test
    public void boardsOnlyWhenDistinctFriendlyDamageMeetsThreshold() {
        Assert.assertFalse(Boarding.hasMetThreshold(2, 3));
        Assert.assertTrue(Boarding.hasMetThreshold(3, 3));
        Assert.assertTrue(Boarding.hasMetThreshold(4, 3));
    }
}
