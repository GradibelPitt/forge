package forge.game.ability.effects;

import com.google.common.collect.Iterables;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardType;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CardDiscoverEffectTest {
    static {
        CardType.Constant.CREATURE_TYPES.add("Pirate");
    }

    private static Card named(final int id, final String name) {
        final Card card = new Card(id, null);
        card.setName(name);
        return card;
    }

    private static PaperCard paper(final String name, final int manaValue, final String types) {
        return new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:" + manaValue,
                "Types:" + types,
                "Oracle:Test card."
        )), "TST", CardRarity.Common);
    }

    private static List<PaperCard> papers(final String prefix, final int count,
            final int manaValue, final String types) {
        return IntStream.range(0, count)
                .mapToObj(i -> paper(prefix + i, manaValue, types))
                .collect(Collectors.toList());
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

    @Test
    public void recordsEveryExiledChoiceWithTheDiscoverSource() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString());
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "CardDiscover exile tracking test"));
        final Player controller = new Player("Discover controller", game, 1);
        game.getPlayers().add(controller);
        controller.setTeam(1);

        final Card source = new Card(game.nextCardId(), game);
        source.setName("Deathstalker Rexxar");
        source.setOwner(controller);
        source.setController(controller, game.getNextTimestamp());
        controller.getZone(ZoneType.Battlefield).add(source);

        final SpellAbility discover = AbilityFactory.getAbility(
                "DB$ CardDiscover | Defined$ You | Destination$ Exile | RememberChosen$ True",
                source);
        discover.setActivatingPlayer(controller);

        Card first = new Card(game.nextCardId(), game);
        first.setName("First Beast");
        first.setOwner(controller);
        first.setController(controller, game.getNextTimestamp());
        first.addType("Creature");
        first.addType("Beast");
        controller.getZone(ZoneType.Exile).add(first);

        Card second = new Card(game.nextCardId(), game);
        second.setName("Second Beast");
        second.setOwner(controller);
        second.setController(controller, game.getNextTimestamp());
        second.addType("Creature");
        second.addType("Beast");
        controller.getZone(ZoneType.Exile).add(second);

        CardDiscoverEffect.recordMovedCard(source, discover, ZoneType.Exile, first, true);
        CardDiscoverEffect.recordMovedCard(source, discover, ZoneType.Exile, second, true);

        Assert.assertSame(first.getExiledWith(), source);
        Assert.assertSame(second.getExiledWith(), source);
        Assert.assertTrue(source.hasExiledCard(first));
        Assert.assertTrue(source.hasExiledCard(second));
        Assert.assertTrue(first.isValid("Creature.ExiledWithSource", controller, source, discover));
        Assert.assertTrue(second.isValid("Creature.ExiledWithSource", controller, source, discover));
        Assert.assertTrue(Iterables.contains(source.getRemembered(), first));
        Assert.assertTrue(Iterables.contains(source.getRemembered(), second));
    }

    @Test
    public void lightweightFilterRecognizesCurrentDatabaseDiscoverClauses() {
        final Card source = named(1, "Chaos Tentacle");
        source.setSVar("X", "Number$3");
        final CardDiscoverCandidateFilter pirates = CardDiscoverCandidateFilter.compile(
                "Creature.Pirate", source, null);
        final CardDiscoverCandidateFilter sorceries = CardDiscoverCandidateFilter.compile(
                "Sorcery.cmcEQX", source, null);

        Assert.assertTrue(pirates.isComplete());
        Assert.assertEquals(pirates.getCapability(),
                CardDiscoverCandidateFilter.Capability.STATIC_EXACT);
        Assert.assertTrue(pirates.matches(paper("Sky Raider", 1, "Creature Pirate")));
        Assert.assertFalse(pirates.matches(paper("Wrong Pirate", 1, "Creature Goblin")));
        Assert.assertTrue(sorceries.isComplete());
        Assert.assertTrue(sorceries.matches(paper("Three Mana Spell", 3, "Sorcery")));
        Assert.assertFalse(sorceries.matches(paper("Four Mana Spell", 4, "Sorcery")));
    }

    @Test
    public void unknownDynamicClauseIsConservativelyDeferred() {
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Creature.YouCtrl", named(1, "Source"), null);

        Assert.assertFalse(filter.isComplete());
        Assert.assertEquals(filter.getCapability(),
                CardDiscoverCandidateFilter.Capability.STATIC_PREFILTER_DYNAMIC_FINAL);
        Assert.assertTrue(filter.matches(paper("Creature", 1, "Creature Human")));
        Assert.assertFalse(filter.matches(paper("Sorcery", 1, "Sorcery")));
    }

    @Test
    public void dynamicOnlyFilterDoesNotPretendToPrefilter() {
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Card.YouCtrl", named(1, "Source"), null);

        Assert.assertEquals(filter.getCapability(),
                CardDiscoverCandidateFilter.Capability.DYNAMIC_ONLY);
        Assert.assertTrue(filter.matches(paper("Any Card", 1, "Sorcery")));
    }

    @Test
    public void completeStaticFilterMaterializesExactlyThreeCandidates() {
        final List<PaperCard> pool = papers("Spell ", 100, 3, "Sorcery");
        final Card source = named(1, "Chaos Tentacle");
        source.setSVar("X", "Number$3");
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Sorcery.cmcEQX", source, null);
        final AtomicInteger createdCandidateCardCount = new AtomicInteger();
        final AtomicInteger exactValidationCount = new AtomicInteger();

        final List<Card> options = CardDiscoverEffect.selectDatabaseOptions(pool, filter, 3, 12,
                new Random(7), paper -> {
                    createdCandidateCardCount.incrementAndGet();
                    return named(paper.getName().hashCode(), paper.getName());
                }, card -> {
                    exactValidationCount.incrementAndGet();
                    return true;
                });

        Assert.assertEquals(options.size(), 3);
        Assert.assertEquals(createdCandidateCardCount.get(), 3);
        Assert.assertEquals(exactValidationCount.get(), 3);
    }

    @Test
    public void rareStaticMatchesAreFilteredBeforeSampling() {
        final List<PaperCard> pool = papers("Common Spell ", 500, 9, "Sorcery");
        pool.addAll(papers("Rare Ten Mana Spell ", 5, 10, "Sorcery"));
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Sorcery.cmcEQ10", named(1, "Source"), null);
        final AtomicInteger createdCandidateCardCount = new AtomicInteger();

        final List<Card> options = CardDiscoverEffect.selectDatabaseOptions(pool, filter, 3, 128,
                new Random(10), paper -> {
                    createdCandidateCardCount.incrementAndGet();
                    return named(paper.getName().hashCode(), paper.getName());
                }, card -> true);

        Assert.assertEquals(options.size(), 3);
        Assert.assertTrue(options.stream().allMatch(
                card -> card.getName().startsWith("Rare Ten Mana Spell ")));
        Assert.assertEquals(createdCandidateCardCount.get(), 3);
    }

    @Test
    public void staticFilterReturnsEveryLegalCardWhenOnlyTwoExist() {
        final List<PaperCard> pool = papers("Common Spell ", 100, 9, "Sorcery");
        pool.addAll(papers("Only Ten Mana Spell ", 2, 10, "Sorcery"));
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Sorcery.cmcEQ10", named(1, "Source"), null);
        final AtomicInteger createdCandidateCardCount = new AtomicInteger();

        final List<Card> options = CardDiscoverEffect.selectDatabaseOptions(pool, filter, 3, 128,
                new Random(12), paper -> {
                    createdCandidateCardCount.incrementAndGet();
                    return named(paper.getName().hashCode(), paper.getName());
                }, card -> true);

        Assert.assertEquals(options.size(), 2);
        Assert.assertTrue(options.stream().allMatch(
                card -> card.getName().startsWith("Only Ten Mana Spell ")));
        Assert.assertEquals(createdCandidateCardCount.get(), 2);
    }

    @Test
    public void staticPoolBelowDynamicThresholdIsValidatedExhaustively() {
        final List<PaperCard> pool = papers("Creature ", 20, 1, "Creature Human");
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Creature.YouCtrl", named(1, "Source"), null);
        final AtomicInteger createdCandidateCardCount = new AtomicInteger();
        final AtomicInteger exactValidationCount = new AtomicInteger();

        final List<Card> options = CardDiscoverEffect.selectDatabaseOptions(pool, filter, 3, 128,
                new Random(11), paper -> {
                    createdCandidateCardCount.incrementAndGet();
                    return named(paper.getName().hashCode(), paper.getName());
                }, card -> {
                    exactValidationCount.incrementAndGet();
                    return card.getName().equals("Creature 19");
                });

        Assert.assertEquals(options.size(), 1);
        Assert.assertEquals(options.get(0).getName(), "Creature 19");
        Assert.assertEquals(createdCandidateCardCount.get(), 20);
        Assert.assertEquals(exactValidationCount.get(), 20);
    }

    @Test
    public void incompleteDynamicFilterNeverExceedsMaterializationBudget() {
        final List<PaperCard> pool = papers("Creature ", 100, 1, "Creature Human");
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Creature.YouCtrl", named(1, "Source"), null);
        final AtomicInteger createdCandidateCardCount = new AtomicInteger();
        final AtomicInteger exactValidationCount = new AtomicInteger();

        final List<Card> options = CardDiscoverEffect.selectDatabaseOptions(pool, filter, 3, 8,
                new Random(9), paper -> {
                    createdCandidateCardCount.incrementAndGet();
                    return named(paper.getName().hashCode(), paper.getName());
                }, card -> {
                    exactValidationCount.incrementAndGet();
                    return false;
                });

        Assert.assertTrue(options.isEmpty());
        Assert.assertEquals(createdCandidateCardCount.get(), 8);
        Assert.assertEquals(exactValidationCount.get(), 8);
    }

    @Test
    public void databaseSelectionIsReproducibleAndDeduplicatesNames() {
        final List<PaperCard> pool = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pool.add(paper("Card " + i, 1, "Sorcery"));
            pool.add(paper("Card " + i, 1, "Sorcery"));
        }
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Sorcery", named(1, "Source"), null);

        final List<String> first = CardDiscoverEffect.selectDatabaseOptions(pool, filter, 3, 8,
                new Random(1234), paper -> named(paper.getName().hashCode(), paper.getName()), card -> true)
                .stream().map(Card::getName).collect(Collectors.toList());
        final List<String> second = CardDiscoverEffect.selectDatabaseOptions(pool, filter, 3, 8,
                new Random(1234), paper -> named(paper.getName().hashCode(), paper.getName()), card -> true)
                .stream().map(Card::getName).collect(Collectors.toList());

        Assert.assertEquals(first, second);
        Assert.assertEquals(first.size(), 3);
        Assert.assertEquals(first.stream().distinct().count(), 3L);
    }

    @Test
    public void rejectedTemporaryCandidatesAreNotReturnedOrRemembered() {
        final Card source = named(1, "Source");
        final List<Card> created = new ArrayList<>();
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Creature.YouCtrl", source, null);

        final List<Card> options = CardDiscoverEffect.selectDatabaseOptions(
                papers("Creature ", 20, 1, "Creature Human"), filter, 3, 5,
                new Random(2), paper -> {
                    final Card card = named(paper.getName().hashCode(), paper.getName());
                    created.add(card);
                    return card;
                }, card -> false);

        Assert.assertTrue(options.isEmpty());
        Assert.assertEquals(created.size(), 5);
        for (final Card card : created) {
            Assert.assertFalse(Iterables.contains(source.getRemembered(), card));
            Assert.assertNull(card.getZone());
        }
    }

    @Test
    public void thousandStaticSelectionsCreateAtMostThreeCandidatesEach() {
        final List<PaperCard> pool = papers("Spell ", 50, 3, "Sorcery");
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Sorcery", named(1, "Source"), null);
        final AtomicInteger createdCandidateCardCount = new AtomicInteger();
        final List<String> firstRun = new ArrayList<>();
        final List<String> secondRun = new ArrayList<>();

        runSeededSelections(pool, filter, createdCandidateCardCount, firstRun);
        Assert.assertEquals(createdCandidateCardCount.get(), 3_000);
        createdCandidateCardCount.set(0);
        runSeededSelections(pool, filter, createdCandidateCardCount, secondRun);

        Assert.assertEquals(createdCandidateCardCount.get(), 3_000);
        Assert.assertEquals(firstRun, secondRun);
    }

    private static void runSeededSelections(final List<PaperCard> pool,
            final CardDiscoverCandidateFilter filter, final AtomicInteger count,
            final List<String> selectedNames) {
        final Random random = new Random(20260714L);
        for (int i = 0; i < 1_000; i++) {
            final List<Card> options = CardDiscoverEffect.selectDatabaseOptions(pool, filter, 3, 8,
                    random, paper -> {
                        count.incrementAndGet();
                        return named(paper.getName().hashCode(), paper.getName());
                    }, card -> true);
            Assert.assertEquals(options.size(), 3);
            Assert.assertEquals(options.stream().map(Card::getName).distinct().count(), 3L);
            selectedNames.addAll(options.stream().map(Card::getName).collect(Collectors.toList()));
        }
    }
}
