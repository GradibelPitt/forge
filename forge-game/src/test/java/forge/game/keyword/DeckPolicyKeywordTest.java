package forge.game.keyword;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DeckPolicyKeywordTest {
    @Test
    public void parsesDeckPolicyAndGameRuleKeywords() {
        final KeywordInterface ignoreLimits = Keyword.getInstance("IgnoreDeckLimits");
        final KeywordInterface deckMinimum = Keyword.getInstance("DeckMinimum:31");
        final KeywordInterface gameRule = Keyword.getInstance("GameRule");

        Assert.assertTrue(ignoreLimits instanceof SimpleKeyword);
        Assert.assertTrue(deckMinimum instanceof KeywordWithAmount);
        Assert.assertEquals(deckMinimum.getAmount(), 31);
        Assert.assertTrue(gameRule instanceof SimpleKeyword);
        Assert.assertEquals(gameRule.getKeyword(), Keyword.GAME_RULE);
    }
}
