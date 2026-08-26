package forge.deck;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.item.PaperCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestDeckRuleTest {
    private static PaperCard card(final String name, final String types) {
        return new PaperCard(CardRules.fromScript(List.of(
                "Name:" + name,
                "ManaCost:R",
                "Types:" + types,
                "Oracle:Test card."
        )), "TST", CardRarity.Common);
    }

    @Test
    void questCardsMustBeKeptInTheSideboard() {
        final Deck deck = new Deck("Quest in main deck");
        deck.getMain().add(card("Test Quest", "Enchantment Quest"), 1);

        assertEquals("must keep Quest cards in its sideboard",
                DeckFormat.getQuestCardConformanceProblem(deck));
    }

    @Test
    void aDeckMayCarryOnlyOneQuest() {
        final Deck deck = new Deck("Two sideboard Quests");
        deck.getOrCreate(DeckSection.Sideboard)
                .add(card("First Quest", "Enchantment Quest"), 1);
        deck.getOrCreate(DeckSection.Sideboard)
                .add(card("Second Quest", "Enchantment Quest"), 1);

        assertEquals("must not contain more than one Quest card in its sideboard",
                DeckFormat.getQuestCardConformanceProblem(deck));
    }

    @Test
    void exactlyOneSideboardQuestIsLegalUnderTheQuestRule() {
        final Deck deck = new Deck("One sideboard Quest");
        deck.getOrCreate(DeckSection.Sideboard)
                .add(card("Test Quest", "Enchantment Quest"), 1);

        assertNull(DeckFormat.getQuestCardConformanceProblem(deck));
    }
}
