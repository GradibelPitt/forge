package forge.game.combat;

import java.nio.file.Paths;
import java.util.Collections;

import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.util.Lang;
import forge.util.Localizer;

public class CombatUtilTest {

    private static void addKeyword(final Card card, final String keyword) {
        card.getCurrentState().addIntrinsicKeyword(keyword, false);
        card.updateKeywordsCache();
    }

    @BeforeClass
    public void initializeLocalizer() {
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void superreachAppliesOnlyWhenTheAttackerDoesNotIgnoreIt() {
        final Card attacker = new Card(1, null);
        final Card blocker = new Card(2, null);
        addKeyword(blocker, "Superreach");

        AssertJUnit.assertTrue(CombatUtil.superreachApplies(attacker, blocker));

        addKeyword(attacker, "Ignore Superreach");
        AssertJUnit.assertFalse(CombatUtil.superreachApplies(attacker, blocker));
    }

    @Test
    public void superreachDoesNotApplyToAnOrdinaryBlocker() {
        final Card attacker = new Card(1, null);
        final Card blocker = new Card(2, null);

        AssertJUnit.assertFalse(CombatUtil.superreachApplies(attacker, blocker));
    }

    @Test
    public void superreachBlocksAnAttackerWithStackedEvasionKeywords() {
        Lang.createInstance("en-US");
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Superreach test"));
        final Player defender = new Player("Defender", game, 1);

        final Card attacker = new Card(1, game);
        attacker.setOwner(defender);
        attacker.addType("Creature");
        attacker.setBasePower(1);
        addKeyword(attacker, "Flying");
        addKeyword(attacker, "Fear");
        addKeyword(attacker, "Menace");
        addKeyword(attacker, "Shadow");
        addKeyword(attacker, "Landwalk:Land");
        addKeyword(attacker, "Horsemanship");
        addKeyword(attacker, "Skulk");

        final Card superreachBlocker = new Card(2, game);
        superreachBlocker.setOwner(defender);
        superreachBlocker.addType("Creature");
        superreachBlocker.setBasePower(2);
        addKeyword(superreachBlocker, "Superreach");

        final Card ordinaryBlocker = new Card(3, game);
        ordinaryBlocker.setOwner(defender);
        ordinaryBlocker.addType("Creature");
        ordinaryBlocker.setBasePower(2);

        AssertJUnit.assertTrue(CombatUtil.canBlock(attacker, superreachBlocker, false));
        AssertJUnit.assertFalse(CombatUtil.canBlock(attacker, ordinaryBlocker, false));
    }
}
