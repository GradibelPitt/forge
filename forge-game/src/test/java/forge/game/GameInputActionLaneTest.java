package forge.game;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

public class GameInputActionLaneTest {
    @Test
    void oneGameIsFifoAndNeverRunsTwoActionsConcurrently() throws Exception {
        final GameInputActionLane lane = new GameInputActionLane();
        final GameInputActionLane.Scope scope = lane.begin("fifo");
        final AtomicBoolean released = new AtomicBoolean();
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger maxRunning = new AtomicInteger();
        final List<Integer> order = Collections.synchronizedList(
                new ArrayList<>());

        for (int i = 0; i < 100; i++) {
            final int value = i;
            assertTrue(lane.submit(scope, () -> {
                final int now = running.incrementAndGet();
                maxRunning.accumulateAndGet(now, Math::max);
                order.add(value);
                running.decrementAndGet();
            }));
        }
        assertTrue(lane.submit(scope, () -> released.set(true)));
        lane.pumpUntil(scope, released::get);

        assertEquals(100, order.size());
        for (int i = 0; i < order.size(); i++) {
            assertEquals(i, order.get(i));
        }
        assertEquals(1, maxRunning.get());
    }

    @Test
    void differentGamesCanPumpInParallel() throws Exception {
        final GameInputActionLane first = new GameInputActionLane();
        final GameInputActionLane second = new GameInputActionLane();
        final GameInputActionLane.Scope firstScope = first.begin("first");
        final GameInputActionLane.Scope secondScope = second.begin("second");
        final CountDownLatch bothRunning = new CountDownLatch(2);
        final CountDownLatch releaseActions = new CountDownLatch(1);
        final AtomicBoolean firstReleased = new AtomicBoolean();
        final AtomicBoolean secondReleased = new AtomicBoolean();

        assertTrue(first.submit(firstScope, () -> awaitBoth(
                bothRunning, releaseActions, firstReleased)));
        assertTrue(second.submit(secondScope, () -> awaitBoth(
                bothRunning, releaseActions, secondReleased)));

        final Thread firstPump = new Thread(() -> first.pumpUntil(
                firstScope, firstReleased::get));
        final Thread secondPump = new Thread(() -> second.pumpUntil(
                secondScope, secondReleased::get));
        firstPump.start();
        secondPump.start();
        assertTrue(bothRunning.await(2, TimeUnit.SECONDS));
        releaseActions.countDown();
        firstPump.join(2_000);
        secondPump.join(2_000);
        assertFalse(firstPump.isAlive());
        assertFalse(secondPump.isAlive());
    }

    @Test
    void exceptionDoesNotPoisonFollowingCancel() {
        final GameInputActionLane lane = new GameInputActionLane();
        final GameInputActionLane.Scope scope = lane.begin("exception");
        final AtomicBoolean cancelRan = new AtomicBoolean();
        assertTrue(lane.submit(scope, () -> {
            throw new IllegalStateException("expected");
        }));
        assertTrue(lane.submit(scope, () -> cancelRan.set(true)));
        lane.pumpUntil(scope, cancelRan::get);
        assertTrue(cancelRan.get());
    }

    @Test
    void observingReleaseAtomicallySealsAndDrainsAcceptedWork() {
        final GameInputActionLane lane = new GameInputActionLane();
        final GameInputActionLane.Scope scope = lane.begin("drain");
        final AtomicBoolean released = new AtomicBoolean();
        final AtomicInteger completed = new AtomicInteger();
        assertTrue(lane.submit(scope, () -> released.set(true)));
        assertTrue(lane.submit(scope, completed::incrementAndGet));
        lane.pumpUntil(scope, released::get);
        assertEquals(1, completed.get());
        assertFalse(lane.submit(scope, completed::incrementAndGet));
    }

    @Test
    void nestedScopeIsRecursivelyPumpedByTheSameGameThread() {
        final GameInputActionLane lane = new GameInputActionLane();
        final GameInputActionLane.Scope outer = lane.begin("outer");
        final AtomicBoolean outerReleased = new AtomicBoolean();
        final List<String> order = new ArrayList<>();

        assertTrue(lane.submit(outer, () -> {
            order.add("outer-start");
            final GameInputActionLane.Scope inner = lane.begin("inner");
            final AtomicBoolean innerReleased = new AtomicBoolean();
            assertTrue(lane.submit(inner, () -> {
                order.add("inner");
                innerReleased.set(true);
            }));
            lane.pumpUntil(inner, innerReleased::get);
            order.add("outer-end");
            outerReleased.set(true);
        }));
        lane.pumpUntil(outer, outerReleased::get);
        assertEquals(List.of("outer-start", "inner", "outer-end"), order);
    }

