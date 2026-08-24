package forge.game.phase;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Collections;

public class WindfuryTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void multipleWindfuryCreaturesCreateOnlyOneExtraCombat() throws Exception {
        final Fixture fixture = new Fixture();
        fixture.creature(fixture.attacker, "First Windfury", true);
        fixture.creature(fixture.attacker, "Second Windfury", true);
        fixture.creature(fixture.defender, "Opposing Windfury", true);
        setCombatsThisTurn(fixture.phaseHandler, 1);
        fixture.phaseHandler.devModeSet(PhaseType.COMBAT_END,
                fixture.attacker, false, 1);

        final ExtraPhase scheduled = fixture.phaseHandler
                .scheduleWindfuryCombatIfNeeded();

        Assert.assertNotNull(scheduled);
        Assert.assertTrue(scheduled.isWindfuryCombat());
        Assert.assertTrue(fixture.phaseHandler.hasExtraPhaseAfter(
                PhaseType.COMBAT_END, PhaseType.COMBAT_BEGIN));
        Assert.assertNull(fixture.phaseHandler.scheduleWindfuryCombatIfNeeded(),
                "windfury is shared and must not schedule once per creature");
    }

    @Test
    public void windfuryCombatUntapsAndAllowsOnlyWindfuryCreaturesToAttack() {
        final Fixture fixture = new Fixture();
        final Card windfury = fixture.creature(fixture.attacker,
                "Windfury Creature", true);
        final Card ordinary = fixture.creature(fixture.attacker,
                "Ordinary Creature", false);
        windfury.setTapped(true);
        ordinary.setTapped(true);
        fixture.phaseHandler.devModeSet(PhaseType.COMBAT_BEGIN,
                fixture.attacker, false, 1);

        fixture.phaseHandler.beginWindfuryCombat();

        Assert.assertTrue(fixture.phaseHandler.isWindfuryCombat());
        Assert.assertFalse(windfury.isTapped());
        Assert.assertTrue(ordinary.isTapped());
        Assert.assertTrue(CombatUtil.canAttack(windfury, fixture.defender));
        ordinary.setTapped(false);
        Assert.assertFalse(CombatUtil.canAttack(ordinary, fixture.defender));

        fixture.phaseHandler.endWindfuryCombat();
        Assert.assertFalse(fixture.phaseHandler.isWindfuryCombat());
        Assert.assertTrue(CombatUtil.canAttack(ordinary, fixture.defender));
    }

    @Test
    public void keywordIsRegisteredWithSharedCombatReminderText() {
        Assert.assertSame(Keyword.smartValueOf("Windfury"), Keyword.WINDFURY);
        Assert.assertTrue(Keyword.WINDFURY.getReminderText()
                .contains("Only creatures with windfury can attack"));
    }

    private static void setCombatsThisTurn(final PhaseHandler phaseHandler,
                                           final int value) throws Exception {
        final Field field = PhaseHandler.class.getDeclaredField("nCombatsThisTurn");
        field.setAccessible(true);
        field.setInt(phaseHandler, value);
    }

    private static final class Fixture {
        private final Game game;
        private final Player attacker;
        private final Player defender;
        private final PhaseHandler phaseHandler;

        private Fixture() {
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(Collections.emptyList(), rules,
                    new Match(rules, Collections.emptyList(), "Windfury test"));
            attacker = new Player("Attacker", game, 1);
            defender = new Player("Defender", game, 2);
            game.getPlayers().add(attacker);
            game.getPlayers().add(defender);
            attacker.setTeam(1);
            defender.setTeam(2);
            game.setAge(GameStage.Play);
            phaseHandler = game.getPhaseHandler();
        }

        private Card creature(final Player controller, final String name,
                              final boolean windfury) {
            final Card card = new Card(game.nextCardId(), game);
            card.setName(name);
            card.setOwner(controller);
            card.setController(controller, game.getNextTimestamp());
            card.addType("Creature");
            card.setBasePower(1);
            card.setBaseToughness(1);
            card.setSickness(false);
            if (windfury) {
                card.getCurrentState().addIntrinsicKeyword("Windfury", false);
                card.updateKeywordsCache();
            }
            controller.getZone(ZoneType.Battlefield).add(card);
            card.setSickness(false);
            return card;
        }
    }
}
