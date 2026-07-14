package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.CardPool;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

public class MakeCardEffectTest {
    private static PaperCard paper(final String name, final String types) {
        return new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:1",
                "Types:" + types,
                "Oracle:Test card."
        )), "TST", CardRarity.MythicRare);
    }

    @Test
    public void startingDeckLegendaryPermanentsComeDirectlyFromTheCardPool() {
        final CardPool startingDeck = new CardPool();
        startingDeck.add(paper("Patches", "Legendary Creature Pirate Demon"), 2);
        startingDeck.add(paper("Hogger", "Legendary Creature Gnoll"), 1);
        startingDeck.add(paper("Lightning Bolt", "Instant"), 1);

        final List<String> names = MakeCardEffect.getLegendaryPermanentStartingDeckNames(
                startingDeck, "Hogger");

        Assert.assertEquals(names, Arrays.asList("Patches", "Patches"));
    }
}
