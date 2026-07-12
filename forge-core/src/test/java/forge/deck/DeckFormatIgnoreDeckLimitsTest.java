package forge.deck;

import forge.CardStorageReader;
import forge.StaticData;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.item.PaperCard;
import forge.util.Localizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckFormatIgnoreDeckLimitsTest {
    @BeforeAll
    static void initializeStaticData() throws Exception {
        final Path testData = Files.createTempDirectory("deck-format-test");
        final Path cards = Files.createDirectories(testData.resolve("cards"));
        final Path blocks = Files.createDirectories(testData.resolve("blocks"));
        final Path editions = Path.of("..", "forge-gui", "res", "editions").toAbsolutePath();
        final Path languages = Path.of("..", "forge-gui", "res", "languages").toAbsolutePath();
        Localizer.getInstance().initialize("en-US", languages.toString());

        writeCardScript(cards, "Minimum Thirty One", "DeckMinimum:31");
        writeCardScript(cards, "Ignore Limits", "IgnoreDeckLimits");
        for (int i = 1; i <= 30; i++) {
            writeCardScript(cards, "Filler " + i);
        }

        new StaticData(new CardStorageReader(cards.toString(), null, false), null, editions.toString(),
                editions.toString(), blocks.toString(), "", true, true);
    }

    private static void writeCardScript(final Path cards, final String name, final String... keywords) throws Exception {
        final StringBuilder script = new StringBuilder("Name:").append(name)
                .append("\nManaCost:0\nTypes:Artifact\nOracle:Test card.\n");
        for (final String keyword : keywords) {
            script.append("K:").append(keyword).append('\n');
        }
        Files.writeString(cards.resolve(name.replace(' ', '_') + ".txt"), script.toString());
    }

    private static PaperCard card(final String name, final String... keywords) {
        final List<String> script = new ArrayList<>(List.of(
                "Name:" + name,
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:Test card."
        ));
        for (final String keyword : keywords) {
            script.add("K:" + keyword);
        }
        return new PaperCard(CardRules.fromScript(script), "TST", CardRarity.Common);
    }

    private static PaperCard commander(final String name) {
        return new PaperCard(CardRules.fromScript(List.of(
                "Name:" + name,
                "ManaCost:0",
                "Types:Legendary Creature",
                "Oracle:Test commander."
        )), "TST", CardRarity.Common);
    }

    @Test
    void recognizesOverrideOnlyFromTheProvidedMainDeckPool() {
        final CardPool main = new CardPool();
        main.add(card("Override", "IgnoreDeckLimits"), 1);

        assertTrue(DeckFormat.hasDeckLimitOverride(main));
        assertFalse(DeckFormat.hasDeckLimitOverride(new CardPool()));
    }

    @Test
    void overridePolicySetsMinimumToOneAndSkipsCopyLimits() {
        assertEquals(1, DeckFormat.effectiveMainDeckMinimum(60, true, new CardPool()));
        assertEquals(60, DeckFormat.effectiveMainDeckMinimum(60, false, new CardPool()));
        assertFalse(DeckFormat.shouldEnforceCardCopyLimits(true));
        assertTrue(DeckFormat.shouldEnforceCardCopyLimits(false));
    }

    @Test
    void soleDeckMinimumKeywordReplacesTheNormalMinimum() {
        final CardPool main = new CardPool();
        main.add(card("Minimum Thirty One", "DeckMinimum:31"), 1);

        assertEquals(31, DeckFormat.effectiveMainDeckMinimum(60, false, main));
    }

    @Test
    void highestDeckMinimumKeywordWins() {
        final CardPool main = new CardPool();
        main.add(card("Minimum Thirty One", "DeckMinimum:31"), 1);
        main.add(card("Minimum Forty", "DeckMinimum:40"), 1);

        assertEquals(40, DeckFormat.effectiveMainDeckMinimum(60, false, main));
    }

    @Test
    void malformedOrNonpositiveDeckMinimumKeywordsKeepTheNormalMinimum() {
        final CardPool main = new CardPool();
        main.add(card("Malformed Minimum", "DeckMinimum:not-a-number"), 1);
        main.add(card("Zero Minimum", "DeckMinimum:0"), 1);
        main.add(card("Negative Minimum", "DeckMinimum:-1"), 1);

        assertEquals(60, DeckFormat.effectiveMainDeckMinimum(60, false, main));
    }

    @Test
    void ignoreDeckLimitsMinimumCannotBeReducedByAdvantageousProclamation() {
        assertEquals(1, DeckFormat.adjustMinimumForConspiracies(1, true, 1));
    }

    @Test
    void deckMinimumStillYieldsToIgnoreDeckLimits() {
        final CardPool main = new CardPool();
        main.add(card("Minimum Forty", "DeckMinimum:40"), 1);

        assertEquals(1, DeckFormat.effectiveMainDeckMinimum(60, true, main));
    }

    @Test
    void deckMinimumDoesNotChangeCommanderDeckRequirements() {
        final Deck deck = new Deck("Commander minimum remains 99");
        deck.getOrCreate(DeckSection.Commander).add(commander("Test Commander"), 1);
        deck.getMain().add(card("Minimum Thirty One", "DeckMinimum:31"), 30);

        assertEquals("should have at least 99 cards", DeckFormat.Commander.getDeckConformanceProblem(deck));
    }

    @Test
    void constructedDeckMinimumKeywordChangesPublicConformance() {
        final Deck shortDeck = new Deck("Thirty cards");
        shortDeck.getMain().add(card("Minimum Thirty One", "DeckMinimum:31"), 1);
        for (int i = 1; i <= 29; i++) {
            shortDeck.getMain().add(card("Filler " + i), 1);
        }
        assertEquals("should have at least 31 cards", DeckFormat.Constructed.getDeckConformanceProblem(shortDeck));

        final Deck legalDeck = new Deck("Thirty one cards");
        legalDeck.getMain().add(card("Minimum Thirty One", "DeckMinimum:31"), 1);
        for (int i = 1; i <= 30; i++) {
            legalDeck.getMain().add(card("Filler " + i), 1);
        }
        assertEquals(null, DeckFormat.Constructed.getDeckConformanceProblem(legalDeck));
    }

    @Test
    void oneCardIgnoreDeckLimitsDeckPassesPublicConformance() {
        final Deck deck = new Deck("One card deck");
        deck.getMain().add(card("Ignore Limits", "IgnoreDeckLimits"), 1);

        assertEquals(null, DeckFormat.Constructed.getDeckConformanceProblem(deck));
    }
}
