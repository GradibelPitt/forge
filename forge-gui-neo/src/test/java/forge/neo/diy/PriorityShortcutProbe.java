package forge.neo;

import forge.gamemodes.match.PriorityPass.Mode;
import forge.gamemodes.match.YieldUpdate;
import forge.game.phase.PhaseType;
import forge.game.player.RegisteredPlayer;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.neo.NeoShortcuts.Action;
import forge.player.LobbyPlayerHuman;
import forge.player.PlayerControllerHuman;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;

public final class PriorityShortcutProbe {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String app = System.getProperty("forge.test.app") + "/";
        GuiBase.setInterface((IGuiBase) Proxy.newProxyInstance(IGuiBase.class.getClassLoader(),
                new Class[]{IGuiBase.class}, (p, m, a) -> {
                    if (m.getName().equals("getAssetsDir")) return app;
                    if (m.getName().equals("isRunningOnDesktop") || m.getName().equals("isGuiThread")) return true;
                    if (m.getName().equals("invokeInEdtLater") || m.getName().equals("invokeInEdtAndWait")) {
                        ((Runnable) a[0]).run(); return null;
                    }
                    return defaultValue(m.getReturnType());
                }));
        GuiBase.setUsingAppDirectory(true);
        forge.util.Localizer.getInstance().initialize("en-US", app + "res/languages/");
        forge.util.Lang.createInstance("en-US");
        forge.model.FModel.loadDynamicGamedata();
        keys();
        engine();
        scene();
        System.out.println("PRIORITY_SHORTCUT_INTEGRATION_OK");
        javafx.application.Platform.exit();
        System.exit(0);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 1f;
        if (type == double.class) return 1d;
        return null;
    }

    private static void keys() {
        var saved = new HashMap<String, String>();
        saved.put("shortcut.passPriority", "Space");
        saved.put("shortcut.passTurn", "Ctrl+E");
        saved.put("shortcut.zoomCard", "2|Z");
        saved.put("shortcut.undo", "Ctrl+U");
        NeoShortcuts.useStore(new NeoShortcuts.Store() {
            public String get(String key) { return saved.get(key); }
            public void set(String key, String value) { saved.put(key, value); }
            public void save() { }
        });
        var prefs = forge.model.FModel.getPreferences();
        var defaults = new forge.localinstance.properties.ForgePreferences();
        defaults.reset();
        check(defaults.getPrefBoolean(FPref.UI_PRIORITY_SHORTCUTS), "Shared shortcuts must default to enabled");
        prefs.setPref(FPref.UI_PRIORITY_SHORTCUTS, true);
        var original = new HashMap<>(saved);
        KeyCode[] digits = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT6, KeyCode.DIGIT0,
                KeyCode.NUMPAD1, KeyCode.NUMPAD2, KeyCode.NUMPAD6, KeyCode.NUMPAD0};
        Action[] actions = {Action.PASS_PRIORITY, Action.PASS_UNTIL_RESPONSE, Action.PASS_TURN, Action.PASS_END_STEP};
        for (int i = 0; i < digits.length; i++) {
            check(NeoShortcuts.actionFor(key(digits[i], false)) == actions[i % 4], "Wrong key: " + digits[i]);
            check(NeoShortcuts.actionFor(key(digits[i], true)) != actions[i % 4], "Modified digit hijacked");
        }
        check(NeoShortcuts.actionFor(key(KeyCode.SPACE, false)) == null, "Old Space pass remains");
        check(saved.get("shortcut.undo").equals("Ctrl+U"), "Unrelated shortcut changed");
        check(original.equals(saved), "Reading shared shortcuts rewrote UI-specific settings");
        prefs.setPref(FPref.UI_PRIORITY_SHORTCUTS, false);
        check(NeoShortcuts.actionFor(key(KeyCode.SPACE, false)) == Action.PASS_PRIORITY, "Disabling global override did not restore prior bindings");
        check(NeoShortcuts.actionFor(key(KeyCode.DIGIT2, false)) == Action.ZOOM_CARD, "Disabling global override lost an existing binding");
        NeoShortcuts.applyPreset(NeoShortcuts.Preset.ARENA);
        check(!forge.gamemodes.match.PriorityShortcuts.isEnabled(), "A UI preset reset the global setting");
        prefs.setPref(FPref.UI_PRIORITY_SHORTCUTS, true);
        NeoShortcuts.applyPreset(NeoShortcuts.Preset.NEOFORGE);
        saved.clear();
        for (int i = 0; i < digits.length; i++) {
            check(NeoShortcuts.actionFor(key(digits[i], false)) == actions[i % 4], "Changing UI preferences swallowed a shared binding");
        }
        check(forge.gamemodes.match.PriorityShortcuts.isEnabled(), "UI reset disabled the global setting");
        System.out.println("GLOBAL_PRIORITY_SETTING_OK");
        System.out.println("PRIORITY_KEY_BINDINGS_OK");
    }

    private static KeyEvent key(KeyCode code, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false);
    }

    private static void scene() throws Exception {
        var done = new java.util.concurrent.CompletableFuture<Void>();
        javafx.application.Platform.startup(() -> {
            forge.neo.match.NeoMatchUI ui = null;
            try {
                var calls = new java.util.ArrayList<Mode>();
                ui = new forge.neo.match.NeoMatchUI(forge.neo.match.NeoMatchUI.Mode.HUMAN, false) {
                    @Override public boolean passPriorityShortcut(forge.gamemodes.match.PriorityPass.Mode mode) { calls.add(mode); return true; }
                };
                var table = new forge.neo.ui.TableScreen(150, 300);
                var scene = new javafx.scene.Scene(table, 1440, 900);
                forge.neo.diy.DiyMatchControls.install(scene, table, ui);
                for (KeyCode code : List.of(KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.NUMPAD6, KeyCode.DIGIT0)) {
                    javafx.event.Event.fireEvent(table, key(code, false));
                    scene.getOnKeyReleased().handle(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
                }
                check(calls.equals(List.of(Mode.OFF, Mode.UNTIL_RESPONSE, Mode.WHOLE_TURN, Mode.UNTIL_END_STEP)),
                        "Scene did not dispatch the four priority actions: " + calls);
                int count = calls.size();
                var field = new javafx.scene.control.TextField();
                scene.getOnKeyPressed().handle(new KeyEvent(field, field, KeyEvent.KEY_PRESSED,
                        "6", "6", KeyCode.DIGIT6, false, false, false, false));
                check(calls.size() == count, "Typing a number fired a shortcut");
                table.getMenuOverlay().show(new javafx.scene.layout.VBox());
                javafx.event.Event.fireEvent(table, key(KeyCode.DIGIT0, false));
                check(calls.size() == count, "A modal did not protect its number keys");
                check(javafx.stage.Window.getWindows().isEmpty(), "Test opened a visible window");
                System.out.println("PRIORITY_SCENE_INPUT_OK");
                done.complete(null);
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            } finally {
                if (ui != null) ui.shutdown();
            }
        });
        done.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void engine() throws Exception {
        var lobby = new LobbyPlayerHuman("Shortcut test");
        var players = List.of(new RegisteredPlayer(new forge.deck.Deck()).setPlayer(lobby),
                new RegisteredPlayer(new forge.deck.Deck()).setPlayer(new LobbyPlayerHuman("Opponent")));
        var rules = new forge.game.GameRules(forge.game.GameType.Constructed);
        var game = new forge.game.Game(players, rules, new forge.game.Match(rules, players, "Priority tests"));
        var player = game.getPlayers().get(0);
        var controller = (PlayerControllerHuman) player.getController();
        controller.setGui((IGuiGame) Proxy.newProxyInstance(IGuiGame.class.getClassLoader(), new Class[]{IGuiGame.class},
                (p, m, a) -> {
                    if (m.getName().equals("getGameView")) return game.getView();
                    if (m.getName().equals("isUiSetToSkipPhase")) return true;
                    if (m.getName().equals("applyYieldUpdate")) { controller.getYieldController().apply((YieldUpdate) a[0]); return null; }
                    return defaultValue(m.getReturnType());
                }));
        var yields = controller.getYieldController();
        yields.setPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS, "true");
        yields.setPref(FPref.YIELD_INTERRUPT_ON_REVEAL, "true");
        yields.setPref(FPref.YIELD_AUTO_PASS_RESPECTS_INTERRUPTS, "true");
        phase(game, PhaseType.MAIN2, 7);
        forge.model.FModel.getPreferences().setPref(FPref.UI_PRIORITY_SHORTCUTS, false);
        check(!forge.gamemodes.match.PriorityShortcuts.perform(controller, controller, player.getView(), Mode.WHOLE_TURN),
                "Disabled global setting still dispatched a yield");
        check(yields.getPriorityPass().mode() == Mode.OFF, "Disabled shortcut changed engine state");
        forge.model.FModel.getPreferences().setPref(FPref.UI_PRIORITY_SHORTCUTS, true);
        check(forge.gamemodes.match.PriorityShortcuts.perform(controller, controller, player.getView(), Mode.WHOLE_TURN),
                "Enabled shared command did not dispatch");
        yields.maybeInterruptOnReveal();
        check(controller.mayAutoPass(), "6 was interrupted by a reveal");
        phase(game, PhaseType.END_OF_TURN, 7);
        check(controller.mayAutoPass(), "6 stopped at end step");
        controller.sendYieldUpdate(new YieldUpdate.PriorityShortcut(player.getView(), Mode.UNTIL_END_STEP));
        check(!controller.mayAutoPass(), "0 pressed at end step did not stop");
        check(!controller.isUiSetToSkipPhase(player.getView(), PhaseType.END_OF_TURN), "0 lost to phase skip");
        check(!yields.shouldAutoYield("any saved ability"), "0 lost to a saved trigger yield");
        phase(game, PhaseType.MAIN1, 8);
        controller.sendYieldUpdate(new YieldUpdate.PriorityShortcut(player.getView(), Mode.UNTIL_RESPONSE));
        yields.setPref(FPref.YIELD_INTERRUPT_ON_REVEAL, "false");
        yields.maybeInterruptOnReveal();
        check(!controller.mayAutoPass(), "2 did not stop when smart interrupts were disabled");
        yields.clearActiveYieldAndDispatch();
        check(yields.getPriorityPass().mode() == Mode.OFF, "Cancel retained a shortcut");
        controller.sendYieldUpdate(new YieldUpdate.PriorityShortcut(player.getView(), Mode.WHOLE_TURN));
        phase(game, PhaseType.CLEANUP, 8);
        controller.autoPassCancel();
        check(controller.mayAutoPass(), "6 must also yield cleanup triggers after native end-turn reset");
        phase(game, PhaseType.UPKEEP, 9);
        yields.shouldAutoYield();
        check(yields.getPriorityPass().mode() == Mode.OFF, "6 leaked into the next turn");
        check(yields.getBoolPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS), "Shortcut modified persistent auto-pass preference");
        var command = new YieldUpdate.PriorityShortcut(player.getView(), Mode.UNTIL_END_STEP);
        var bytes = new java.io.ByteArrayOutputStream();
        new java.io.ObjectOutputStream(bytes).writeObject(command);
        var read = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())).readObject();
        check(read instanceof YieldUpdate.PriorityShortcut u && u.mode() == Mode.UNTIL_END_STEP,
                "Multiplayer yield command did not round-trip");
        yields.setPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS, "false");
        yields.setPref(FPref.YIELD_DECLINE_SCOPE_STACK_YIELD, "NEVER");
        yields.setPref(FPref.YIELD_DECLINE_SCOPE_NO_ACTIONS, "NEVER");
        var priority = new forge.gamemodes.match.input.InputPassPriority(controller);
        controller.getInputQueue().setInput(priority);
        var suggestion = priority.getClass().getDeclaredField("pendingSuggestion");
        suggestion.setAccessible(true);
        suggestion.set(priority, forge.gamemodes.match.SuggestionType.NO_ACTIONS);
        controller.passPriority();
        check(controller.getInputQueue().getInput() == null, "1 did not release the current priority input");
        check(!yields.isYieldActive(), "1 accepted an auto-yield suggestion instead of passing once");
        var accepted = new java.util.concurrent.atomic.AtomicInteger();
        var choice = new forge.gamemodes.match.input.InputSyncronizedBase(controller) {
            @Override protected void showMessage() { }
            @Override protected void onOk() { accepted.incrementAndGet(); }
            @Override public String getActivateAction(forge.game.card.Card card) { return null; }
        };
        controller.getInputQueue().setInput(choice);
        controller.passPriority();
        controller.sendYieldUpdate(new YieldUpdate.PriorityShortcut(player.getView(), Mode.WHOLE_TURN));
        check(accepted.get() == 0, "Priority shortcuts answered a mandatory choice");
        choice.stop();
        System.out.println("PRIORITY_ENGINE_STOPS_OK");
    }

    private static void phase(forge.game.Game game, PhaseType phase, int turn) throws Exception {
        game.getPhaseHandler().devModeSet(phase, game.getPlayers().get(0), false, turn);
        var update = forge.game.GameView.class.getDeclaredMethod("updateTurn", forge.game.phase.PhaseHandler.class);
        update.setAccessible(true);
        update.invoke(game.getView(), game.getPhaseHandler());
    }
}
