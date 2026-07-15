package forge.gamemodes.match;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class MatchGameTaskContextTest {
    @Test
    void capturesGameTaskDependenciesBeforeQueuedExecution() {
        final GameRules oldRules = new GameRules(GameType.Constructed);
        final Match oldMatch = new Match(oldRules, Collections.emptyList(), "old match");
        final Game oldGame = new Game(Collections.emptyList(), oldRules, oldMatch);
        final GameRules newRules = new GameRules(GameType.Constructed);
        final Match newMatch = new Match(newRules, Collections.emptyList(), "new match");
        final Game newGame = new Game(Collections.emptyList(), newRules, newMatch);
        final Runnable oldStartHook = () -> { };
        final Runnable oldEndHook = () -> { };

        final MatchGameTaskContext captured = new MatchGameTaskContext(
                oldGame, oldMatch, 2, oldStartHook, oldEndHook, List.of());

        assertSame(oldGame, captured.game());
        assertSame(oldMatch, captured.match());
        assertEquals(2, captured.humanCount());
        assertSame(oldStartHook, captured.startGameHook());
        assertSame(oldEndHook, captured.endGameHook());
        assertNotSame(newGame, captured.game());
        assertNotSame(newMatch, captured.match());
        assertEquals(List.of(), captured.humanControllers());
    }
}
