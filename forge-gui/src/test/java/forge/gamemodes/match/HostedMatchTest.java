package forge.gamemodes.match;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.gamemodes.net.ProtocolMethod;
import forge.gui.interfaces.IGuiGame;
import forge.util.Lang;
import forge.util.Localizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostedMatchTest {
    @BeforeAll
    static void initializeLocalizer() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    void matchConfigurationDisablesExperimentalSnapshotRestore() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Hosted match test"));
        game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;

        HostedMatch.disableExperimentalSnapshotRestore(game);

        assertFalse(game.EXPERIMENTAL_RESTORE_SNAPSHOT);
    }

    @Test
    void staleSubgameStartAndEndEventsDoNotMutateNewerGameUiState() {
        final Game oldRootGame = createGame(null, "old root");
        final Game oldSubgame = createGame(oldRootGame, "old subgame");
        final Game newRootGame = createGame(null, "new root");
        final Game newSubgame = createGame(newRootGame, "new subgame");
        final HostedMatch hostedMatch = new HostedMatch();
        final AtomicInteger guiMutations = new AtomicInteger();
        hostedMatch.subGameCount = 4;
        hostedMatch.resetActiveControllerTracking(newRootGame, Collections.emptyList());

        assertFalse(hostedMatch.runSubgameStartUiUpdateIfCurrent(
                oldSubgame, guiMutations::incrementAndGet));
        assertFalse(hostedMatch.runSubgameEndUiUpdateIfCurrent(
                oldRootGame, guiMutations::incrementAndGet));

        assertEquals(4, hostedMatch.subGameCount);
        assertEquals(0, guiMutations.get());
        assertTrue(hostedMatch.runSubgameStartUiUpdateIfCurrent(
                newSubgame, guiMutations::incrementAndGet));
        assertTrue(hostedMatch.runSubgameEndUiUpdateIfCurrent(
                newRootGame, guiMutations::incrementAndGet));
        assertEquals(5, hostedMatch.subGameCount);
        assertEquals(2, guiMutations.get());
    }

    @Test
    void failedGameUsesFailureSpecificGuiShutdownWhileNormalEndDoesNot() {
        final List<String> callbacks = new ArrayList<>();
        final IGuiGame gui = (IGuiGame) Proxy.newProxyInstance(
                IGuiGame.class.getClassLoader(), new Class<?>[] { IGuiGame.class },
                (proxy, method, args) -> {
                    if (method.getName().startsWith("afterGame")) {
                        callbacks.add(method.getName()
                                + (args == null ? "" : ":" + args[0]));
                    }
                    return null;
                });

        HostedMatch.notifyGuiAfterGameEnd(gui, false, null);
        HostedMatch.notifyGuiAfterGameEnd(gui, true,
                "The match exited because of an unexpected error.");

        assertEquals(List.of(
                "afterGameEnd",
                "afterGameFailure:The match exited because of an unexpected error."),
                callbacks);
    }

    @Test
    void failureCallbackIsRegisteredForNetworkClientsWithItsMessage() {
        assertNotNull(ProtocolMethod.afterGameFailure.getMethod());
        assertArrayEquals(new Class<?>[] { String.class },
                ProtocolMethod.afterGameFailure.getArgTypes());
    }

    private static Game createGame(final Game maingame, final String title) {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Match match = maingame == null
                ? new Match(rules, Collections.emptyList(), title)
                : maingame.getMatch();
        if (maingame == null) {
            return new Game(Collections.emptyList(), rules, match);
        }
        return new Game(Collections.emptyList(), rules, match, maingame, -1);
    }
}
