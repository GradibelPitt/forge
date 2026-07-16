package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityManaConvert;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.trackable.TrackableProperty;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class ReplaceCardsEffectTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    private static PaperCard paper(final String name, final String manaCost) {
        return new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:" + manaCost,
                "Types:Sorcery",
                "A:SP$ Draw | NumCards$ 1 | SpellDescription$ Test.",
                "Oracle:Test card."
        )), "TST", CardRarity.Common);
    }

    @Test
    public void cachedReplacementPoolScansDatabaseOnlyOnceAndBucketsByManaValue() {
        final Collection<PaperCard> database = Arrays.asList(
                paper("White One", "W"),
                paper("Blue Three", "2 U"),
                paper("Black One", "B"),
                paper("Colorless Three", "3")
        );
        final Card source = new Card(1, null);
        source.setName("Source");
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Card.nonBlack+nonColorless", source, null);
        final AtomicInteger visits = new AtomicInteger();
        final Iterable<PaperCard> countedDatabase = () -> database.stream()
                .peek(ignored -> visits.incrementAndGet()).iterator();
        final ReplaceCardsEffect.ManaValuePoolCache cache =
                new ReplaceCardsEffect.ManaValuePoolCache();
        final Object databaseIdentity = new Object();

        final Map<Integer, List<PaperCard>> first = cache.get(
                databaseIdentity, database.size(), "Card.nonBlack+nonColorless",
                countedDatabase, filter);
        final Map<Integer, List<PaperCard>> second = cache.get(
                databaseIdentity, database.size(), "Card.nonBlack+nonColorless",
                countedDatabase, filter);

        Assert.assertSame(first, second);
        Assert.assertEquals(visits.get(), database.size());
        Assert.assertEquals(first.get(1).stream().map(PaperCard::getName).toList(),
                List.of("White One"));
        Assert.assertEquals(first.get(3).stream().map(PaperCard::getName).toList(),
                List.of("Blue Three"));
    }

    @Test
    public void replacementNamesPreserveExistingEntriesAndDeduplicateAdditions() {
        final Card host = new Card(1, null);
        host.setName("Source");
        host.setNamedCards(new ArrayList<>(Arrays.asList(
                "Alpha", "Alpha", "", "Beta")));

        ReplaceCardsEffect.mergeReplacementNames(host, Arrays.asList(
                "Alpha", "Gamma", "Gamma", "", null));

        Assert.assertEquals(host.getNamedCards(),
                Arrays.asList("Alpha", "Alpha", "", "Beta", "Gamma"));
        Assert.assertTrue(host.hasNamedCardName("Alpha"));
        Assert.assertTrue(host.hasNamedCardName("Gamma"));
        Assert.assertFalse(host.hasNamedCardName("Delta"));
    }

    @Test
    public void replacementNameMergeScansEachLargeInputOnlyOnce() {
        final List<String> currentNames = new ArrayList<>();
        final List<String> successfulNames = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            currentNames.add("Existing " + i);
            successfulNames.add("Existing " + i);
            successfulNames.add("New " + i);
            successfulNames.add("New " + i);
        }
        final AtomicInteger currentVisits = new AtomicInteger();
        final AtomicInteger successfulVisits = new AtomicInteger();
        final Iterable<String> countedCurrent = () -> currentNames.stream()
                .peek(ignored -> currentVisits.incrementAndGet()).iterator();
        final Iterable<String> countedSuccessful = () -> successfulNames.stream()
                .peek(ignored -> successfulVisits.incrementAndGet()).iterator();

        final ReplaceCardsEffect.ReplacementNameMerge merge =
                ReplaceCardsEffect.collectReplacementNameMerge(
                        countedCurrent, countedSuccessful);

        Assert.assertEquals(currentVisits.get(), currentNames.size());
        Assert.assertEquals(successfulVisits.get(), successfulNames.size());
        Assert.assertTrue(merge.hasAdditions());
        Assert.assertEquals(merge.names().size(), 20_000);
        Assert.assertEquals(merge.names().get(0), "Existing 0");
        Assert.assertEquals(merge.names().get(9_999), "Existing 9999");
        Assert.assertEquals(merge.names().get(10_000), "New 0");
        Assert.assertEquals(merge.names().get(19_999), "New 9999");
    }

    @Test
    public void replacementNameMergeUsesOneBoundedViewUpdateAndNoOpIsSilent() {
        final Card host = new Card(1, null);
        host.setName("Source");
        host.setNamedCards(new ArrayList<>(Arrays.asList(
                "Existing", "Existing", "")));
        final List<String> originalNames = host.getNamedCards();
        final int consumerId = 47;
        host.getView().registerConsumer(consumerId);
        host.getView().getAndClearDirtyProps(consumerId);

        final int beforeNoOp = host.getView().getVersion();
        ReplaceCardsEffect.mergeReplacementNames(host, Arrays.asList(
                null, "", "Existing", "Existing"));

        Assert.assertSame(host.getNamedCards(), originalNames,
                "a no-op merge must not call the setter");
        Assert.assertEquals(host.getView().getVersion(), beforeNoOp);
        Assert.assertTrue(host.getView().getAndClearDirtyProps(consumerId).isEmpty());

        final List<String> successfulNames = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            successfulNames.add("New " + i);
        }
        final int beforeBatch = host.getView().getVersion();
        ReplaceCardsEffect.mergeReplacementNames(host, successfulNames);

        final int versionDelta = host.getView().getVersion() - beforeBatch;
        Assert.assertTrue(versionDelta > 0 && versionDelta <= 2,
                "one batch update must have constant view-notification cost");
        Assert.assertEquals(host.getView().getAndClearDirtyProps(consumerId),
                EnumSet.of(TrackableProperty.NamedCard));
        Assert.assertEquals(host.getNamedCards().size(), 259);
        Assert.assertEquals(host.getNamedCards().get(0), "Existing");
        Assert.assertEquals(host.getNamedCards().get(1), "Existing");
        Assert.assertEquals(host.getNamedCards().get(2), "");
        Assert.assertEquals(host.getNamedCards().get(258), "New 255");
        Assert.assertNotSame(host.getNamedCards(), originalNames);

        successfulNames.add("Mutated Later");
        Assert.assertFalse(host.hasNamedCardName("Mutated Later"),
                "the host must not retain a mutable input alias");

        final List<String> batchNames = host.getNamedCards();
        final int beforeRepeat = host.getView().getVersion();
        ReplaceCardsEffect.mergeReplacementNames(host, successfulNames.subList(
                0, successfulNames.size() - 1));
        Assert.assertSame(host.getNamedCards(), batchNames,
                "a repeated no-op merge must not call the setter");
        Assert.assertEquals(host.getView().getVersion(), beforeRepeat);
        Assert.assertTrue(host.getView().getAndClearDirtyProps(consumerId).isEmpty());
        host.getView().unregisterConsumer(consumerId);
    }

    @Test
    public void indexedPlanVisitsALargeZoneOnceAndDeduplicatesCardIdentity() {
        final List<Probe> cards = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            cards.add(new Probe(i));
        }
        cards.add(cards.get(0));
        final AtomicInteger visits = new AtomicInteger();
        final Iterable<Probe> countedCards = () -> cards.stream()
                .peek(ignored -> visits.incrementAndGet()).iterator();

        final List<ReplaceCardsEffect.IndexedValue<Probe>> plan =
                ReplaceCardsEffect.collectIndexedMatches(
                        countedCards, probe -> probe.index() % 2 == 0);

        Assert.assertEquals(visits.get(), cards.size());
        Assert.assertEquals(plan.size(), 10_000);
        Assert.assertEquals(plan.get(0).position(), 0);
        Assert.assertEquals(plan.get(plan.size() - 1).position(), 19_998);
        Assert.assertSame(plan.get(0).value(), cards.get(0));
    }

    @Test
    public void eligiblePlayersAreDeduplicatedBeforePlanning() {
        final Game game = game("ReplaceCards duplicate players");
        final Player player = player(game);

        final List<Player> players = ReplaceCardsEffect.collectEligiblePlayers(
                Arrays.asList(player, null, player));

        Assert.assertEquals(players, List.of(player));
    }

    @Test
    public void sameIdReplacementObjectNeverSatisfiesAnUnorderedPosition() {
        final Game game = game("ReplaceCards unordered identity");
        final Player player = player(game);
        final Card host = card(paper("Identity Source", "B"), player);
        final SpellAbility replace = replaceAbility(host, player, "Library");
        final Card original = addToLibrary(
                paper("Planned Black One", "B"), player);
        final Zone library = player.getZone(ZoneType.Library);
        final ReplaceCardsEffect.ZoneReplacementPlan plan = plan(
                player, ZoneType.Library, Card::isBlack);
        game.getAction().ceaseToExist(original, true);
        final Card sameId = CardFactory.getCard(
                paper("Same Id Impostor", "B"), player,
                original.getId(), game);
        sameId.setController(player, game.getNextTimestamp());
        library.add(sameId);
        final CountingRandom random = new CountingRandom();
        final Set<String> names = new LinkedHashSet<>();

        ReplaceCardsEffect.executePlan(game, replace, plan,
                Map.of(1, List.of(paper("Blue One", "U"))), names, random);

        Assert.assertEquals(random.calls, 0);
        Assert.assertEquals(library.size(), 1);
        Assert.assertSame(library.get(0), sameId);
        Assert.assertTrue(names.isEmpty());
        Assert.assertTrue(player.getZone(ZoneType.None).isEmpty());
    }

    @Test
    public void positionDriftSkipsThePlannedObjectWithoutSearching() {
        final Game game = game("ReplaceCards position drift");
        final Player player = player(game);
        final Card host = card(paper("Position Source", "B"), player);
        final SpellAbility replace = replaceAbility(host, player, "Library");
        final Card stable = addToLibrary(paper("Stable White", "W"), player);
        final Card original = addToLibrary(
                paper("Drifted Black One", "B"), player);
        final Zone library = player.getZone(ZoneType.Library);
        final ReplaceCardsEffect.ZoneReplacementPlan plan = plan(
                player, ZoneType.Library, Card::isBlack);
        library.reorder(original, 0);
        final CountingRandom random = new CountingRandom();
        final Set<String> names = new LinkedHashSet<>();

        ReplaceCardsEffect.executePlan(game, replace, plan,
                Map.of(1, List.of(paper("Blue One", "U"))), names, random);

        Assert.assertEquals(random.calls, 0);
        Assert.assertSame(library.get(0), original);
        Assert.assertSame(library.get(1), stable);
        Assert.assertTrue(names.isEmpty());
    }

    @Test
    public void matchManaValueMustBeExplicitlyTrueBeforeAnySideEffect() {
        final Game game = game("ReplaceCards MatchManaValue validation");
        final Player player = player(game);
        final Card host = card(paper("Validation Source", "B"), player);
        host.addNamedCard("Existing Name");
        final Card libraryCard = card(paper("Unchanged Black Card", "B"), player);
        player.getZone(ZoneType.Library).add(libraryCard);
        final List<Card> handBefore = player.getZone(ZoneType.Hand)
                .getCards().stream().toList();
        final List<Card> libraryBefore = player.getZone(ZoneType.Library)
                .getCards().stream().toList();

        for (final String matchParam : List.of("", " | MatchManaValue$ False")) {
            final SpellAbility replace = AbilityFactory.getAbility(
                    "DB$ ReplaceCards | Defined$ You | Zones$ Hand,Library "
                            + "| ValidCards$ Card.Black "
                            + "| ReplacementValid$ Card.nonBlack+nonColorless "
                            + "| RememberNames$ True"
                            + matchParam,
                    host);
            final AbilitySub grant = (AbilitySub) AbilityFactory.getAbility(
                    "DB$ GrantSpellRule | Defined$ You "
                            + "| RuleKey$ invalid-match-must-not-register "
                            + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                            + "| ReduceGeneric$ 2 "
                            + "| ManaConversion$ AnyType->AnyColor "
                            + "| Duration$ Permanent",
                    host);
            replace.setSubAbility(grant);
            replace.setActivatingPlayer(player);

            final IllegalArgumentException error = Assert.expectThrows(
                    IllegalArgumentException.class,
                    () -> AbilityUtils.resolve(replace));

            Assert.assertTrue(error.getMessage().contains(
                    "MatchManaValue$ True"));
            Assert.assertEquals(player.getZone(ZoneType.Hand)
                    .getCards().stream().toList(), handBefore);
            Assert.assertEquals(player.getZone(ZoneType.Library)
                    .getCards().stream().toList(), libraryBefore);
            Assert.assertEquals(host.getNamedCards(), List.of("Existing Name"));
            Assert.assertTrue(player.getSpellRuleRegistry().isEmpty());
        }
    }

    @Test
    public void nullAndUnsupportedZoneScriptsFailWithoutMutation() {
        final ReplaceCardsEffect effect = new ReplaceCardsEffect();
        Assert.expectThrows(IllegalArgumentException.class,
                () -> effect.resolve(null));

        final Game game = game("ReplaceCards invalid zone validation");
        final Player player = player(game);
        final Card host = card(paper("Invalid Zone Source", "B"), player);
        final Card battlefieldCard = card(
                paper("Unchanged Battlefield Card", "1 B"), player);
        player.getZone(ZoneType.Battlefield).add(battlefieldCard);
        final SpellAbility replace = AbilityFactory.getAbility(
                "DB$ ReplaceCards | Defined$ You | Zones$ Battlefield "
                        + "| ValidCards$ Card.Black "
                        + "| ReplacementValid$ Card.nonBlack+nonColorless "
                        + "| MatchManaValue$ True",
                host);
        replace.setActivatingPlayer(player);

        Assert.expectThrows(IllegalArgumentException.class,
                () -> effect.resolve(replace));
        Assert.assertSame(player.getZone(ZoneType.Battlefield).get(0),
                battlefieldCard);
    }

    @Test
    public void replacementPreventionKeepsLinearPositionsAndCommitsOnlySuccesses() {
        final Game game = game("ReplaceCards replacement prevention");
        final Player player = player(game);
        final Card host = card(paper("Replacement Source", "B"), player);
        host.addNamedCard("Existing Name");
        final SpellAbility replace = AbilityFactory.getAbility(
                "DB$ ReplaceCards | Defined$ You | Zones$ Library "
                        + "| ValidCards$ Card.Black "
                        + "| ReplacementValid$ Card.nonBlack+nonColorless "
                        + "| MatchManaValue$ True",
                host);
        replace.setActivatingPlayer(player);

        final PaperCard preventionPaper = new PaperCard(
                CardRules.fromScript(Arrays.asList(
                        "Name:White Library Prevention",
                        "ManaCost:1",
                        "Types:Artifact",
                        "R:Event$ Moved | ActiveZones$ Battlefield "
                                + "| Origin$ None | Destination$ Library "
                                + "| ValidCard$ Card.White | Prevent$ True "
                                + "| Layer$ CantHappen "
                                + "| Description$ White cards cannot enter libraries.",
                        "Oracle:White cards cannot enter libraries."
                )), "TST", CardRarity.Common);
        final Card prevention = card(preventionPaper, player);
        player.getZone(ZoneType.Battlefield).add(prevention);

        final Card blockedOriginal = addToLibrary(
                paper("Blocked Black One", "B"), player);
        final Card missingBucketOriginal = addToLibrary(
                paper("Missing Black Five", "4 B"), player);
        final Card stableMiddle = addToLibrary(
                paper("Stable White Two", "1 W"), player);
        final Card successfulOriginal = addToLibrary(
                paper("Successful Black Three", "2 B"), player);
        final Card stableTail = addToLibrary(
                paper("Stable Green Four", "3 G"), player);

        final PaperCard blockedCandidate = paper("Blocked White One", "W");
        final PaperCard successfulCandidate = paper("Successful Blue Three", "2 U");
        final Map<Integer, List<PaperCard>> candidates = new LinkedHashMap<>();
        candidates.put(1, List.of(blockedCandidate));
        candidates.put(3, List.of(successfulCandidate));
        final Zone library = player.getZone(ZoneType.Library);
        final ReplaceCardsEffect.ZoneReplacementPlan plan =
                new ReplaceCardsEffect.ZoneReplacementPlan(
                        player, ZoneType.Library,
                        ReplaceCardsEffect.collectIndexedMatches(
                                library.getCards(), Card::isBlack));
        final CountingRandom random = new CountingRandom();

        final Set<String> successfulNames = new LinkedHashSet<>();
        ReplaceCardsEffect.executePlan(game, replace, plan,
                candidates, successfulNames, random);

        Assert.assertEquals(random.calls, 2,
                "only cards with a candidate bucket consume randomness");
        Assert.assertEquals(library.size(), 4);
        Assert.assertSame(library.get(0), missingBucketOriginal,
                "a missing bucket must preserve the original card");
        Assert.assertSame(library.get(1), stableMiddle);
        Assert.assertEquals(library.get(2).getName(), "Successful Blue Three");
        Assert.assertSame(library.get(3), stableTail);
        Assert.assertTrue(blockedOriginal.isInZone(ZoneType.None));
        Assert.assertTrue(successfulOriginal.isInZone(ZoneType.None));
        Assert.assertTrue(missingBucketOriginal.isInZone(ZoneType.Library));
        Assert.assertTrue(player.getZone(ZoneType.None).isEmpty(),
                "a prevented generated card must not remain staged");
        Assert.assertEquals(successfulNames,
                Set.of("Successful Blue Three"));
        ReplaceCardsEffect.mergeReplacementNames(host, successfulNames);
        Assert.assertEquals(host.getNamedCards(),
                List.of("Existing Name", "Successful Blue Three"));
        Assert.assertFalse(host.hasNamedCardName("Blocked White One"));
    }

    @Test
    public void namedCardsValidityMatchesFutureCardsByName() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(List.of(), rules,
                new Match(rules, List.of(), "ReplaceCards named lookup test"));
        final Player player = new Player("Controller", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);

        final Card emblem = new Card(game.nextCardId(), game);
        emblem.setName("Emblem");
        emblem.setOwner(player);
        emblem.setController(player, game.getNextTimestamp());
        emblem.addNamedCard("White One");

        final Card futureCopy = new Card(game.nextCardId(), game);
        futureCopy.setName("White One");
        futureCopy.setOwner(player);
        futureCopy.setController(player, game.getNextTimestamp());
        futureCopy.setZone(player.getZone(forge.game.zone.ZoneType.Hand));

        Assert.assertTrue(futureCopy.isValid("Card.sharesNameWith NamedCards",
                player, emblem, null));
    }

    @Test
    public void legacyStaticColoredSpellFiltersRemainCompatible() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(List.of(), rules,
                new Match(rules, List.of(), "ReplaceCards colored emblem test"));
        final Player player = new Player("Controller", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);

        final Card emblem = new Card(game.nextCardId(), game);
        emblem.setName("Emblem — Renounce Darkness");
        emblem.setOwner(player);
        emblem.setController(player, game.getNextTimestamp());
        emblem.setEmblem(true);
        emblem.setZone(player.getZone(ZoneType.Command));

        final StaticAbility harmony = emblem.addStaticAbility(
                "Mode$ ManaConvert | ValidPlayer$ You | ValidCard$ Card.nonColorless "
                        + "| ValidSA$ Spell | ManaConversion$ AnyType->AnyColor");
        harmony.setActiveZone(EnumSet.of(ZoneType.Command));
        final StaticAbility reduction = emblem.addStaticAbility(
                "Mode$ ReduceCost | Type$ Spell | ValidCard$ Card.nonColorless "
                        + "| Activator$ You | Amount$ 2");
        reduction.setActiveZone(EnumSet.of(ZoneType.Command));

        final Card colored = Card.fromPaperCard(paper("Colored Seven", "5 W W"), player);
        final SpellAbility coloredSpell = colored.getSpellAbilities().getFirst();
        coloredSpell.setActivatingPlayer(player);
        final Card colorless = Card.fromPaperCard(paper("Colorless Seven", "7"), player);
        final SpellAbility colorlessSpell = colorless.getSpellAbilities().getFirst();
        colorlessSpell.setActivatingPlayer(player);

        Assert.assertTrue(harmony.matchesValidParam("ValidCard", colored));
        Assert.assertTrue(harmony.matchesValidParam("ValidPlayer", player));
        Assert.assertTrue(harmony.matchesValidParam("ValidSA", coloredSpell));
        Assert.assertTrue(StaticAbilityManaConvert.checkManaConvert(
                harmony, player, colored, coloredSpell));
        Assert.assertTrue(reduction.matchesValidParam("ValidCard", colored));
        Assert.assertTrue(reduction.matchesValidParam("Activator", player));
        Assert.assertFalse(harmony.matchesValidParam("ValidCard", colorless));
        Assert.assertFalse(reduction.matchesValidParam("ValidCard", colorless));
        Assert.assertFalse(StaticAbilityManaConvert.checkManaConvert(
                harmony, player, colorless, colorlessSpell));
    }

    private static Game game(final String description) {
        final GameRules rules = new GameRules(GameType.Constructed);
        return new Game(List.of(), rules,
                new Match(rules, List.of(), description));
    }

    private static Player player(final Game game) {
        final Player player = new Player("Controller", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);
        return player;
    }

    private static Card card(final PaperCard paperCard, final Player player) {
        final Card card = Card.fromPaperCard(paperCard, player);
        card.setController(player, player.getGame().getNextTimestamp());
        return card;
    }

    private static Card addToLibrary(final PaperCard paperCard,
                                     final Player player) {
        final Card card = card(paperCard, player);
        player.getZone(ZoneType.Library).add(card);
        return card;
    }

    private static SpellAbility replaceAbility(final Card host,
            final Player player, final String zones) {
        final SpellAbility replace = AbilityFactory.getAbility(
                "DB$ ReplaceCards | Defined$ You | Zones$ " + zones + " "
                        + "| ValidCards$ Card.Black "
                        + "| ReplacementValid$ Card.nonBlack+nonColorless "
                        + "| MatchManaValue$ True",
                host);
        replace.setActivatingPlayer(player);
        return replace;
    }

    private static ReplaceCardsEffect.ZoneReplacementPlan plan(
            final Player player, final ZoneType zone,
            final Predicate<Card> predicate) {
        return new ReplaceCardsEffect.ZoneReplacementPlan(
                player, zone, ReplaceCardsEffect.collectIndexedMatches(
                player.getCardsIn(zone), predicate));
    }

    private record Probe(int index) {
    }

    private static final class CountingRandom extends Random {
        private static final long serialVersionUID = 7440195180872353061L;
        private int calls;

        @Override
        public int nextInt(final int bound) {
            calls++;
            return 0;
        }
    }
}
