package forge.game;

import forge.LobbyPlayer;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.combat.Combat;
import forge.game.player.IGameEntitiesFactory;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.List;

public class HearthstoneModeTest {
    @BeforeClass
    public void initializeLocalizer() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void modeStartsAtThirtyLifeWithSevenCardsAndTenCardMaximum() {
        final Fixture fixture = new Fixture(true);

        Assert.assertEquals(fixture.player.getLife(), 30);
        Assert.assertEquals(fixture.player.getStartingHandSize(), 7);
        Assert.assertEquals(fixture.player.getMaxHandSize(), 10);
        Assert.assertTrue(fixture.player.getCardsIn(ZoneType.Command).isEmpty(),
                "the mode must not create an emblem or other command-zone card");
        Assert.assertEquals(GameType.Hearthstone.getDeckFormat().getMainRange().getMinimum(),
                Integer.valueOf(30));
    }

    @Test
    public void modeUsesFatigueWithoutGrantingAPlayerKeyword() {
        final Fixture fixture = new Fixture(true);
        fixture.game.setAge(GameStage.Play);

        fixture.player.drawCards(3);

        Assert.assertEquals(fixture.player.getFatigueCount(), 3);
        Assert.assertEquals(fixture.player.getLife(), 24);
        Assert.assertFalse(fixture.player.hasKeyword(Player.FATIGUE_ON_EMPTY_DRAW_KEYWORD));
        Assert.assertFalse(fixture.player.checkLoseCondition());
    }

    @Test
    public void cleanupKeepsMarkedDamageOnlyInHearthstoneMode() {
        final Fixture hearthstone = new Fixture(true);
        final Card persistent = hearthstone.creature("Persistent", 2, 5);
        persistent.setDamage(2);

        HearthstoneMode.cleanupMarkedDamage(hearthstone.game);

        Assert.assertEquals(persistent.getDamage(), 2);

        final Fixture constructed = new Fixture(false);
        final Card temporary = constructed.creature("Temporary", 2, 5);
        temporary.setDamage(2);

        HearthstoneMode.cleanupMarkedDamage(constructed.game);

        Assert.assertEquals(temporary.getDamage(), 0);
    }

    @Test
    public void upkeepResourceIsAHiddenChosenBasicLandConjure() {
        final Fixture fixture = new Fixture(true);

        final SpellAbility ability = HearthstoneMode.createUpkeepResourceAbility(fixture.player);

        Assert.assertNotNull(ability);
        Assert.assertEquals(ability.getParam("Spellbook"),
                "Plains,Island,Swamp,Mountain,Forest");
        Assert.assertEquals(ability.getParam("Zone"), "Hand");
        Assert.assertTrue(ability.hasParam("Conjure"));
        Assert.assertFalse(fixture.game.getCardsInGame().contains(ability.getHostCard()),
                "the rules source must stay hidden instead of becoming an artifact or emblem");
    }

    @Test
    public void selectedCreatureIsForcedToBlockItsAttackerWhenLegal() {
        final Fixture fixture = new Fixture(true);
        final Player defender = fixture.addPlayer("Defender", 2, 2);
        final Card attacker = fixture.creature("Attacker", 3, 3);
        final Card blocker = fixture.creature(defender, "Chosen blocker", 2, 2);
        final Combat combat = new Combat(fixture.player);
        combat.addAttacker(attacker, defender);
        combat.setHearthstoneForcedBlocker(attacker, blocker);

        HearthstoneMode.applyForcedBlockers(combat, defender);

        Assert.assertTrue(combat.isBlocking(blocker, attacker));
    }

    @Test
    public void forcedBlockDoesNotBypassMinimumBlockerRequirements() {
        final Fixture fixture = new Fixture(true);
        final Player defender = fixture.addPlayer("Defender", 2, 2);
        final Card attacker = fixture.creature("Menacing attacker", 3, 3);
        attacker.addIntrinsicKeyword("Menace");
        final Card blocker = fixture.creature(defender, "Lone blocker", 2, 2);
        final Combat combat = new Combat(fixture.player);
        combat.addAttacker(attacker, defender);
        combat.setHearthstoneForcedBlocker(attacker, blocker);

        HearthstoneMode.applyForcedBlockers(combat, defender);

        Assert.assertFalse(combat.isBlocking(blocker, attacker));
    }

    private static final class Fixture {
        private final Game game;
        private final Player player;

        private Fixture(final boolean hearthstone) {
            final RegisteredPlayer registered = new RegisteredPlayer(new Deck("Hearthstone mode test"))
                    .setPlayer(new TestLobbyPlayer("Player"));
            final List<RegisteredPlayer> players = List.of(registered);
            final GameRules rules = new GameRules(GameType.Constructed);
            if (hearthstone) {
                rules.addAppliedVariant(GameType.Hearthstone);
            }
            game = new Game(players, rules, new Match(rules, players, "Hearthstone mode test"));
            player = game.getPlayers().get(0);
            player.setTeam(1);
        }

        private Player addPlayer(final String name, final int id, final int team) {
            final Player added = new Player(name, game, id);
            game.getPlayers().add(added);
            added.setTeam(team);
            return added;
        }

        private Card creature(final String name, final int power, final int toughness) {
            return creature(player, name, power, toughness);
        }

        private Card creature(final Player owner, final String name, final int power, final int toughness) {
            final CardRules rules = CardRules.fromScript(List.of(
                    "Name:" + name,
                    "ManaCost:1",
                    "Types:Creature Test",
                    "PT:" + power + "/" + toughness,
                    "Oracle:Test creature."));
            final Card card = CardFactory.getCard(
                    new PaperCard(rules, "TST", CardRarity.Common), owner, game);
            owner.getZone(ZoneType.Battlefield).add(card);
            card.setController(owner, game.getNextTimestamp());
            return card;
        }
    }

    private static final class TestLobbyPlayer extends LobbyPlayer implements IGameEntitiesFactory {
        private TestLobbyPlayer(final String name) {
            super(name);
        }

        @Override
        public Player createIngamePlayer(final Game game, final int id) {
            return new Player(getName(), game, id);
        }

        @Override
        public PlayerController createMindSlaveController(final Player master, final Player slave) {
            return null;
        }

        @Override
        public void hear(final LobbyPlayer player, final String message) {
        }
    }
}
