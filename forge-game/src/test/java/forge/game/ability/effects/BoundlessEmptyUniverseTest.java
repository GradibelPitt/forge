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
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.card.CounterType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class BoundlessEmptyUniverseTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
        if (StaticData.instance() == null) {
            final String cards = Paths.get("..", "forge-gui", "res", "cardsfolder")
                    .toAbsolutePath().normalize().toString();
            final String editions = Paths.get("..", "forge-gui", "res", "editions")
                    .toAbsolutePath().normalize().toString();
            final String customEditions = Paths.get("..", "custom", "editions")
                    .toAbsolutePath().normalize().toString();
            final String blockData = Paths.get("..", "forge-gui", "res", "blockdata")
                    .toAbsolutePath().normalize().toString();
            new StaticData(new CardStorageReader(cards, null, true), null, editions,
                    customEditions, blockData, "Latest", true, true);
        }
    }

    @Test
    public void realScriptTracksZoneEntriesAndExilesOnlyUncontrolledPermanents() throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Boundless Empty Universe test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(controller);
        game.setAge(GameStage.Play);

        addVanillaCard(game, controller, controller, ZoneType.Hand, "Controller hand card");
        addVanillaCard(game, opponent, opponent, ZoneType.Hand, "Opponent hand card");

        final Path script = Paths.get("..", "custom", "cards", "colorless", "无界空宇.txt")
                .toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "无界空宇");
        final Card universe = CardFactory.getCard(
                new PaperCard(cardRules, "PH01", CardRarity.MythicRare), controller, game);

        final Trigger newGame = universe.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.NewGame)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the real script has no NewGame trigger"));
        final SpellAbility createEmblem = newGame.getOverridingAbility()
                .copy(universe, controller, false, true);
        createEmblem.setActivatingPlayer(controller);
        AbilityUtils.resolve(createEmblem);

        final Card emblem = controller.getCardsIn(ZoneType.Command).stream()
                .filter(Card::isEmblem)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the reduction emblem was not created"));
        Assert.assertFalse(emblem.hasChosenNumber(),
                "the fixed two-player opening-hand baseline should not require runtime hand counting");
        Assert.assertEquals(emblem.getStaticAbilities().size(), 1);
        final StaticAbility reducer = emblem.getStaticAbilities().get(0);
        Assert.assertEquals(
                AbilityUtils.calculateAmount(emblem, reducer.getParam("Amount"), reducer),
                14,
                "the initial reduction should include seven opening cards for each player");

        Assert.assertEquals(emblem.getTriggers().size(), 1);
        final Trigger zoneEntry = emblem.getTriggers().get(0);
        final Card movedCard = vanillaCard(game, opponent, opponent, "Opponent moved card");
        final Map<AbilityKey, Object> runParams = AbilityKey.mapFromCard(movedCard);
        runParams.put(AbilityKey.Origin, ZoneType.Library.name());
        runParams.put(AbilityKey.Destination, ZoneType.Hand.name());
        runParams.put(AbilityKey.CardLKI, movedCard);
        runParams.put(AbilityKey.Cause, null);
        Assert.assertTrue(zoneEntry.performTest(runParams),
                "the command-zone trigger should accept a nontoken card entering a hand");

        final SpellAbility addReduction = zoneEntry.getOverridingAbility();
        Assert.assertEquals(addReduction.getParam("CounterType"), "STORAGE");
        Assert.assertEquals(addReduction.getParam("CounterNum"), "1");
        emblem.setCounters(CounterType.getType("STORAGE"), 1);
        Assert.assertEquals(emblem.getCounters(CounterType.getType("STORAGE")), 1);
        Assert.assertEquals(
                AbilityUtils.calculateAmount(emblem, reducer.getParam("Amount"), reducer),
                15,
                "an opponent's later qualifying zone entry should add one more reduction");

        final Card controlledPermanent = addVanillaCard(
                game, controller, controller, ZoneType.Battlefield, "Controlled permanent");
        final Card uncontrolledPermanent = addVanillaCard(
                game, opponent, opponent, ZoneType.Battlefield, "Uncontrolled permanent");
        final SpellAbility exile = AbilityFactory.getAbility(
                universe.getSVar("ExileUncontrolledPermanents"), universe);
        exile.setActivatingPlayer(controller);
        Assert.assertEquals(exile.getParam("ChangeType"), "Permanent.YouDontCtrl");
        Assert.assertEquals(exile.getParam("Origin"), "Battlefield");
        Assert.assertEquals(exile.getParam("Destination"), "Exile");
        Assert.assertFalse(controlledPermanent.isValid(
                        exile.getParam("ChangeType"), controller, universe, exile),
                "the controller's permanent must not match the exile selector");
        Assert.assertTrue(uncontrolledPermanent.isValid(
                        exile.getParam("ChangeType"), controller, universe, exile),
                "a permanent the caster does not control must match the exile selector");
    }

    private static Card addVanillaCard(final Game game, final Player owner,
                                       final Player controller, final ZoneType zone,
                                       final String name) {
        final Card card = vanillaCard(game, owner, controller, name);
        game.getAction().moveTo(zone, card, null, null);
        return card;
    }

    private static Card vanillaCard(final Game game, final Player owner,
                                    final Player controller, final String name) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:1",
                "Types:Artifact",
                "Oracle:Test card."));
        final Card card = CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), owner, game);
        card.setController(controller, game.getNextTimestamp());
        return card;
    }
}
