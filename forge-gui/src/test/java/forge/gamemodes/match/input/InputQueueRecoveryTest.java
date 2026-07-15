package forge.gamemodes.match.input;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.player.PlayerControllerHuman;
import forge.util.Lang;
import forge.util.Localizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputQueueRecoveryTest {
    private IGuiBase previousGui;

    @BeforeAll
    static void initializeLocalizer() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @BeforeEach
    void rememberGui() {
        previousGui = GuiBase.getInterface();
        GuiBase.setInterface(testGui(false));
    }

    @AfterEach
    void restoreGui() {
        GuiBase.setInterface(previousGui);
    }

    @Test
    void syntheticLockTracksWhetherItIsTheProxyCurrentInput() {
        final PlayerControllerHuman controller = newController();
        final InputQueue queue = controller.getInputQueue();

        queue.updateObservers();
        final InputLockUI lock = assertInstanceOf(InputLockUI.class, controller.getInputProxy().getInput());
        assertTrue(lock.isActive());

        final RecordingInput replacement = new RecordingInput(controller, false);
        queue.setInput(replacement);

        assertSame(replacement, controller.getInputProxy().getInput());
        assertFalse(lock.isActive());
    }

    @Test
    void clearInputsStopsEachInputWhileItIsStillTheCurrentTop() {
        final PlayerControllerHuman controller = newController();
        final InputQueue queue = controller.getInputQueue();
        final RecordingInput first = new RecordingInput(controller, false);
        final RecordingInput second = new RecordingInput(controller, false);
        final RecordingInput third = new RecordingInput(controller, false);
        queue.setInput(first);
        queue.setInput(second);
        queue.setInput(third);

        queue.clearInputs();

        assertNull(queue.getInput());
        for (final RecordingInput input : List.of(first, second, third)) {
            assertEquals(1, input.stopCalls);
            assertTrue(input.wasCurrentWhenStopped,
                    "clearInputs must let stop remove the current input instead of pre-popping it");
        }
    }

    @Test
    void clearInputsNotifiesObserversEvenWhenAlreadyEmpty() {
        final PlayerControllerHuman controller = newController();
        final InputQueue queue = controller.getInputQueue();
        final AtomicInteger notifications = new AtomicInteger();
        queue.addObserver((observable, argument) -> notifications.incrementAndGet());

        queue.clearInputs();

        assertEquals(1, notifications.get());
        assertNull(queue.getInput());
    }

    @Test
    void clearInputsDrainsAllInputsAndAggregatesStopFailures() throws Exception {
        final PlayerControllerHuman controller = newController();
        final InputQueue queue = controller.getInputQueue();
        final RecordingInput lower = new RecordingInput(controller, false);
        final RecordingInput middle = new RecordingInput(controller, true);
        final IllegalStateException topFailure = new IllegalStateException("top stop failed");
        final AtomicInteger topStopCalls = new AtomicInteger();
        final CountDownLatch topLatch = new CountDownLatch(1);
        final InputSynchronized top = stubbornInput(topFailure, topStopCalls, topLatch);
        queue.setInput(lower);
        queue.setInput(middle);
        queue.setInput(top);
        final AtomicInteger emptyNotifications = new AtomicInteger();
        queue.addObserver((observable, argument) -> {
            if (queue.getInput() == null) {
                emptyNotifications.incrementAndGet();
            }
        });

        final IllegalStateException failure = assertThrows(IllegalStateException.class, queue::clearInputs);

        assertSame(topFailure, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(middle.stopFailure, failure.getSuppressed()[0]);
        assertNull(queue.getInput());
        assertEquals(2, emptyNotifications.get(),
                "the last removal and clearInputs final notification must both publish the empty queue");
        assertEquals(1, topStopCalls.get());
        assertEquals(0, topLatch.getCount());
        for (final RecordingInput input : List.of(middle, lower)) {
            assertEquals(1, input.stopCalls);
            assertEquals(0, latchOf(input).getCount());
        }
    }

    @Test
    void stopFinishesRemovesAndReleasesLatchWhenOnStopThrows() throws Exception {
        final PlayerControllerHuman controller = newController();
        final InputQueue queue = controller.getInputQueue();
        final RecordingInput input = new RecordingInput(controller, true);
        queue.setInput(input);
        queue.deleteObservers();
        GuiBase.setInterface(testGui(true));

        final IllegalStateException failure = assertThrows(IllegalStateException.class, input::stop);

        assertSame(input.stopFailure, failure);
        assertTrue(input.finished());
        assertNull(queue.getInput());
        assertEquals(0, latchOf(input).getCount());
    }

    @Test
    void stopWaitsForEdtFinishBeforeReturningOrReleasingWaiter() throws Exception {
        final PlayerControllerHuman controller = newController();
        final InputQueue queue = controller.getInputQueue();
        final RecordingInput input = new RecordingInput(controller, false);
        queue.setInput(input);
        queue.deleteObservers();
        final ControllableEdtGui edt = new ControllableEdtGui();
        GuiBase.setInterface(edt.gui());
        final ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            final Future<?> waiter = executor.submit(input::awaitLatchRelease);
            assertTrue(input.awaitStarted.await(1, TimeUnit.SECONDS));
            final Future<?> stopper = executor.submit(input::stop);
            final Runnable finishOnEdt = edt.nextTask();

            assertFalse(input.finished());
            assertFalse(stopper.isDone(), "stop must wait for setFinished to complete on the GUI thread");
            assertFalse(waiter.isDone(), "the input waiter must remain blocked until setFinished completes");
            assertEquals(1, latchOf(input).getCount());

            finishOnEdt.run();

            stopper.get(1, TimeUnit.SECONDS);
            waiter.get(1, TimeUnit.SECONDS);
            assertTrue(input.finished());
            assertNull(queue.getInput());
            assertEquals(0, latchOf(input).getCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void stopOnGuiThreadFinishesDirectlyAndStillCapturesFinishFailure() throws Exception {
        final PlayerControllerHuman controller = newController();
        final InputQueue queue = controller.getInputQueue();
        final AwaitingRecordingInput input = new AwaitingRecordingInput(controller);
        queue.setInput(input);
        queue.deleteObservers();
        final IllegalStateException finishFailure = new IllegalStateException("await next input failed");
        controller.setGui((IGuiGame) Proxy.newProxyInstance(
                IGuiGame.class.getClassLoader(),
                new Class<?>[] { IGuiGame.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("awaitNextInput")) {
                        throw finishFailure;
                    }
                    return defaultValue(method.getReturnType());
                }));
        GuiBase.setInterface((IGuiBase) Proxy.newProxyInstance(
                IGuiBase.class.getClassLoader(),
                new Class<?>[] { IGuiBase.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("isGuiThread")) {
                        return true;
                    }
                    if (method.getName().equals("invokeInEdtAndWait")) {
                        throw new AssertionError("GUI-thread stop must not wait on itself");
                    }
                    return defaultValue(method.getReturnType());
                }));

        final IllegalStateException failure = assertThrows(IllegalStateException.class, input::stop);

        assertSame(finishFailure, failure);
        assertTrue(input.finished());
        assertNull(queue.getInput());
        assertEquals(0, latchOf(input).getCount());
    }

    private static PlayerControllerHuman newController() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "input recovery test"));
        final PlayerControllerHuman controller = new PlayerControllerHuman(game, null, null);
        controller.setGui((IGuiGame) Proxy.newProxyInstance(
                IGuiGame.class.getClassLoader(),
                new Class<?>[] { IGuiGame.class },
                (proxy, method, arguments) -> defaultValue(method.getReturnType())));
        return controller;
    }

    private static CountDownLatch latchOf(final InputSyncronizedBase input) throws Exception {
        final Field field = InputSyncronizedBase.class.getDeclaredField("cdlDone");
        field.setAccessible(true);
        return (CountDownLatch) field.get(input);
    }

    private static InputSynchronized stubbornInput(final RuntimeException failure,
                                                    final AtomicInteger stopCalls,
                                                    final CountDownLatch latch) {
        return (InputSynchronized) Proxy.newProxyInstance(
                InputSynchronized.class.getClassLoader(),
                new Class<?>[] { InputSynchronized.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("stop")) {
                        stopCalls.incrementAndGet();
                        throw failure;
                    }
                    if (method.getName().equals("relaseLatchWhenGameIsOver")) {
                        latch.countDown();
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static IGuiBase testGui(final boolean runEdtTasks) {
        return (IGuiBase) Proxy.newProxyInstance(
                IGuiBase.class.getClassLoader(),
                new Class<?>[] { IGuiBase.class },
                (proxy, method, arguments) -> {
                    if (runEdtTasks && method.getName().startsWith("invokeInEdt")
                            && arguments != null && arguments.length > 0 && arguments[0] instanceof Runnable) {
                        ((Runnable) arguments[0]).run();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(final Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class ControllableEdtGui {
        private final BlockingQueue<EdtTask> tasks = new LinkedBlockingQueue<>();

        private IGuiBase gui() {
            return (IGuiBase) Proxy.newProxyInstance(
                    IGuiBase.class.getClassLoader(),
                    new Class<?>[] { IGuiBase.class },
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("invokeInEdtLater")) {
                            tasks.add(new EdtTask((Runnable) arguments[0]));
                            return null;
                        }
                        if (method.getName().equals("invokeInEdtAndWait")) {
                            final EdtTask task = new EdtTask((Runnable) arguments[0]);
                            tasks.add(task);
                            task.awaitCompletion();
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private Runnable nextTask() throws InterruptedException {
            final EdtTask task = tasks.poll(1, TimeUnit.SECONDS);
            assertTrue(task != null, "expected stop to enqueue setFinished on the GUI thread");
            return task;
        }
    }

    private static final class EdtTask implements Runnable {
        private final Runnable delegate;
        private final CountDownLatch completed = new CountDownLatch(1);
        private Throwable failure;

        private EdtTask(final Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } catch (final RuntimeException | Error ex) {
                failure = ex;
            } finally {
                completed.countDown();
            }
        }

        private void awaitCompletion() throws InterruptedException {
            completed.await();
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
        }
    }

    private static class RecordingInput extends InputSyncronizedBase {
        private static final long serialVersionUID = 1L;

        private final boolean throwOnStop;
        private final IllegalStateException stopFailure = new IllegalStateException("onStop failed");
        private final CountDownLatch awaitStarted = new CountDownLatch(1);
        private int stopCalls;
        private boolean wasCurrentWhenStopped;

        private RecordingInput(final PlayerControllerHuman controller, final boolean throwOnStop) {
            super(controller);
            this.throwOnStop = throwOnStop;
        }

        @Override
        protected void showMessage() {
        }

        @Override
        protected void onStop() {
            stopCalls++;
            wasCurrentWhenStopped = getController().getInputQueue().getInput() == this;
            if (throwOnStop) {
                throw stopFailure;
            }
        }

        protected final boolean finished() {
            return isFinished();
        }

        @Override
        public void awaitLatchRelease() {
            awaitStarted.countDown();
            super.awaitLatchRelease();
        }

        @Override
        public boolean selectAbility(final SpellAbility ability) {
            return false;
        }

        @Override
        public String getActivateAction(final Card card) {
            return null;
        }
    }

    private static final class AwaitingRecordingInput extends RecordingInput {
        private static final long serialVersionUID = 1L;

        private AwaitingRecordingInput(final PlayerControllerHuman controller) {
            super(controller, false);
        }

        @Override
        protected boolean allowAwaitNextInput() {
            return true;
        }
    }
}
