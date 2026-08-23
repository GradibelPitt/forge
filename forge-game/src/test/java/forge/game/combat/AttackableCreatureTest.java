package forge.game.combat;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Collections;

public class AttackableCreatureTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void opposingCreaturesCanAttackAnAttackableCreatureAsACardDefender() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Attackable creature test"));
        final Player attackingPlayer = new Player("Attacker", game, 1);
        final Player defendingPlayer = new Player("Defender", game, 2);
        game.getPlayers().add(attackingPlayer);
        game.getPlayers().add(defendingPlayer);
        attackingPlayer.setTeam(1);
        defendingPlayer.setTeam(2);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, attackingPlayer, false, 1);
        game.setAge(GameStage.Play);

        final Card doomsayer = creature(game, defendingPlayer, "Doomsayer",
                "Mode$ CanBeAttacked | ValidDefender$ Card.Self "
                        + "| ValidAttacker$ Creature.OppCtrl");
        final Card ordinaryCreature = creature(game, defendingPlayer, "Ordinary Creature");
        final Card opposingAttacker = creature(game, attackingPlayer, "Opposing Attacker");
        addKeyword(opposingAttacker, "Haste");
        final Card friendlyCreature = creature(game, defendingPlayer, "Friendly Creature");
        addKeyword(friendlyCreature, "Haste");

        Assert.assertTrue(CombatUtil.getAllPossibleDefenders(attackingPlayer).contains(doomsayer));
        Assert.assertFalse(CombatUtil.getAllPossibleDefenders(attackingPlayer)
                .contains(ordinaryCreature));
        Assert.assertTrue(CombatUtil.canAttack(opposingAttacker, doomsayer));
        Assert.assertFalse(CombatUtil.canAttack(friendlyCreature, doomsayer));

        final Combat combat = new Combat(attackingPlayer);
        Assert.assertTrue(combat.getDefenders().contains(doomsayer));
        combat.addAttacker(opposingAttacker, doomsayer);
        Assert.assertSame(combat.getDefenderByAttacker(opposingAttacker), doomsayer);
        Assert.assertSame(combat.getDefenderPlayerByAttacker(opposingAttacker), defendingPlayer);
        Assert.assertFalse(combat.removeAbsentCombatants());
        Assert.assertSame(combat.getDefenderByAttacker(opposingAttacker), doomsayer);
    }

    private static Card creature(final Game game, final Player owner, final String name) {
        return creature(game, owner, name, null);
    }

    private static Card creature(final Game game, final Player owner, final String name,
                                 final String staticAbility) {
        final Card card = new Card(game.nextCardId(), game);
        card.setName(name);
        card.setOwner(owner);
        card.setController(owner, game.getNextTimestamp());
        card.addType("Creature");
        card.setBasePower(1);
        card.setBaseToughness(1);
        if (staticAbility != null) {
            card.addStaticAbility(staticAbility);
        }
        owner.getZone(ZoneType.Battlefield).add(card);
        return card;
    }

    private static void addKeyword(final Card card, final String keyword) {
        card.getCurrentState().addIntrinsicKeyword(keyword, false);
        card.updateKeywordsCache();
    }
}
