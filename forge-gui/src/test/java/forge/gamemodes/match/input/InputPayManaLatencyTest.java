package forge.gamemodes.match.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import forge.ai.ManaPaymentPreview;
import forge.CardStorageReader;
import forge.LobbyPlayer;
import forge.StaticData;
import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.cost.Cost;
import forge.game.mana.Mana;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.AbilityActivated;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.player.LobbyPlayerHuman;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class InputPayManaLatencyTest {
    private IGuiBase previousGuiBase;
    private Thread uiThread;
    private BlockingQueue<Runnable> uiTasks;
    private AtomicBoolean mobileBase;
    private AtomicReference<RuntimeException> nextUiDispatchFailure;

    @BeforeAll
    static void initializeLocalizer() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res",
                "languages").toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
        initializeStaticData();
    }

    private static void initializeStaticData() {
        synchronized (StaticData.class) {
            if (StaticData.instance() != null) {
                return;
            }
            new StaticData(new CardStorageReader(
                    workspacePath("forge-gui", "res", "cardsfolder"),
                    null, true), null,
                    workspacePath("forge-gui", "res", "editions"),
                    workspacePath("custom", "editions"),
                    workspacePath("forge-gui", "res", "blockdata"),
                    "Latest", true, true);
        }
    }

    private static String workspacePath(final String first,
            final String... more) {
        Path path = Paths.get(first, more).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            path = Paths.get("..").resolve(Paths.get(first, more))
                    .toAbsolutePath().normalize();
        }
        return path.toString();
    }

    @BeforeEach
    void installDeterministicUiDispatcher() {
        previousGuiBase = GuiBase.getInterface();
        uiThread = Thread.currentThread();
        uiTasks = new LinkedBlockingQueue<>();
        mobileBase = new AtomicBoolean();
        nextUiDispatchFailure = new AtomicReference<>();
        GuiBase.setInterface((IGuiBase) Proxy.newProxyInstance(
                IGuiBase.class.getClassLoader(),
                new Class<?>[] { IGuiBase.class },
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                    case "isGuiThread":
                        return Thread.currentThread() == uiThread;
                    case "isLibgdxPort":
                        return mobileBase.get();
                    case "invokeInEdtNow":
                        ((Runnable) arguments[0]).run();
                        return null;
                    case "invokeInEdtLater":
                        final RuntimeException dispatchFailure =
                                nextUiDispatchFailure.getAndSet(null);
                        if (dispatchFailure != null) {
                            throw dispatchFailure;
                        }
                        uiTasks.add((Runnable) arguments[0]);
                        return null;
                    case "invokeInEdtAndWait":
                        if (Thread.currentThread() == uiThread) {
                            ((Runnable) arguments[0]).run();
                            return null;
                        }
                        final CountDownLatch finished = new CountDownLatch(1);
                        uiTasks.add(() -> {
                            try {
                                ((Runnable) arguments[0]).run();
                            } finally {
                                finished.countDown();
                            }
                        });
                        if (!finished.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("EDT wait was not drained");
                        }
                        return null;
                    default:
                        return defaultValue(method.getReturnType());
                    }
                }));
    }

    @AfterEach
    void restoreGuiDispatcher() {
        GuiBase.setInterface(previousGuiBase);
    }

    @Test
    void blockedAdvisoryNeverBlocksUiAndCancelWaitsForActorBarrier()
            throws Exception {
        final Fixture fixture = fixture();
        final CountDownLatch previewStarted = new CountDownLatch(1);
        final CountDownLatch releasePreview = new CountDownLatch(1);
        final HarnessInput input = fixture.input("1");
        input.previewBlock = () -> {
            previewStarted.countDown();
            await(releasePreview);
        };

        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);
        assertTrue(fixture.ui.lastAutoEnabled.get(),
                "advisory status must not authorize or disable Auto");
        assertTrue(fixture.ui.lastCancelEnabled.get());
        assertTrue(previewStarted.await(1, TimeUnit.SECONDS));

        final long beforeCancel = System.nanoTime();
        input.selectButtonCancel();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - beforeCancel) < 100,
                "the UI only enqueues Cancel");
        assertFalse(running.future.isDone(),
                "showAndWait must not return while accepted preview work mutates/reads");

        releasePreview.countDown();
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);
        assertTrue(input.finished());
        assertNull(fixture.controller.getInputQueue().getInput());
        assertEquals(0, fixture.ui.previewPublications.get(),
                "late advisory publication is invalid after Cancel");
        running.close();
    }

    @Test
    void showAndWaitCalledFromGuiThreadFailsFastWithoutMutation()
            throws Exception {
        final Fixture fixture = fixture();
        final HarnessInput input = fixture.input("1");
        GuiBase.setInterface((IGuiBase) Proxy.newProxyInstance(
                IGuiBase.class.getClassLoader(),
                new Class<?>[] { IGuiBase.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("isGuiThread")) {
                        return true;
                    }
                    if (method.getName().startsWith("invokeInEdt")
                            && arguments != null && arguments.length > 0
                            && arguments[0] instanceof Runnable) {
                        ((Runnable) arguments[0]).run();
                    }
                    return defaultValue(method.getReturnType());
                }));
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<?> future = executor.submit(input::showAndWait);

        try {
            final java.util.concurrent.ExecutionException failure =
                    assertThrows(java.util.concurrent.ExecutionException.class,
                            () -> future.get(500, TimeUnit.MILLISECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertNull(fixture.controller.getInputQueue().getInput());
            assertNull(input.saPaidFor.getManaCostBeingPaid());
            assertNull(fixture.player.getPaidForSA());
        } finally {
            if (!future.isDone()) {
                fixture.game.setGameOver(GameEndReason.Draw);
            }
            executor.shutdownNow();
        }
    }

    @Test
    void manualPoolPaymentRunsOnActorAndDrainsBeforeReturn()
            throws Exception {
        final Fixture fixture = fixture();
        final HarnessInput input = fixture.input("1");
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);

        final Card source = fixture.card(700);
        fixture.player.getZone(ZoneType.Battlefield).add(source);
        fixture.player.getManaPool().addMana(new Mana(MagicColor.RED,
                source, null, fixture.player));
        input.useManaFromPool(MagicColor.RED);

        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);
        assertEquals("0", input.remainingCost());
        assertTrue(input.isPaid());
        assertTrue(input.finished());
        assertTrue(fixture.player.getManaPool().isEmpty());
        running.close();
    }

    @Test
    void blockedPreviewQueuesEveryPoolCardAndLifeActionInFifoOrder()
            throws Exception {
        final Fixture fixture = fixture();
        final CountDownLatch previewStarted = new CountDownLatch(1);
        final CountDownLatch releasePreview = new CountDownLatch(1);
        final HarnessInput input = fixture.input("2 B/P");
        input.previewBlock = () -> {
            previewStarted.countDown();
            await(releasePreview);
        };
        final Card floatingSource = fixture.card(710);
        fixture.player.getZone(ZoneType.Battlefield).add(floatingSource);
        final Card activatedSource = fixture.manaCard(711);

        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);
        fixture.player.getManaPool().addMana(new Mana(MagicColor.RED,
                floatingSource, null, fixture.player));
        assertTrue(previewStarted.await(1, TimeUnit.SECONDS));

        input.useManaFromPool(MagicColor.RED);
        assertTrue(input.selectCard(activatedSource, null, null));
        input.selectPlayer(fixture.player, null);
        assertFalse(activatedSource.isTapped());
        assertEquals(20, fixture.player.getLife());

        releasePreview.countDown();
        try {
            drainUntil(running.future::isDone);
        } catch (final AssertionError failure) {
            throw new AssertionError("manual FIFO state: cost="
                    + input.remainingCost() + ", tapped="
                    + activatedSource.isTapped() + ", life="
                    + fixture.player.getLife() + ", pending="
                    + input.isActivatingManaAbility(), failure);
        }
        running.future.get(1, TimeUnit.SECONDS);
        assertTrue(activatedSource.isTapped());
        assertEquals(18, fixture.player.getLife());
        assertTrue(input.isPaid());
        running.close();
    }

    @Test
    void advisoryFailureDoesNotDisableExactAutoOrCancel() throws Exception {
        final Fixture fixture = fixture();
        final HarnessInput input = fixture.input("1");
        input.previewFailure = new IllegalStateException("preview failed");
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);
        drainAvailableUi();

        assertTrue(fixture.ui.lastAutoEnabled.get());
        assertFalse(input.finished());
        input.selectButtonOK();
        waitFor(() -> input.actualAutoCalls.get() == 1);
        assertEquals(1, input.actualAutoCalls.get(),
                "Auto must run the exact current-state pass");

        input.selectButtonCancel();
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);
        running.close();
    }

    @Test
    void hoverUsesPublishedIdsAndDoesNotProbeAbilitiesOnUiThread()
            throws Exception {
        final Fixture fixture = fixture();
        fixture.controller.getYieldController().setPref(
                FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, "true");
        final Card source = fixture.manaCard(800);
        final HarnessInput input = fixture.input("1");
        input.manaAbilityThread.set(null);
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.previewPublications.get() == 1);

        assertEquals("Game-input-test", input.manaAbilityThread.get());
        input.manaAbilityThread.set(null);
        assertNotNull(input.getActivateAction(source));
        assertNull(input.manaAbilityThread.get(),
                "hover reads only the immutable identity snapshot");

        input.selectButtonCancel();
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);
        running.close();
    }

    @Test
    void realProxyHoverAndSelectOnTwentyThousandCardsNeverResolvesLiveCards()
            throws Exception {
        final Fixture fixture = fixture();
        fixture.controller.getYieldController().setPref(
                FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, "true");
        final List<CardView> views = new ArrayList<>(20_000);
        for (int i = 0; i < 19_999; i++) {
            final Card filler = fixture.card(20_000 + i);
            filler.addStaticAbility(
                    "Mode$ IgnoreLegendRule | EffectZone$ All");
            fixture.player.getZone(ZoneType.Battlefield).add(filler);
            views.add(filler.getView());
        }
        final Card source = fixture.manaCard(50_000);
        views.add(source.getView());
        final HarnessInput input = fixture.input("1");
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.previewPublications.get() == 1);
        fixture.controller.getInputProxy().update(null, null);
        drainAvailableUi();
        fixture.controller.rejectLiveCardResolution.set(true);

        final long started = System.nanoTime();
        for (int i = 0; i < 20_000; i++) {
            assertNotNull(fixture.controller.getInputProxy()
                    .getActivateAction(source.getView()));
        }
        assertTrue(fixture.controller.getInputProxy().selectCard(
                source.getView(), views, null));
        fixture.controller.getInputProxy().selectPlayer(null, null);
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started);

        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);
        assertEquals(0, fixture.controller.liveCardResolutions.get(),
                "payment hover/select must not call getCard/findByView");
        assertTrue(elapsedMs < 5_000,
                "20k real InputProxy ID reads took " + elapsedMs + "ms");
        running.close();
    }

    @Test
    void desktopMultipleManaAbilitiesUseTheExistingChooserForGenericMana()
            throws Exception {
        final Fixture fixture = fixture();
        fixture.ui.abilityChoiceIndex = 1;
        final Card source = fixture.multiManaCard(51_000,
                MagicColor.RED, MagicColor.BLUE);
        final HarnessInput input = fixture.input("1");
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);

        assertTrue(input.selectCard(source, null, null));
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);

        assertEquals(1, fixture.ui.abilityChooserCalls.get());
        assertEquals(1, input.saPaidFor.getPayingMana().size());
        assertEquals(MagicColor.BLUE,
                input.saPaidFor.getPayingMana().get(0).getColor());
        running.close();
    }

    @Test
    void mobileDelayedSelectionReResolvesIdentityAndPays() throws Exception {
        final Fixture fixture = fixture();
        fixture.ui.mobile = true;
        mobileBase.set(true);
        final Card coveredTapped = fixture.manaCard(900);
        coveredTapped.setTapped(true);
        final Card delayed = fixture.manaCard(901);
        final HarnessInput input = fixture.input("1");
        input.autoSupported = false;
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);

        assertTrue(input.selectCard(coveredTapped, List.of(delayed), null));
        try {
            drainUntil(running.future::isDone);
        } catch (final AssertionError failure) {
            throw new AssertionError("mobile state: cost="
                    + input.remainingCost() + ", delayedTapped="
                    + delayed.isTapped() + ", pending="
                    + input.isActivatingManaAbility(), failure);
        }
        running.future.get(1, TimeUnit.SECONDS);
        assertTrue(delayed.isTapped());
        assertTrue(input.isPaid());
        running.close();
    }

    @Test
    void realNestedManaInputRecursivelyPumpsWithoutDeadlock()
            throws Exception {
        final Fixture fixture = fixture();
        final HarnessInput outer = fixture.input("1");
        final AtomicReference<HarnessInput> nested = new AtomicReference<>();
        outer.autoOverride = () -> {
            final HarnessInput inner = fixture.input("1");
            nested.set(inner);
            inner.showAndWait();
        };
        final RunningInput running = start(outer);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);
        outer.selectButtonOK();
        waitFor(() -> nested.get() != null);
        drainUntil(() -> fixture.ui.promptCalls.get() >= 2);

        nested.get().selectButtonCancel();
        drainUntil(() -> fixture.controller.getInputQueue().getInput() == outer);
        outer.selectButtonCancel();
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);
        running.close();
    }

    @Test
    void extraManaMatrixIsAppliedByActorBeforePrompt() throws Exception {
        final Fixture fixture = fixture();
        final ManaConversionMatrix matrix = new ManaConversionMatrix();
        matrix.restoreColorReplacements();
        matrix.adjustColorReplacement(MagicColor.RED, MagicColor.BLUE, true);
        final AbilityActivated paidFor = fixture.paidFor(fixture.card(1000));
        final InputPayManaOfCostPayment input =
                new InputPayManaOfCostPayment(fixture.controller,
                        new ManaCostBeingPaid(new ManaCost("U")), paidFor,
                        fixture.player, matrix, false);
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);
        assertTrue((fixture.player.getManaPool()
                .getPossibleColorUses(MagicColor.RED) & MagicColor.BLUE) != 0);

        input.selectButtonCancel();
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);
        running.close();
    }

    @Test
    void extraManaMatrixIsReappliedBetweenConsecutiveRealManaAbilities()
            throws Exception {
        final Fixture fixture = fixture();
        final ManaConversionMatrix matrix = new ManaConversionMatrix();
        matrix.restoreColorReplacements();
        matrix.adjustColorReplacement(MagicColor.RED, MagicColor.BLUE, true);
        final Card first = fixture.manaCard(52_000, MagicColor.RED, true);
        final Card second = fixture.manaCard(52_001, MagicColor.RED);
        final AbilityActivated paidFor = fixture.paidFor(fixture.card(52_002));
        final InputPayManaOfCostPayment input =
                new InputPayManaOfCostPayment(fixture.controller,
                        new ManaCostBeingPaid(new ManaCost("U U")), paidFor,
                        fixture.player, matrix, false);
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);

        assertTrue(input.selectCard(first, null, null));
        waitFor(first::isTapped);
        drainUntil(() -> fixture.ui.promptCalls.get() >= 2);
        assertTrue((fixture.player.getManaPool()
                .getPossibleColorUses(MagicColor.RED) & MagicColor.BLUE) != 0,
                "refresh must restore the outer matrix after the inner "
                        + "ManaConversion path reset it");
        assertTrue(input.selectCard(second, null, null));
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);

        assertTrue(first.isTapped());
        assertTrue(second.isTapped());
        assertTrue(input.isPaid());
        assertEquals(2, paidFor.getPayingMana().size());
        assertEquals(0, fixture.player.getManaPool()
                .getPossibleColorUses(MagicColor.RED) & MagicColor.BLUE,
                "the temporary outer payment matrix must not leak");
        running.close();
    }

    @Test
    void terminationClearsCountersForDiscardedManualAndAutoClosures()
            throws Exception {
        final Fixture fixture = fixture();
        final CountDownLatch previewStarted = new CountDownLatch(1);
        final CountDownLatch releasePreview = new CountDownLatch(1);
        final HarnessInput input = fixture.input("2");
        input.previewBlock = () -> {
            previewStarted.countDown();
            await(releasePreview);
        };
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);
        assertTrue(previewStarted.await(1, TimeUnit.SECONDS));

        input.useManaFromPool(MagicColor.RED);
        input.selectButtonOK();
        assertTrue(input.isActivatingManaAbility());
        fixture.game.setGameOver(GameEndReason.Draw);
        releasePreview.countDown();
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);

        assertFalse(input.isActivatingManaAbility());
        assertFalse(atomicBooleanField(input, "autoActionPending").get());
        assertTrue(dequeField(input, "delayedCards").isEmpty());
        assertNull(fixture.controller.getInputQueue().getInput());
        running.close();
    }

    @Test
    void gameOverWindowCannotCreateAPostTerminationPaymentScope()
            throws Exception {
        final TerminatingFixture fixture = terminatingFixture();
        final HarnessInput input = fixture.fixture.input("1");
        final Thread terminator = new Thread(
                () -> fixture.game.setGameOver(GameEndReason.Draw));
        terminator.start();
        assertTrue(fixture.game.terminationWindow.await(
                1, TimeUnit.SECONDS));
        final CountDownLatch showInvoked = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "Game-over-race-input"));
        final Future<?> future = executor.submit(() -> {
            showInvoked.countDown();
            input.showAndWait();
        });
        assertTrue(showInvoked.await(1, TimeUnit.SECONDS));

        try {
            Thread.sleep(100);
            assertNull(fixture.fixture.controller.getInputQueue().getInput());
            assertNull(input.saPaidFor.getManaCostBeingPaid());
            assertNull(fixture.fixture.player.getPaidForSA());
        } finally {
            fixture.game.finishGameOver.countDown();
            terminator.join(2_000);
            drainUntil(future::isDone);
            future.get(1, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void rawScopeReleaseStillPerformsFullCleanupBeforeGameThreadReturns()
            throws Exception {
        final Fixture fixture = fixture();
        final HarnessInput input = fixture.input("1");
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);

        releaseRawLatch(input);
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);

        assertTrue(input.finished());
        assertNull(fixture.controller.getInputQueue().getInput());
        assertNull(input.saPaidFor.getManaCostBeingPaid());
        assertNull(fixture.player.getPaidForSA());
        running.close();
    }

    @Test
    void setupFailureStillRunsFullActorCleanup() throws Exception {
        final Fixture fixture = fixture();
        final HarnessInput input = fixture.input("1");
        final IllegalStateException setupFailure =
                new IllegalStateException("setInput observer failed");
        fixture.controller.getInputQueue().addObserver(
                (observable, argument) -> {
                    throw setupFailure;
                });
        final RunningInput running = start(input);

        drainUntil(running.future::isDone);
        assertSame(setupFailure, assertThrows(java.util.concurrent.ExecutionException.class,
                () -> running.future.get(1, TimeUnit.SECONDS)).getCause());
        assertNull(fixture.controller.getInputQueue().getInput());
        assertNull(input.saPaidFor.getManaCostBeingPaid());
        assertNull(fixture.player.getPaidForSA());
        running.close();
    }

    @Test
    void promptPublicationFailureStillRunsFullActorCleanup() throws Exception {
        final Fixture fixture = fixture();
        final HarnessInput input = fixture.input("1");
        nextUiDispatchFailure.set(
                new IllegalStateException("prompt dispatch failed"));
        final RunningInput running = start(input);

        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);
        assertNull(fixture.controller.getInputQueue().getInput());
        assertNull(input.saPaidFor.getManaCostBeingPaid());
        assertNull(fixture.player.getPaidForSA());
        running.close();
    }

    @Test
    void cleanupUiDispatchFailureCannotSkipGameStateCleanup() throws Exception {
        final Fixture fixture = fixture();
        final ManaConversionMatrix matrix = new ManaConversionMatrix();
        matrix.restoreColorReplacements();
        matrix.adjustColorReplacement(MagicColor.RED, MagicColor.BLUE, true);
        final AbilityActivated paidFor = fixture.paidFor(fixture.card(53_000));
        final InputPayManaOfCostPayment input =
                new InputPayManaOfCostPayment(fixture.controller,
                        new ManaCostBeingPaid(new ManaCost("U")), paidFor,
                        fixture.player, matrix, false);
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);
        nextUiDispatchFailure.set(
                new IllegalStateException("cleanup dispatch failed"));

        input.selectButtonCancel();
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);

        assertNull(fixture.controller.getInputQueue().getInput());
        assertNull(paidFor.getManaCostBeingPaid());
        assertNull(fixture.player.getPaidForSA());
        assertEquals(0, fixture.player.getManaPool()
                .getPossibleColorUses(MagicColor.RED) & MagicColor.BLUE);
        running.close();
    }

    @Test
    void realGameOverTerminatesScopeAndRunsRegisteredCleanup()
            throws Exception {
        final Fixture fixture = fixture();
        final HarnessInput input = fixture.input("1");
        input.autoOverride = () -> fixture.game
                .setGameOver(GameEndReason.Draw);
        final RunningInput running = start(input);
        drainUntil(() -> fixture.ui.promptCalls.get() == 1);
        input.selectButtonOK();
        drainUntil(running.future::isDone);
        running.future.get(1, TimeUnit.SECONDS);
        assertTrue(fixture.game.isGameOver());
        assertTrue(input.finished());
        assertNull(fixture.controller.getInputQueue().getInput());
        running.close();
    }

    private Fixture fixture() {
        final RegisteredPlayer registered = new RegisteredPlayer(
                new Deck("Input lane test"))
                .setPlayer(new LobbyPlayerHuman("Human"));
        final List<RegisteredPlayer> players = List.of(registered);
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(players, rules,
                new Match(rules, players, "Input lane test"));
        final Player player = game.getPlayers().get(0);
        player.setLife(20, null);
        final CountingController controller = new CountingController(game,
                player, player.getOriginalLobbyPlayer());
        player.dangerouslySetController(controller);
        final UiRecorder ui = new UiRecorder(uiThread);
        controller.setGui(ui.proxy());
        controller.getYieldController().setPref(
                FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, "false");
        controller.getYieldController().setPref(
                FPref.UI_SHOW_AUTOTAP_PREVIEW, "false");
        controller.getInputQueue().deleteObservers();
        return new Fixture(game, player, controller, ui);
    }

    private TerminatingFixture terminatingFixture() {
        final RegisteredPlayer registered = new RegisteredPlayer(
                new Deck("Game over race test"))
                .setPlayer(new LobbyPlayerHuman("Human"));
        final List<RegisteredPlayer> players = List.of(registered);
        final GameRules rules = new GameRules(GameType.Constructed);
        final TerminationWindowGame game = new TerminationWindowGame(players,
                rules, new Match(rules, players, "Game over race test"));
        final Player player = game.getPlayers().get(0);
        player.setLife(20, null);
        final CountingController controller = new CountingController(game,
                player, player.getOriginalLobbyPlayer());
        player.dangerouslySetController(controller);
        final UiRecorder ui = new UiRecorder(uiThread);
        controller.setGui(ui.proxy());
        controller.getYieldController().setPref(
                FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, "false");
        controller.getYieldController().setPref(
                FPref.UI_SHOW_AUTOTAP_PREVIEW, "false");
        controller.getInputQueue().deleteObservers();
        return new TerminatingFixture(game,
                new Fixture(game, player, controller, ui));
    }

    private RunningInput start(final InputPayMana input) {
        final ExecutorService executor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "Game-input-test"));
        return new RunningInput(executor,
                executor.submit(input::showAndWait));
    }

    private void drainUntil(final Condition condition) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.get() && System.nanoTime() < deadline) {
            final Runnable task = uiTasks.poll(20, TimeUnit.MILLISECONDS);
            if (task != null) {
                task.run();
            }
        }
        assertTrue(condition.get(), "timed out draining UI tasks");
    }

    private void drainAvailableUi() {
        Runnable task;
        while ((task = uiTasks.poll()) != null) {
            task.run();
        }
    }

    private static void waitFor(final Condition condition) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.get() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(condition.get(), "timed out waiting for actor state");
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for latch");
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static void releaseRawLatch(final InputPayMana input)
            throws ReflectiveOperationException {
        final Field field = InputSyncronizedBase.class
                .getDeclaredField("cdlDone");
        field.setAccessible(true);
        ((CountDownLatch) field.get(input)).countDown();
    }

    private static AtomicBoolean atomicBooleanField(final InputPayMana input,
            final String name) throws ReflectiveOperationException {
        final Field field = InputPayMana.class.getDeclaredField(name);
        field.setAccessible(true);
        return (AtomicBoolean) field.get(input);
    }

    private static java.util.Deque<?> dequeField(final InputPayMana input,
            final String name) throws ReflectiveOperationException {
        final Field field = InputPayMana.class.getDeclaredField(name);
        field.setAccessible(true);
        return (java.util.Deque<?>) field.get(input);
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    @FunctionalInterface
    private interface Condition {
        boolean get();
    }

    private static final class HarnessInput extends InputPayMana {
        private static final long serialVersionUID = 1L;
        private final AtomicInteger previewCalls = new AtomicInteger();
        private final AtomicInteger actualAutoCalls = new AtomicInteger();
        private final AtomicReference<String> manaAbilityThread =
                new AtomicReference<>();
        private Runnable previewBlock;
        private Runnable autoOverride;
        private RuntimeException previewFailure;
        private boolean autoSupported = true;

        private HarnessInput(final PlayerControllerHuman controller,
                final SpellAbility paidFor, final Player player,
                final String cost) {
            super(controller, paidFor, player, false);
            manaCost = new ManaCostBeingPaid(new ManaCost(cost));
        }

        @Override
        protected void initializeInputStateOnLane() {
            super.initializeInputStateOnLane();
            player.pushPaidForSA(saPaidFor);
            saPaidFor.setManaCostBeingPaid(manaCost);
            markPaidForStatePushed();
        }

        @Override
        protected List<SpellAbility> getAllManaAbilities(final Card card) {
            manaAbilityThread.set(Thread.currentThread().getName());
            return super.getAllManaAbilities(card);
        }

        @Override
        protected ManaPaymentPreview.Result computeAdvisoryPreview(
                final ManaCostBeingPaid cost,
                final Iterable<Card> explicitCandidates) {
            previewCalls.incrementAndGet();
            if (previewBlock != null) {
                previewBlock.run();
            }
            if (previewFailure != null) {
                throw previewFailure;
            }
            return super.computeAdvisoryPreview(cost, explicitCandidates);
        }

        @Override
        protected void payManaAutomaticallyOnLane() {
            actualAutoCalls.incrementAndGet();
            if (autoOverride != null) {
                autoOverride.run();
            }
        }

        @Override
        protected boolean supportAutoPay() {
            return autoSupported;
        }

        @Override
        protected void onPlayerSelectedOnLane(final Player selected) {
            if (selected == player && player.canPayLife(phyLifeToLose + 2,
                    effect, saPaidFor) && manaCost.payPhyrexian()) {
                phyLifeToLose += 2;
                saPaidFor.setSpendPhyrexianMana(true);
            }
        }

        @Override
        protected void done() {
            if (phyLifeToLose > 0) {
                player.payLife(phyLifeToLose, saPaidFor, effect);
            }
        }

        @Override
        protected String getMessage() {
            return "Pay test mana " + manaCost.toString();
        }

        private String remainingCost() {
            return manaCost.toString();
        }

        private boolean finished() {
            return isFinished();
        }
    }

    private static final class UiRecorder {
        private final Thread expectedThread;
        private final AtomicInteger promptCalls = new AtomicInteger();
        private final AtomicInteger previewPublications = new AtomicInteger();
        private final AtomicBoolean lastAutoEnabled = new AtomicBoolean();
        private final AtomicBoolean lastCancelEnabled = new AtomicBoolean();
        private final AtomicInteger abilityChooserCalls = new AtomicInteger();
        private int abilityChoiceIndex;
        private boolean mobile;

        private UiRecorder(final Thread expectedThread) {
            this.expectedThread = expectedThread;
        }

        private IGuiGame proxy() {
            return (IGuiGame) Proxy.newProxyInstance(
                    IGuiGame.class.getClassLoader(),
                    new Class<?>[] { IGuiGame.class },
                    (proxy, method, arguments) -> {
                        switch (method.getName()) {
                        case "updateButtons":
                            assertSame(expectedThread, Thread.currentThread());
                            lastAutoEnabled.set((Boolean) arguments[3]);
                            lastCancelEnabled.set((Boolean) arguments[4]);
                            break;
                        case "showPromptMessage":
                            assertSame(expectedThread, Thread.currentThread());
                            promptCalls.incrementAndGet();
                            break;
                        case "setWeaklySelectable":
                            assertSame(expectedThread, Thread.currentThread());
                            previewPublications.incrementAndGet();
                            break;
                        case "isLibgdxPort":
                            return mobile;
                        case "getAbilityToPlay":
                            abilityChooserCalls.incrementAndGet();
                            final List<?> abilities = (List<?>) arguments[1];
                            return abilities.isEmpty() ? null
                                    : abilities.get(Math.min(abilityChoiceIndex,
                                            abilities.size() - 1));
                        default:
                            break;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static final class RunningInput implements AutoCloseable {
        private final ExecutorService executor;
        private final Future<?> future;

        private RunningInput(final ExecutorService executor,
                final Future<?> future) {
            this.executor = executor;
            this.future = future;
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }

    private final class Fixture {
        private final Game game;
        private final Player player;
        private final CountingController controller;
        private final UiRecorder ui;
        private int nextCardId = 1;

        private Fixture(final Game game, final Player player,
                final CountingController controller,
                final UiRecorder ui) {
            this.game = game;
            this.player = player;
            this.controller = controller;
            this.ui = ui;
        }

        private HarnessInput input(final String cost) {
            final Card host = card(nextCardId++);
            return new HarnessInput(controller, paidFor(host), player, cost);
        }

        private AbilityActivated paidFor(final Card host) {
            final AbilityActivated ability = new AbilityActivated(host,
                    Cost.Zero, null) {
                private static final long serialVersionUID = 1L;

                @Override
                public void resolve() {
                }
            };
            ability.setActivatingPlayer(player);
            return ability;
        }

        private Card card(final int id) {
            final Card card = new Card(id, game);
            card.setName("Input Lane Test " + id);
            card.setOwner(player);
            card.setController(player, 0L);
            return card;
        }

        private Card manaCard(final int id) {
            return manaCard(id, MagicColor.GREEN);
        }

        private Card manaCard(final int id, final byte color) {
            return manaCard(id, color, false);
        }

        private Card manaCard(final int id, final byte color,
                final boolean resetsPaymentMatrix) {
            final Card card = card(id);
            card.setSickness(false);
            card.addSpellAbility(AbilityFactory.getAbility(
                    "AB$ Mana | Cost$ T | Produced$ "
                            + MagicColor.toShortString(color)
                            + " | Amount$ 1"
                            + (resetsPaymentMatrix
                                    ? " | ManaConversion$ AnyType->AnyColor"
                                    : ""), card));
            player.getZone(ZoneType.Battlefield).add(card);
            return card;
        }

        private Card multiManaCard(final int id, final byte first,
                final byte second) {
            final Card card = card(id);
            card.setSickness(false);
            for (final byte color : new byte[] { first, second }) {
                card.addSpellAbility(AbilityFactory.getAbility(
                        "AB$ Mana | Cost$ T | Produced$ "
                                + MagicColor.toShortString(color)
                                + " | Amount$ 1", card));
            }
            player.getZone(ZoneType.Battlefield).add(card);
            return card;
        }
    }

    private static final class CountingController
            extends PlayerControllerHuman {
        private final AtomicInteger liveCardResolutions = new AtomicInteger();
        private final AtomicBoolean rejectLiveCardResolution =
                new AtomicBoolean();

        private CountingController(final Game game, final Player player,
                final LobbyPlayer lobbyPlayer) {
            super(game, player, lobbyPlayer);
        }

        @Override
        public Card getCard(final CardView cardView) {
            liveCardResolutions.incrementAndGet();
            if (rejectLiveCardResolution.get()) {
                throw new AssertionError(
                        "InputProxy performed a live card/zone resolution");
            }
            return super.getCard(cardView);
        }
    }

    private static final class TerminatingFixture {
        private final TerminationWindowGame game;
        private final Fixture fixture;

        private TerminatingFixture(final TerminationWindowGame game,
                final Fixture fixture) {
            this.game = game;
            this.fixture = fixture;
        }
    }

    private static final class TerminationWindowGame extends Game {
        private final CountDownLatch terminationWindow = new CountDownLatch(1);
        private final CountDownLatch finishGameOver = new CountDownLatch(1);

        private TerminationWindowGame(
                final List<RegisteredPlayer> registeredPlayers,
                final GameRules rules, final Match match) {
            super(registeredPlayers, rules, match);
        }

        @Override
        public synchronized void setGameOver(final GameEndReason reason) {
            getAction().terminateInputActions();
            terminationWindow.countDown();
            await(finishGameOver);
            super.setGameOver(reason);
        }
    }
}
