package forge.itemmanager;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.item.PaperCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFilterUtilMemoizationTest {
    @Test
    void rulesOnlySearchEvaluatesSharedRulesOnceAcrossPrintings() {
        final CardRules rules = rules("Shared Rules");
        final PaperCard firstPrinting = new PaperCard(rules, "ONE", CardRarity.Common);
        final PaperCard secondPrinting = new PaperCard(rules, "TWO", CardRarity.Rare);
        final AtomicInteger evaluations = new AtomicInteger();
        final Predicate<PaperCard> predicate = SFilterUtil.memoizeTextFilter(card -> {
            evaluations.incrementAndGet();
            return card.getRules().getName().contains("Shared");
        }, false);

        assertTrue(predicate.test(firstPrinting));
        assertTrue(predicate.test(secondPrinting));
        assertEquals(1, evaluations.get());
    }

    @Test
    void nameSearchReusesEquivalentSearchableNamesButSeparatesDifferentRules() {
        final CardRules sharedRules = rules("Shared Name");
        final PaperCard firstPrinting = new PaperCard(sharedRules, "ONE", CardRarity.Common);
        final PaperCard secondPrinting = new PaperCard(sharedRules, "TWO", CardRarity.Rare);
        final PaperCard differentCard = new PaperCard(rules("Different Name"), "ONE", CardRarity.Common);
        final AtomicInteger evaluations = new AtomicInteger();
        final Predicate<PaperCard> predicate = SFilterUtil.memoizeTextFilter(card -> {
            evaluations.incrementAndGet();
            return true;
        }, true);

        assertTrue(predicate.test(firstPrinting));
        assertTrue(predicate.test(secondPrinting));
        assertTrue(predicate.test(differentCard));
        assertEquals(2, evaluations.get());
    }

    private static CardRules rules(final String name) {
        return CardRules.fromScript(List.of(
                "Name:" + name,
                "ManaCost:1",
                "Types:Sorcery",
                "Oracle:Test."
        ));
    }
}
