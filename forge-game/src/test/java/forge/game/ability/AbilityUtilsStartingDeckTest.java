package forge.game.ability;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.CardPool;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;

public class AbilityUtilsStartingDeckTest {
    private static PaperCard paper(final String name, final String edition, final String types) {
        return new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:1",
                "Types:" + types,
                "Oracle:Test card."
        )), edition, CardRarity.MythicRare);
    }

    @Test
    public void duplicateNonlandNamesMergePrintingsAndIgnoreLands() {
        final CardPool startingDeck = new CardPool();
        startingDeck.add(paper("Alpha", "T01", "Creature Elf"), 1);
        startingDeck.add(paper("Alpha", "T02", "Creature Elf"), 1);
        startingDeck.add(paper("Beta", "T01", "Sorcery"), 3);
        startingDeck.add(paper("Gamma", "T01", "Artifact"), 1);
        startingDeck.add(paper("Forest", "T01", "Basic Land Forest"), 20);

        Assert.assertEquals(AbilityUtils.countStartingDeckDuplicateNonlandNames(startingDeck), 2);
    }

    @Test
    public void singletonNonlandsHaveNoDuplicateNames() {
        final CardPool startingDeck = new CardPool();
        startingDeck.add(paper("Alpha", "T01", "Creature Elf"), 1);
        startingDeck.add(paper("Beta", "T01", "Instant"), 1);
        startingDeck.add(paper("Island", "T01", "Basic Land Island"), 12);

        Assert.assertEquals(AbilityUtils.countStartingDeckDuplicateNonlandNames(startingDeck), 0);
    }
}
