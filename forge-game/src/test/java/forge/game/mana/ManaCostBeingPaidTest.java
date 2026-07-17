package forge.game.mana;

import org.testng.annotations.Test;
import org.testng.AssertJUnit;
import static forge.card.MagicColor.COLORLESS;
import static forge.card.MagicColor.GREEN;
import static forge.card.MagicColor.RED;
import static forge.card.MagicColor.WHITE;

import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostParser;
import forge.card.mana.ManaCostShard;

public class ManaCostBeingPaidTest {

    @Test
    public void testPayManaViaConvoke() {
        runConvokeTest("1 W W", new byte[] { WHITE, COLORLESS, WHITE }, new String[] { "{1}{W}{W}", "{1}{W}", "{W}" });
        runConvokeTest("1 W W", new byte[] { COLORLESS, WHITE, WHITE }, new String[] { "{1}{W}{W}", "{W}{W}", "{W}" });
        runConvokeTest("1 W W", new byte[] { GREEN, WHITE, WHITE }, new String[] { "{1}{W}{W}", "{W}{W}", "{W}" });
        runConvokeTest("1 W G", new byte[] { GREEN, RED, WHITE }, new String[] { "{1}{W}{G}", "{1}{W}", "{W}" });
    }

    @Test
    public void harmonyReductionRemovesColoredSymbolsBeforeGeneric() {
        final ManaCostBeingPaid oneGreen = createManaCostBeingPaid("1 G");
        oneGreen.decreaseHarmonyMana(2);
        AssertJUnit.assertTrue("{1}{G} reduced by 2 should be free", oneGreen.isPaid());

        final ManaCostBeingPaid doubleBlue = createManaCostBeingPaid("1 U U");
        doubleBlue.decreaseHarmonyMana(2);
        AssertJUnit.assertEquals("{1}", doubleBlue.toString());

        final ManaCostBeingPaid oneBlue = createManaCostBeingPaid("3 U");
        oneBlue.decreaseHarmonyMana(2);
        AssertJUnit.assertEquals("{2}", oneBlue.toString());
    }

    @Test
    public void harmonyReductionHandlesEveryColoredOptionShardAsOnePoint() {
        for (final String symbol : new String[] {
                "W", "W/U", "2/W", "C/W", "W/P", "W/U/P"
        }) {
            final ManaCostBeingPaid cost = createManaCostBeingPaid(symbol);
            cost.decreaseHarmonyMana(1);
            AssertJUnit.assertTrue(symbol + " should be removed", cost.isPaid());
        }

        final ManaCostBeingPaid priority = createManaCostBeingPaid("W U/P");
        priority.decreaseHarmonyMana(1);
        AssertJUnit.assertEquals(0,
                priority.getUnpaidShards(ManaCostShard.WHITE));
        AssertJUnit.assertEquals(1,
                priority.getUnpaidShards(ManaCostShard.UP));
    }

    @Test
    public void harmonyReductionPreservesXSnowAndPureColorless() {
        final ManaCostBeingPaid cost = createManaCostBeingPaid("2 X S C U");
        cost.decreaseHarmonyMana(4);

        AssertJUnit.assertEquals(1, cost.getXcounter());
        AssertJUnit.assertEquals(1, cost.getUnpaidShards(ManaCostShard.S));
        AssertJUnit.assertEquals(1,
                cost.getUnpaidShards(ManaCostShard.COLORLESS));
        AssertJUnit.assertEquals(0, cost.getGenericManaAmount());
        AssertJUnit.assertEquals(0,
                cost.getUnpaidShards(ManaCostShard.BLUE));
    }

    @Test
    public void harmonyReductionUsesGenericBeforeColorRestrictedX() {
        final ManaCostBeingPaid cost = createManaCostBeingPaid("2 X U");
        cost.setXManaCostPaid(3, "U");
        cost.decreaseHarmonyMana(2);

        AssertJUnit.assertEquals(0, cost.getXcounter());
        AssertJUnit.assertEquals(3,
                cost.getUnpaidShards(ManaCostShard.BLUE));
        AssertJUnit.assertEquals(1, cost.getGenericManaAmount());
    }

    @Test
    public void harmonyReductionUsesChosenXAfterColoredSymbols() {
        final ManaCostBeingPaid cost = createManaCostBeingPaid("X R");
        cost.setXManaCostPaid(5, "");
        cost.decreaseHarmonyMana(2);

        AssertJUnit.assertEquals(0, cost.getXcounter());
        AssertJUnit.assertEquals(0,
                cost.getUnpaidShards(ManaCostShard.RED));
        AssertJUnit.assertEquals(4, cost.getGenericManaAmount());
    }

    @Test
    public void harmonyReductionCanReduceChosenColorRestrictedX() {
        final ManaCostBeingPaid cost = createManaCostBeingPaid("X R");
        cost.setXManaCostPaid(5, "U");
        cost.decreaseHarmonyMana(2);

        AssertJUnit.assertEquals(0, cost.getXcounter());
        AssertJUnit.assertEquals(0,
                cost.getUnpaidShards(ManaCostShard.RED));
        AssertJUnit.assertEquals(4,
                cost.getUnpaidShards(ManaCostShard.BLUE));
    }

    private void runConvokeTest(String initialCost, byte[] colorsToPay, String[] expectedRemainder) {
        ManaCostBeingPaid costBeingPaid = createManaCostBeingPaid(initialCost);

        for (int i = 0; i < colorsToPay.length; i++) {
            AssertJUnit.assertEquals(expectedRemainder[i], costBeingPaid.toString());
            costBeingPaid.payManaViaConvoke(colorsToPay[i]);
        }

        AssertJUnit.assertEquals("0", costBeingPaid.toString());
    }

    private ManaCostBeingPaid createManaCostBeingPaid(String costString) {
        ManaCostParser parsedCostString = new ManaCostParser(costString);
        ManaCost manaCost = new ManaCost(parsedCostString);

        return new ManaCostBeingPaid(manaCost);
    }
}
