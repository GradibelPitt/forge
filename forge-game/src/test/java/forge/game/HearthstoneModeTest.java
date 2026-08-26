package forge.game;

import forge.LobbyPlayer;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
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
    public void everyOpposingCreatureIsAnAttackableDefender() {
        final Fixture fixture = new Fixture(true);
        final Player defender = fixture.addPlayer("Defender", 2, 2);
        final Card attacker = fixture.creature("Attacker", 3, 3);
        attacker.addIntrinsicKeyword("Haste");
        final Card target = fixture.creature(defender, "Target creature", 2, 2);
        final Card friendlyTarget = fixture.creature("Friendly target", 2, 2);

        Assert.assertTrue(CombatUtil.getAllPossibleDefenders(fixture.player).contains(target));
        Assert.assertTrue(CombatUtil.canAttack(attacker, target));
        Assert.assertFalse(CombatUtil.getAllPossibleDefenders(fixture.player)
                .contains(friendlyTarget));
        Assert.assertFalse(CombatUtil.canAttack(attacker, friendlyTarget));

        final Combat combat = new Combat(fixture.player);
        combat.addAttacker(attacker, target);

        Assert.assertSame(combat.getDefenderByAttacker(attacker), target);
        Assert.assertFalse(combat.removeAbsentCombatants());
        Assert.assertSame(combat.getDefenderByAttacker(attacker), target);
    }

    @Test
    public void creatureTargetsMirrorPairwiseBlockingRestrictions() {
        final Fixture fixture = new Fixture(true);
        final Player defender = fixture.addPlayer("Defender", 2, 2);
        final Card attacker = fixture.creature("Ordinary attacker", 3, 3);
        attacker.addIntrinsicKeyword("Haste");
        final Card flyingTarget = fixture.creature(defender, "Flying target", 2, 2);
        flyingTarget.addIntrinsicKeyword("Flying");
        final Card horsemanshipTarget = fixture.creature(defender, "Horsemanship target", 2, 2);
        horsemanshipTarget.addIntrinsicKeyword("Horsemanship");
        final Card shadowTarget = fixture.creature(defender, "Shadow target", 2, 2);
        shadowTarget.addIntrinsicKeyword("Shadow");
        final Card skulkTarget = fixture.creature(defender, "Skulk target", 2, 2);
        skulkTarget.addIntrinsicKeyword("Skulk");
        final Card menaceTarget = fixture.creature(defender, "Menace target", 2, 2);
        menaceTarget.addIntrinsicKeyword("Menace");
        final Card flyingAttacker = fixture.creature("Flying attacker", 3, 3);
        flyingAttacker.addIntrinsicKeyword("Flying");
        flyingAttacker.addIntrinsicKeyword("Haste");
        final Card reachAttacker = fixture.creature("Reach attacker", 3, 3);
        reachAttacker.addIntrinsicKeyword("Reach");
        reachAttacker.addIntrinsicKeyword("Haste");
        final Card horsemanshipAttacker = fixture.creature("Horsemanship attacker", 3, 3);
        horsemanshipAttacker.addIntrinsicKeyword("Horsemanship");
        horsemanshipAttacker.addIntrinsicKeyword("Haste");
        final Card shadowAttacker = fixture.creature("Shadow attacker", 3, 3);
        shadowAttacker.addIntrinsicKeyword("Shadow");
        shadowAttacker.addIntrinsicKeyword("Haste");
        final Card cannotBlockAttacker = fixture.creature("Cannot block attacker", 3, 3);
        cannotBlockAttacker.addIntrinsicKeyword("CARDNAME can't block.");
        cannotBlockAttacker.addIntrinsicKeyword("Haste");
        final Card weakAttacker = fixture.creature("Weak attacker", 2, 2);
        weakAttacker.addIntrinsicKeyword("Haste");
        final Card ordinaryTarget = fixture.creature(defender, "Ordinary target", 2, 2);

        Assert.assertFalse(CombatUtil.canAttack(attacker, flyingTarget));
        Assert.assertTrue(CombatUtil.canAttack(flyingAttacker, flyingTarget));
        Assert.assertTrue(CombatUtil.canAttack(reachAttacker, flyingTarget));
        Assert.assertFalse(CombatUtil.canAttack(attacker, horsemanshipTarget));
        Assert.assertTrue(CombatUtil.canAttack(horsemanshipAttacker, horsemanshipTarget));
        Assert.assertFalse(CombatUtil.canAttack(attacker, shadowTarget));
        Assert.assertTrue(CombatUtil.canAttack(shadowAttacker, shadowTarget));
        Assert.assertFalse(CombatUtil.canAttack(shadowAttacker, ordinaryTarget));
        Assert.assertFalse(CombatUtil.canAttack(attacker, skulkTarget));
        Assert.assertTrue(CombatUtil.canAttack(weakAttacker, skulkTarget));
        Assert.assertTrue(CombatUtil.canAttack(attacker, menaceTarget),
                "minimum blocker counts do not make each individual blocker illegal");
        Assert.assertTrue(CombatUtil.canAttack(cannotBlockAttacker, ordinaryTarget),
                "the target's pairwise evasion matters, not the attacker's general readiness to block");
    }

    @Test
    public void automaticAttackableDefendersAreInactiveOutsideHearthstoneMode() {
        final Fixture fixture = new Fixture(false);
        final Player defender = fixture.addPlayer("Defender", 2, 2);
        final Card attacker = fixture.creature("Attacker", 3, 3);
        attacker.addIntrinsicKeyword("Haste");
        final Card target = fixture.creature(defender, "Target creature", 2, 2);

        Assert.assertFalse(CombatUtil.getAllPossibleDefenders(fixture.player).contains(target));
        Assert.assertFalse(CombatUtil.canAttack(attacker, target));
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
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player, false, 1);
            game.setAge(GameStage.Play);
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
