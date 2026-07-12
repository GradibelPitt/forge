package forge.game.staticability;

import forge.game.card.Card;
import org.testng.Assert;
import org.testng.annotations.Test;

public class StaticAbilityLayersTest {
    @Test
    public void maximumHandSizeStillUsesRulesLayerWhenAbilityAlsoAddsKeyword() {
        final Card host = new Card(1, null);
        final StaticAbility ability = new StaticAbility(
                "Mode$ Continuous | Affected$ Player | SetMaxHandSize$ 10 | AddKeyword$ FatigueOnEmptyDraw",
                host, null);

        Assert.assertTrue(ability.getLayers().contains(StaticAbilityLayer.ABILITIES));
        Assert.assertTrue(ability.getLayers().contains(StaticAbilityLayer.RULES));
    }

    @Test
    public void raisedMaximumHandSizeUsesRulesLayer() {
        final Card host = new Card(1, null);
        final StaticAbility ability = new StaticAbility(
                "Mode$ Continuous | Affected$ You | RaiseMaxHandSize$ 3",
                host, null);

        Assert.assertTrue(ability.getLayers().contains(StaticAbilityLayer.RULES));
    }
}
