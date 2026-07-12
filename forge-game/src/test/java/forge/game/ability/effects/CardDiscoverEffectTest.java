package forge.game.ability.effects;

import com.google.common.collect.Iterables;
import forge.game.card.Card;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class CardDiscoverEffectTest {
    private static Card named(final int id, final String name) {
        final Card card = new Card(id, null);
        card.setName(name);
        return card;
    }

    @Test
    public void offersAtMostThreeDistinctNames() {
        final List<Card> candidates = Arrays.asList(
                named(1, "Patches"), named(2, "Patches"),
                named(3, "Sky Raider"), named(4, "Deckhand"), named(5, "Corsair"));

        final List<Card> options = CardDiscoverEffect.selectUniqueOptions(candidates, 3, new Random(1));

        Assert.assertEquals(options.size(), 3);
        Assert.assertEquals(options.stream().map(Card::getName).distinct().count(), 3L);
    }

    @Test
    public void returnsEveryAvailableDistinctNameWhenFewerThanLimitExist() {
        final List<Card> candidates = Arrays.asList(
                named(1, "Patches"), named(2, "Patches"), named(3, "Deckhand"));

        final List<String> names = CardDiscoverEffect.selectUniqueOptions(candidates, 3, new Random(1))
                .stream().map(Card::getName).sorted().collect(Collectors.toList());

        Assert.assertEquals(names, Arrays.asList("Deckhand", "Patches"));
    }

    @Test
    public void returnsNoOptionsForEmptyPoolOrNonPositiveLimit() {
        Assert.assertTrue(CardDiscoverEffect.selectUniqueOptions(Collections.emptyList(), 3, new Random(1)).isEmpty());
        Assert.assertTrue(CardDiscoverEffect.selectUniqueOptions(Collections.singletonList(named(1, "Patches")), 0, new Random(1)).isEmpty());
    }

    @Test
    public void remembersTheMovedCardOnlyWhenRequested() {
        final Card source = named(1, "Cursed Catacombs");
        final Card discovered = named(2, "Discovered Card");

        CardDiscoverEffect.rememberChosen(source, true, discovered);
        Assert.assertTrue(Iterables.contains(source.getRemembered(), discovered));

        source.clearRemembered();
        CardDiscoverEffect.rememberChosen(source, false, discovered);
        Assert.assertFalse(Iterables.contains(source.getRemembered(), discovered));
    }
}
