package forge.gamemodes.match;

import forge.game.Game;
import forge.game.Match;
import forge.player.PlayerControllerHuman;

import java.util.List;
import java.util.Objects;

/**
 * Immutable dependencies captured before a game task is submitted.
 */
record MatchGameTaskContext(Game game, Match match, int humanCount, Runnable startGameHook,
        Runnable endGameHook, List<PlayerControllerHuman> humanControllers) {
    MatchGameTaskContext {
        Objects.requireNonNull(game);
        Objects.requireNonNull(match);
        humanControllers = List.copyOf(humanControllers);
    }
}
