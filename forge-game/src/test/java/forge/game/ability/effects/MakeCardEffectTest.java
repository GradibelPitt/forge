package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.CardPool;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
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

    private static PaperCard gameRulePaper(final String name) {
        return new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:0",
                "Types:Artifact",
                "K:GameRule",
                "Oracle:Test game rule."
        )), "TST", CardRarity.MythicRare);
    }

    @Test
    public void gameRulesCannotBeMaterializedByMakeCard() {
        Assert.assertFalse(MakeCardEffect.canMaterialize(null),
                "a missing entity name must be rejected before Card.fromPaperCard");
        Assert.assertTrue(MakeCardEffect.canMaterialize(paper("Ordinary", "Artifact")));
        Assert.assertFalse(MakeCardEffect.canMaterialize(gameRulePaper("Game Rule")));
    }

    @Test
    public void randomStartingDeckNonlandsPreferDifferentNamesBeforeDuplicates() {
        final CardPool startingDeck = new CardPool();
        startingDeck.add(paper("Alpha", "Creature Wizard"), 4);
        startingDeck.add(paper("Beta", "Sorcery"), 3);
        startingDeck.add(paper("Gamma", "Artifact"), 2);
        startingDeck.add(paper("Island", "Basic Land Island"), 20);

        final List<String> names = MakeCardEffect.getRandomNonlandStartingDeckNames(
                startingDeck, 4, new java.util.Random(7));

        Assert.assertEquals(names.size(), 4);
        Assert.assertEquals(names.subList(0, 3).stream().distinct().count(), 3L,
                "one copy of every different nonland name must be chosen before a duplicate");
        Assert.assertFalse(names.contains("Island"));
    }

    @Test
    public void randomStartingDeckNonlandsStopWhenTheDeckHasFewerThanTwentyCandidates() {
        final CardPool startingDeck = new CardPool();
        startingDeck.add(paper("Alpha", "Creature Wizard"), 2);
        startingDeck.add(paper("Beta", "Instant"), 1);
        startingDeck.add(paper("Swamp", "Basic Land Swamp"), 20);

        final List<String> names = MakeCardEffect.getRandomNonlandStartingDeckNames(
                startingDeck, 20, new java.util.Random(11));

        Assert.assertEquals(names.size(), 3);
        Assert.assertEquals(names.stream().filter("Alpha"::equals).count(), 2L);
        Assert.assertEquals(names.stream().filter("Beta"::equals).count(), 1L);
    }

    @Test
    public void cancelledSpellbookChoiceStopsWithoutAddingAnEmptyCardName() {
        final List<String> names = new ArrayList<>();

        Assert.assertFalse(MakeCardEffect.recordChosenNameOrCancel(names, null));
        Assert.assertFalse(MakeCardEffect.recordChosenNameOrCancel(names, ""));
        Assert.assertTrue(names.isEmpty());
        Assert.assertTrue(MakeCardEffect.recordChosenNameOrCancel(names, "Garden of Hope"));
        Assert.assertEquals(names, List.of("Garden of Hope"));
        Assert.assertFalse(MakeCardEffect.recordChosenNameOrCancel(names, ""));
        Assert.assertTrue(names.isEmpty(), "cancelling a later SpellbookAmount choice must be atomic");
    }
}