    @Test
    void secondPumpThreadIsRejected() throws Exception {
        final GameInputActionLane lane = new GameInputActionLane();
        final GameInputActionLane.Scope scope = lane.begin("owned");
        final CountDownLatch actionStarted = new CountDownLatch(1);
        final CountDownLatch releaseAction = new CountDownLatch(1);
        final AtomicBoolean released = new AtomicBoolean();
        assertTrue(lane.submit(scope, () -> {
            actionStarted.countDown();
            try {
                releaseAction.await();
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            released.set(true);
        }));
        final Thread owner = new Thread(() -> lane.pumpUntil(
                scope, released::get));
        owner.start();
        assertTrue(actionStarted.await(2, TimeUnit.SECONDS));
        boolean rejected = false;
        try {
            lane.pumpUntil(scope, () -> false);
        } catch (final IllegalStateException expected) {
            rejected = true;
        }
        assertTrue(rejected);
        releaseAction.countDown();
        owner.join(2_000);
    }

    @Test(timeOut = 2_000)
    void queueIsBoundedAndTerminateCreatesFreshEpoch() {
        final GameInputActionLane lane = new GameInputActionLane();
        final GameInputActionLane.Scope old = lane.begin("old");
        final AtomicBoolean cleanupRan = new AtomicBoolean();
        lane.setTerminationCleanup(old, () -> cleanupRan.set(true));
        for (int i = 0; i < GameInputActionLane.MAX_PENDING_ACTIONS; i++) {
            assertTrue(lane.submit(old, () -> { }));
        }
        assertFalse(lane.submit(old, () -> { }));

        lane.terminate();
        assertFalse(lane.submit(old, () -> { }));
        lane.pumpUntil(old, () -> false);
        assertTrue(cleanupRan.get());
        final GameInputActionLane.Scope fresh = lane.begin("fresh");
        final AtomicBoolean released = new AtomicBoolean();
        assertTrue(lane.submit(fresh, () -> released.set(true)));
        lane.pumpUntil(fresh, released::get);
    }

    @Test
    void criticalCleanupCannotBeStarvedByFullNormalQueue() {
        final GameInputActionLane lane = new GameInputActionLane();
        final GameInputActionLane.Scope scope = lane.begin("critical");
        final AtomicInteger normalRan = new AtomicInteger();
        final AtomicBoolean cleanupRan = new AtomicBoolean();
        for (int i = 0; i < GameInputActionLane.MAX_PENDING_ACTIONS; i++) {
            assertTrue(lane.submit(scope, normalRan::incrementAndGet));
        }
        assertTrue(lane.submitCritical(scope, () -> cleanupRan.set(true)));
        assertFalse(lane.submit(scope, normalRan::incrementAndGet));
        lane.pumpUntil(scope, cleanupRan::get);
        assertEquals(GameInputActionLane.MAX_PENDING_ACTIONS,
                normalRan.get());
        assertTrue(cleanupRan.get());
    }

    @Test
    void rejectedInitializationScopesCanBeAbandonedWithoutLeaking() {
        final GameInputActionLane lane = new GameInputActionLane();
        final GameInputActionLane.Scope full = lane.begin("full");
        for (int i = 0; i < GameInputActionLane.MAX_PENDING_ACTIONS; i++) {
            assertTrue(lane.submit(full, () -> { }));
        }

        for (int i = 0; i < 20_000; i++) {
            final GameInputActionLane.Scope rejected = lane.begin("rejected");
            assertFalse(lane.submit(rejected, () -> { }));
            assertTrue(lane.abandon(rejected));
        }
        assertEquals(1, lane.activeScopeCount());

        lane.terminate();
        lane.pumpUntil(full, () -> false);
        assertEquals(0, lane.activeScopeCount());
        final GameInputActionLane.Scope fresh = lane.begin("fresh-after-full");
        final AtomicBoolean released = new AtomicBoolean();
        assertTrue(lane.submit(fresh, () -> released.set(true)));
        lane.pumpUntil(fresh, released::get);
        assertTrue(released.get());
        assertEquals(0, lane.activeScopeCount());
    }

    @Test
    void nestedScopeHasPriorityThenOuterFifoAndCleanupResume() {
        final GameInputActionLane lane = new GameInputActionLane();
        final GameInputActionLane.Scope outer = lane.begin("outer-priority");
        final List<String> order = new ArrayList<>();
        final AtomicBoolean cleaned = new AtomicBoolean();
        assertTrue(lane.submit(outer, () -> {
            order.add("outer-start");
            final GameInputActionLane.Scope inner = lane.begin("inner-priority");
            final AtomicBoolean innerDone = new AtomicBoolean();
            assertTrue(lane.submit(outer, () -> order.add("outer-late")));
            assertTrue(lane.submitCritical(outer, () -> {
                order.add("outer-cleanup");
                cleaned.set(true);
            }));
            assertTrue(lane.submit(inner, () -> {
                order.add("inner");
                innerDone.set(true);
            }));
            lane.pumpUntil(inner, innerDone::get);
            order.add("outer-end");
        }));
        lane.pumpUntil(outer, cleaned::get);
        assertEquals(List.of("outer-start", "inner", "outer-end",
                "outer-late", "outer-cleanup"), order);
    }

    @Test
    void gameActionEpochResetRejectsOldScopeAndSeparateGameIsIndependent() {
        final GameAction firstGame = new GameAction(null);
        final GameAction secondGame = new GameAction(null);
        final GameAction.InputActionScope old = firstGame
                .beginInputActionScope("restart-old");
        final GameAction.InputActionScope other = secondGame
                .beginInputActionScope("subgame-independent");
        final AtomicBoolean otherRan = new AtomicBoolean();
        assertTrue(secondGame.invokeInputAction(other,
                () -> otherRan.set(true)));

        firstGame.terminateInputActions();
        assertFalse(firstGame.invokeInputAction(old, () -> { }));
        final GameAction.InputActionScope fresh = firstGame
                .beginInputActionScope("restart-fresh");
        final AtomicBoolean freshRan = new AtomicBoolean();
        assertTrue(firstGame.invokeInputAction(fresh,
                () -> freshRan.set(true)));
        firstGame.pumpInputActionsUntil(fresh, freshRan::get);
        secondGame.pumpInputActionsUntil(other, otherRan::get);
        assertTrue(freshRan.get());
        assertTrue(otherRan.get());
    }

    private static void awaitBoth(final CountDownLatch bothRunning,
            final CountDownLatch releaseActions,
            final AtomicBoolean released) {
        bothRunning.countDown();
        try {
            releaseActions.await();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        released.set(true);
    }
}
