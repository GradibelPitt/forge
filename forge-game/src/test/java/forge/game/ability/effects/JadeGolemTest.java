package forge.game.ability.effects;

import forge.CardStorageReader;
import forge.StaticData;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.card.CardFactory;
import forge.game.card.CounterType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

public class JadeGolemTest {
    @BeforeClass
    public void initializeCardData() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", path("forge-gui", "res", "languages"));

        new StaticData(
                new CardStorageReader(path("forge-gui", "res", "cardsfolder"), null, false),
                new CardStorageReader(path("forge-gui", "res", "tokenscripts"), null, false),
                new CardStorageReader(path("custom", "cards"), null, false),
                new CardStorageReader(path("custom", "tokens"), null, false),
                path("forge-gui", "res", "editions"),
                path("custom", "editions"),
                path("forge-gui", "res", "blockdata"),
                path("forge-gui", "res", "setlookup"),
                "Latest",
                true,
                true,
                true,
                true
        );
    }

    private static String path(final String first, final String... more) {
        Path result = Paths.get("..", first);
        for (final String element : more) {
            result = result.resolve(element);
        }
        return result.toAbsolutePath().normalize().toString();
    }

    @Test
    public void realScriptComputesEtbCountersPerControllerFromOneUpward() throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Jade Golem test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        controller.setLife(20, null);
        opponent.setLife(20, null);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, controller);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "green", "青玉魔像.txt")
                .toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "青玉魔像");

        final Card first = enterWithCalculatedCounters(cardRules, controller, game, 1);
        assertP1P1Counters(first, 1);
        createAndInitializeEmblem(first, controller, game);

        final Card controllerEmblem = jadeEmblem(controller);
        Assert.assertEquals(controllerEmblem.getCounters(CounterType.getType("STORAGE")), 1);
        assertStatsAndZone(first, 1, 1, controller);

        final Card second = enterWithCalculatedCounters(cardRules, controller, game, 2);
        assertP1P1Counters(second, 2);
        trackEntry(controllerEmblem, second, game);

        Assert.assertEquals(controllerEmblem.getCounters(CounterType.getType("STORAGE")), 2);
        assertP1P1Counters(first, 1);
        assertStatsAndZone(first, 1, 1, controller);
        assertStatsAndZone(second, 2, 2, controller);

        final Card opposingGolem = enterWithCalculatedCounters(cardRules, opponent, game, 1);
        assertP1P1Counters(opposingGolem, 1);
        final Trigger controllerTracker = zoneEntryTrigger(controllerEmblem);
        Assert.assertFalse(controllerTracker.performTest(zoneEntryParams(opposingGolem)),
                "one player's emblem must not count an opponent's Jade Golem");
        createAndInitializeEmblem(opposingGolem, opponent, game);

        final Card opponentEmblem = jadeEmblem(opponent);
        Assert.assertNotEquals(opponentEmblem, controllerEmblem);
        Assert.assertEquals(controllerEmblem.getCounters(CounterType.getType("STORAGE")), 2);
        Assert.assertEquals(opponentEmblem.getCounters(CounterType.getType("STORAGE")), 1);
        assertStatsAndZone(first, 1, 1, controller);
        assertStatsAndZone(second, 2, 2, controller);
        assertStatsAndZone(opposingGolem, 1, 1, opponent);
    }

    private static Card createGolem(final CardRules rules, final Player owner, final Game game) {
        return CardFactory.getCard(new PaperCard(rules, "PH01", CardRarity.Common), owner, game);
    }

    private static Card enterWithCalculatedCounters(final CardRules rules, final Player owner,
                                                    final Game game, final int expected) {
        final Card card = createGolem(rules, owner, game);
        final ReplacementEffect replacement = card.getReplacementEffects().stream()
                .filter(effect -> effect.getMode() == ReplacementType.Moved)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Jade Golem has no ETB replacement"));
        final SpellAbility addCounters = replacement.getOverridingAbility();
        Assert.assertEquals(addCounters.getParam("Defined"), "Self");
        Assert.assertEquals(addCounters.getParam("CounterType"), "P1P1");
        Assert.assertEquals(addCounters.getParam("CounterNum"), "X");
        Assert.assertEquals(AbilityUtils.calculateAmount(card, "X", addCounters), expected);

        card.setCounters(CounterType.getType("P1P1"), expected);
        owner.getZone(ZoneType.Library).add(card);
        final Card latestState = CardCopyService.getLKICopy(card);
        owner.getZone(ZoneType.Library).remove(card);
        owner.getZone(ZoneType.Battlefield).add(card, null, latestState);
        card.setController(owner, game.getNextTimestamp());
        return card;
    }

    private static void createAndInitializeEmblem(final Card golem, final Player controller,
                                                  final Game game) {
        final Trigger create = golem.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.ChangesZone)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Jade Golem has no emblem trigger"));
        Assert.assertTrue(create.isStatic(), "the 0/0 Golem must create its emblem immediately");
        final Map<AbilityKey, Object> params = zoneEntryParams(golem);
        Assert.assertTrue(create.performTest(params));
        Assert.assertTrue(create.requirementsCheck(game));
        resolveTrigger(create, golem, controller, params);

        final Card emblem = jadeEmblem(controller);
        final Trigger initialize = emblem.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.Always)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Jade emblem has no initializer"));
        Assert.assertTrue(initialize.isStatic(), "the first counter must be added immediately");
        Assert.assertTrue(initialize.requirementsCheck(game));
        final SpellAbility addInitialCounter = initialize.getOverridingAbility();
        Assert.assertEquals(addInitialCounter.getParam("Defined"), "Self");
        Assert.assertEquals(addInitialCounter.getParam("CounterType"), "STORAGE");
        Assert.assertEquals(addInitialCounter.getParam("CounterNum"), "1");
        emblem.setCounters(CounterType.getType("STORAGE"), 1);
        game.getAction().checkStaticAbilities(false);
    }

    private static void trackEntry(final Card emblem, final Card golem, final Game game) {
        final Trigger tracker = zoneEntryTrigger(emblem);
        Assert.assertTrue(tracker.isStatic(), "later entries must update the count immediately");
        final Map<AbilityKey, Object> params = zoneEntryParams(golem);
        Assert.assertTrue(tracker.performTest(params));
        Assert.assertTrue(tracker.requirementsCheck(game));
        final SpellAbility addCounter = tracker.getOverridingAbility();
        Assert.assertEquals(addCounter.getParam("Defined"), "Self");
        Assert.assertEquals(addCounter.getParam("CounterType"), "STORAGE");
        Assert.assertEquals(addCounter.getParam("CounterNum"), "1");
        final CounterType storage = CounterType.getType("STORAGE");
        emblem.setCounters(storage, emblem.getCounters(storage) + 1);
        game.getAction().checkStaticAbilities(false);
    }

    private static Trigger zoneEntryTrigger(final Card emblem) {
        return emblem.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.ChangesZone)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Jade emblem has no entry tracker"));
    }

    private static Map<AbilityKey, Object> zoneEntryParams(final Card card) {
        final Map<AbilityKey, Object> params = AbilityKey.mapFromCard(card);
        params.put(AbilityKey.Origin, ZoneType.Library.name());
        params.put(AbilityKey.Destination, ZoneType.Battlefield.name());
        params.put(AbilityKey.CardLKI, card);
        params.put(AbilityKey.Cause, null);
        return params;
    }

    private static void resolveTrigger(final Trigger trigger, final Card host,
                                       final Player controller,
                                       final Map<AbilityKey, Object> params) {
        final SpellAbility ability = trigger.getOverridingAbility()
                .copy(host, controller, false, true);
        ability.setActivatingPlayer(controller);
        ability.setTrigger(trigger);
        trigger.setTriggeringObjects(ability, params);
        AbilityUtils.resolve(ability);
    }

    private static Card jadeEmblem(final Player player) {
        return player.getCardsIn(ZoneType.Command).stream()
                .filter(card -> "Emblem — 青玉魔像计数".equals(card.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(player + " has no Jade Golem counter emblem"));
    }

    private static void assertStatsAndZone(final Card card, final int power,
                                           final int toughness, final Player controller) {
        Assert.assertEquals(card.getZone().getZoneType(), ZoneType.Battlefield,
                "a 0/0 Jade Golem must enter with its +1/+1 counters already on it");
        Assert.assertEquals(card.getController(), controller);
        Assert.assertEquals(card.getNetPower(), power);
        Assert.assertEquals(card.getNetToughness(), toughness);
    }

    private static void assertP1P1Counters(final Card card, final int expected) {
        Assert.assertEquals(card.getCounters(CounterType.getType("P1P1")), expected);
    }
}
