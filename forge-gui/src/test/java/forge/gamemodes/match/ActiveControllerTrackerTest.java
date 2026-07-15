package forge.gamemodes.match;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ActiveControllerTrackerTest {
    @Test
    void tracksBaselineAndActiveSubgameControllersByIdentity() {
        final Object game = new Object();
        final EqualController topLevel = new EqualController("top-level");
        final EqualController subgame = new EqualController("subgame");
        final ActiveControllerTracker<EqualController> tracker = new ActiveControllerTracker<>();

        tracker.reset(game, List.of(topLevel));
        tracker.replaceActive(game, List.of(subgame, subgame));

        final List<EqualController> tracked = tracker.snapshotForFailure(game);
        assertEquals(2, tracked.size());
        assertSame(topLevel, tracked.get(0));
        assertSame(subgame, tracked.get(1));
    }

    @Test
    void replacingActiveControllersDropsFinishedSubgameAndRejectsStaleGameUpdates() {
        final Object oldGame = new Object();
        final Object newGame = new Object();
        final EqualController oldTopLevel = new EqualController("old-top-level");
        final EqualController oldSubgame = new EqualController("old-subgame");
        final EqualController newTopLevel = new EqualController("new-top-level");
        final ActiveControllerTracker<EqualController> tracker = new ActiveControllerTracker<>();

        tracker.reset(oldGame, List.of(oldTopLevel));
        tracker.replaceActive(oldGame, List.of(oldSubgame));
        tracker.replaceActive(oldGame, List.of(oldTopLevel));

        final List<EqualController> afterSubgame = tracker.snapshotForFailure(oldGame);
        assertEquals(1, afterSubgame.size());
        assertSame(oldTopLevel, afterSubgame.get(0));

        tracker.reset(newGame, List.of(newTopLevel));
        tracker.replaceActive(oldGame, List.of(oldSubgame));

        assertEquals(List.of(), tracker.snapshotForFailure(oldGame));
        final List<EqualController> trackedForNewGame = tracker.snapshotForFailure(newGame);
        assertEquals(1, trackedForNewGame.size());
        assertSame(newTopLevel, trackedForNewGame.get(0));
    }

    private static final class EqualController {
        private final String name;

        private EqualController(final String name) {
            this.name = name;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof EqualController;
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
