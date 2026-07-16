package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.event.GameEventSubgameEnd;
import forge.game.event.GameEventSubgameStart;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class PlayerSpellRuleSubgameIntegrationTest extends AITest {
    private static final String RULE_KEY = "test:main-game-only";
    private static final String CHILD_RULE_KEY = "test:child-game-only";

    @Test(timeOut = 15_000)
    public void realSubgameRoundTripKeepsRulesScopedToMainGame() {
        final Game mainGame = initAndCreateGame();
        final Player activatingPlayer = mainGame.getPlayers().get(1);
        activatingPlayer.getSpellRuleRegistry().register(
                RULE_KEY,
                "Card.nonColorless",
                "Spell",
                2,
                "AnyType->AnyColor");
        final List<String> mainStatesBefore = ruleStates(mainGame);

        final SubgameProbe probe = new SubgameProbe();
        mainGame.getMatch().subscribeToEvents(probe);
        final Card source = addCard("Karn Liberated", activatingPlayer);
        final SpellAbility subgame = AbilityFactory.getAbility(
                "DB$ Subgame | StartingLife$ 5", source);
        subgame.setActivatingPlayer(activatingPlayer);

        AbilityUtils.resolve(subgame);

        Assert.assertEquals(probe.startCount, 1);
        Assert.assertEquals(probe.endCount, 1);
        Assert.assertNotNull(probe.subgame);
        Assert.assertSame(probe.subgame.getMaingame(), mainGame);
        Assert.assertSame(probe.endedMainGame, mainGame);
        Assert.assertTrue(probe.subgame.isGameOver());
        Assert.assertTrue(probe.subgame.getOutcome().isDraw());
        Assert.assertEquals(probe.initialChildRuleStates,
                emptyRuleStates(mainGame.getPlayers().size()));
        Assert.assertEquals(ruleStates(probe.subgame),
                probe.childRuleStatesAfterRegistration);
        Assert.assertEquals(probe.childRuleStatesAfterRegistration.stream()
                .filter(state -> !state.isEmpty()).count(), 1L);
        Assert.assertEquals(ruleStates(mainGame), mainStatesBefore);
    }

    private static List<String> ruleStates(final Game game) {
        final List<String> states = new ArrayList<>();
        for (final Player player : game.getPlayers()) {
            states.add(player.getSpellRuleRegistry().toStateString());
        }
        return states;
    }

    private static List<String> emptyRuleStates(final int playerCount) {
        final List<String> states = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            states.add("");
        }
        return states;
    }

    private static final class SubgameProbe {
        private Game subgame;
        private Game endedMainGame;
        private List<String> initialChildRuleStates = List.of();
        private List<String> childRuleStatesAfterRegistration = List.of();
        private int startCount;
        private int endCount;

        @Subscribe
        public void onSubgameStart(final GameEventSubgameStart event) {
            startCount++;
            subgame = event.subgame();
            initialChildRuleStates = ruleStates(subgame);
            subgame.getPlayers().get(0).getSpellRuleRegistry().register(
                    CHILD_RULE_KEY,
                    "Card.nonColorless",
                    "Spell",
                    1,
                    "AnyType->AnyColor");
            childRuleStatesAfterRegistration = ruleStates(subgame);
            for (final Player player : subgame.getPlayers()) {
                player.intentionalDraw();
            }
            subgame.setGameOver(GameEndReason.Draw);
        }

        @Subscribe
        public void onSubgameEnd(final GameEventSubgameEnd event) {
            endCount++;
            endedMainGame = event.maingame();
        }
    }
}
