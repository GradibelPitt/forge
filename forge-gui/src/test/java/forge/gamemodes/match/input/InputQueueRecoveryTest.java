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
import java.util.concurrent.CountDownLatch;
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

    private static final class RecordingInput extends InputSyncronizedBase {
        private static final long serialVersionUID = 1L;

        private final boolean throwOnStop;
        private final IllegalStateException stopFailure = new IllegalStateException("onStop failed");
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

        private boolean finished() {
            return isFinished();
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
}
