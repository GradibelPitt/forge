package forge.game.cost;

import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostParser;
import forge.game.mana.ManaCostBeingPaid;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

public class CostAdjustmentColorChoiceTest {

    @Test
    public void reducesOnlyTheChosenAvailableColor() {
        final ManaCostBeingPaid cost = new ManaCostBeingPaid(new ManaCost(new ManaCostParser("6 U U B B")));
        final ColorSet choices = ColorSet.fromNames("U", "B");

        AssertJUnit.assertEquals(ColorSet.UB, CostAdjustment.getColorReductionChoices(cost, choices));
        AssertJUnit.assertTrue(CostAdjustment.reduceChosenColor(cost, choices, MagicColor.BLUE));
        AssertJUnit.assertEquals("{6}{U}{B}{B}", cost.toString());

        AssertJUnit.assertTrue(CostAdjustment.reduceChosenColor(cost, choices, MagicColor.BLACK));
        AssertJUnit.assertTrue(CostAdjustment.reduceChosenColor(cost, choices, MagicColor.BLACK));
        AssertJUnit.assertEquals(ColorSet.U, CostAdjustment.getColorReductionChoices(cost, choices));
        AssertJUnit.assertEquals("{6}{U}", cost.toString());
    }
}
