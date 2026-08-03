package forge.game;

import forge.CardStorageReader;
import forge.StaticData;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.card.CardRarity;
import forge.card.CardRules;
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
import java.util.ArrayList;
import java.util.List;

public class GameRuleCardTest {
    @BeforeClass
    public void initializeGameData() {
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
    public void gameRuleLeavesTheLibraryBeforeOpeningHandsAreDrawn() {
        final Fixture fixture = new Fixture();
        final Card gameRule = fixture.card("Game Rule", true);
        final Card normal = fixture.card("Normal Card", false);
        fixture.player.getZone(ZoneType.Library).add(gameRule);
        fixture.player.getZone(ZoneType.Library).add(normal);

        GameAction.exileGameRulesBeforeMulligan(fixture.game);

        Assert.assertTrue(fixture.player.getCardsIn(ZoneType.Exile)
                .anyMatch(card -> card.getName().equals("Game Rule")));
        Assert.assertTrue(normal.isInZone(ZoneType.Library));
        Assert.assertEquals(fixture.player.getCardsIn(ZoneType.Library).size(), 1);
    }

    @Test
    public void exiledHearthstoneStillCreatesAnEmblemForEveryPlayer() throws Exception {
        final Fixture fixture = new Fixture();
        final Player opponent = fixture.addPlayer("Opponent", 2, 2);
        final Path script = Paths.get("..", "custom", "cards", "colorless",
                "\u7089\u77f3\u4f20\u8bf4.txt").toAbsolutePath().normalize();
        final CardRules rules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8),
                "\u7089\u77f3\u4f20\u8bf4");
        final Card hearthstone = CardFactory.getCard(
                new PaperCard(rules, "PH01", CardRarity.MythicRare),
                fixture.player, fixture.game);
        fixture.player.getZone(ZoneType.Library).add(hearthstone);

        GameAction.exileGameRulesBeforeMulligan(fixture.game);

        final Card exiledHearthstone = fixture.player.getCardsIn(ZoneType.Exile).stream()
                .filter(card -> card.getName().equals("\u7089\u77f3\u4f20\u8bf4"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Hearthstone was not moved to exile before the mulligan"));
        fixture.game.setAge(GameStage.Play);
        final Trigger newGame = fixture.game.getTriggerHandler()
                .getActiveTrigger(TriggerType.NewGame, AbilityKey.newMap()).stream()
                .filter(trigger -> trigger.getHostCard().equals(exiledHearthstone))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the exiled Hearthstone card has no active NewGame trigger"));
        final SpellAbility createEmblems = newGame.getOverridingAbility()
                .copy(exiledHearthstone, fixture.player, false, true);
        createEmblems.setActivatingPlayer(fixture.player);
        AbilityUtils.resolve(createEmblems);

        assertHasHearthstoneEmblem(fixture.player);
        assertHasHearthstoneEmblem(opponent);
        Assert.assertEquals(fixture.player.getLife(), 30);
        Assert.assertEquals(opponent.getLife(), 30);
    }

    @Test
    public void emergencyDrawGuardExilesGameRuleAndDrawsTheNextCard() {
        final Fixture fixture = new Fixture();
        final Card gameRule = fixture.card("Game Rule", true);
        final Card normal = fixture.card("Normal Card", false);
        fixture.player.getZone(ZoneType.Library).add(gameRule);
        fixture.player.getZone(ZoneType.Library).add(normal);

        Assert.assertEquals(fixture.player.drawCards(1).size(), 1);
        Assert.assertTrue(fixture.player.getCardsIn(ZoneType.Exile)
                .anyMatch(card -> card.getName().equals("Game Rule")));
        Assert.assertTrue(fixture.player.getCardsIn(ZoneType.Hand)
                .anyMatch(card -> card.getName().equals("Normal Card")));
    }

    @Test
    public void emergencyDrawGuardAlsoProtectsBottomDraws() {
        final Fixture fixture = new Fixture();
        final Card normal = fixture.card("Normal Card", false);
        final Card gameRule = fixture.card("Game Rule", true);
        fixture.player.getZone(ZoneType.Library).add(normal);
        fixture.player.getZone(ZoneType.Library).add(gameRule);
        fixture.player.getKeywords().add(
                "You draw cards from the bottom of your library instead of the top of your library.");

        Assert.assertEquals(fixture.player.drawCards(1).size(), 1);
        Assert.assertTrue(fixture.player.getCardsIn(ZoneType.Exile)
                .anyMatch(card -> card.getName().equals("Game Rule")));
        Assert.assertTrue(fixture.player.getCardsIn(ZoneType.Hand)
                .anyMatch(card -> card.getName().equals("Normal Card")));
    }

    @Test
    public void gameRuleCannotBeMovedOutOfExileByCardEffects() {
        final Fixture fixture = new Fixture();
        final Card gameRule = fixture.card("Game Rule", true);
        fixture.player.getZone(ZoneType.Library).add(gameRule);
        final Card exiled = fixture.game.getAction().moveTo(
                ZoneType.Exile, gameRule, null, AbilityKey.newMap());

        final Card result = fixture.game.getAction().moveTo(
                ZoneType.Hand, exiled, null, AbilityKey.newMap());

        Assert.assertSame(result, exiled);
        Assert.assertTrue(exiled.isInZone(ZoneType.Exile));
        Assert.assertTrue(fixture.player.getCardsIn(ZoneType.Hand).isEmpty());
    }

    private static void assertHasHearthstoneEmblem(final Player player) {
        final long emblems = player.getCardsIn(ZoneType.Command).stream()
                .filter(card -> card.isEmblem()
                        && card.getName().equals(
                                "Emblem \u2014 \u7089\u77f3\u4f20\u8bf4"))
                .count();
        Assert.assertEquals(emblems, 1L,
                player.getName() + " should receive the Hearthstone emblem");
    }

    private static final class Fixture {
        private final Game game;
        private final Player player;

        private Fixture() {
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(Collections.emptyList(), rules,
                    new Match(rules, Collections.emptyList(), "Game rule card test"));
            player = new Player("Player", game, 1);
            game.getPlayers().add(player);
            player.setTeam(1);
        }

        private Player addPlayer(final String name, final int id, final int team) {
            final Player added = new Player(name, game, id);
            game.getPlayers().add(added);
            added.setTeam(team);
            return added;
        }

        private Card card(final String name, final boolean gameRule) {
            final List<String> script = new ArrayList<>(List.of(
                    "Name:" + name,
                    "ManaCost:1",
                    "Types:Artifact",
                    "Oracle:Test card."));
            if (gameRule) {
                script.add(3, "K:GameRule");
            }
            final PaperCard paperCard = new PaperCard(
                    CardRules.fromScript(script), "TST", CardRarity.Common);
            return CardFactory.getCard(paperCard, player, game);
        }
    }
}
