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
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.keyword.Companion;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityCantBeCast;
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

public class ChefNethrazekTest {
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
    public void realScriptEnforcesTheCompanionRestrictionAndThirdTurnGate() throws Exception {
        final Game game = game();
        final Player controller = game.getPlayers().get(0);
        final Card chef = cardFromScript(game, controller);
        game.getAction().moveTo(ZoneType.Hand, chef, null, null);

        final Companion companion = (Companion) chef.getKeywords(Keyword.COMPANION)
                .iterator().next();
        Assert.assertEquals(companion.getDeckRestriction(), "Card.cmcLE3");

        game.getAction().moveTo(ZoneType.Library,
                vanilla(game, controller, "Three-drop", "3"), null, null);
        game.getAction().moveTo(ZoneType.Library,
                vanilla(game, controller, "Zero-drop", "0"), null, null);
        Assert.assertTrue(controller.deckMatchesDeckRestriction(
                chef, companion.getDeckRestriction()));

        game.getAction().moveTo(ZoneType.Library,
                vanilla(game, controller, "Four-drop", "4"), null, null);
        Assert.assertFalse(controller.deckMatchesDeckRestriction(
                chef, companion.getDeckRestriction()));

        final SpellAbility spell = chef.getSpellAbilities().getFirst();
        spell.setActivatingPlayer(controller);
        Assert.assertTrue(StaticAbilityCantBeCast.cantBeCastAbility(spell, chef, controller));
        controller.incrementTurn();
        Assert.assertTrue(StaticAbilityCantBeCast.cantBeCastAbility(spell, chef, controller));
        controller.incrementTurn();
        Assert.assertTrue(StaticAbilityCantBeCast.cantBeCastAbility(spell, chef, controller));
        controller.incrementTurn();
        Assert.assertFalse(StaticAbilityCantBeCast.cantBeCastAbility(spell, chef, controller));
    }

    @Test
    public void realScriptOnlyTriggersForTheChosenCompanion() throws Exception {
        final Game game = game();
        final Player controller = game.getPlayers().get(0);
        final Card chef = cardFromScript(game, controller);

        final Trigger castTrigger = chef.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.SpellCast)
                .findFirst().orElseThrow();
        Assert.assertEquals(castTrigger.getParam("Execute"), "TrigLands");
        Assert.assertEquals(castTrigger.getParam("ValidCard"), "Card.Self+IsCompanion");
        Assert.assertTrue(castTrigger.getActiveZone().contains(ZoneType.Stack));

        Assert.assertFalse(chef.isValid(new String[] {"Card.IsCompanion"},
                controller, chef, null));
        controller.getZone(ZoneType.Command).add(Player.createCompanionEffect(chef));
        Assert.assertTrue(chef.isValid(new String[] {"Card.IsCompanion"},
                controller, chef, null));

        final Card ordinaryCopy = cardFromScript(game, controller);
        Assert.assertFalse(ordinaryCopy.isValid(new String[] {"Card.IsCompanion"},
                controller, ordinaryCopy, null));

        final SpellAbility search = AbilityFactory.getAbility(chef.getSVar("TrigLands"), chef);
        Assert.assertEquals(search.getParam("Origin"), "Library");
        Assert.assertEquals(search.getParam("Destination"), "Battlefield");
        Assert.assertEquals(search.getParam("ChangeType"), "Land");
        Assert.assertEquals(search.getParam("ChangeNum"), "10");
        Assert.assertEquals(search.getParam("NoShuffle"), "True");
        Assert.assertFalse(search.hasParam("Tapped"));
    }

    private static Game game() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Chef Nethrazek test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(controller);
        game.setAge(GameStage.Play);
        return game;
    }

    private static Card cardFromScript(final Game game, final Player owner) throws Exception {
        final Path script = Paths.get("..", "custom", "cards", "green", "主厨奈瑟雷克.txt")
                .toAbsolutePath().normalize();
        final CardRules rules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "主厨奈瑟雷克");
        return CardFactory.getCard(
                new PaperCard(rules, "PH01", CardRarity.MythicRare), owner, game);
    }

    private static Card vanilla(final Game game, final Player owner,
                                final String name, final String manaCost) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:" + manaCost,
                "Types:Artifact",
                "Oracle:Test card."));
        return CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), owner, game);
    }
}
