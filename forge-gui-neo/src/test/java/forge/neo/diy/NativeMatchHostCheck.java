package forge.neo.diy;

import forge.game.card.CardView;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.neo.ui.Overlay;
import forge.neo.ui.TableScreen;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Checks that native decisions survive without building a legacy card board. */
public final class NativeMatchHostCheck {
    public static void main(final String[] args) throws Exception {
        final String app = System.getProperty("forge.test.app") + "/";
        GuiBase.setInterface((IGuiBase) Proxy.newProxyInstance(IGuiBase.class.getClassLoader(),
                new Class[]{IGuiBase.class}, (proxy, method, values) -> {
                    if (method.getName().equals("getAssetsDir")) return app;
                    if (method.getName().equals("isRunningOnDesktop")) return true;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == float.class) return 1f;
                    if (method.getReturnType() == double.class) return 1d;
                    return null;
                }));
        GuiBase.setUsingAppDirectory(true);
        forge.util.Localizer.getInstance().initialize("en-US", app + "res/languages/");
        forge.util.Lang.createInstance("en-US");
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                final TableScreen table = new TableScreen(150, 300, true);
                final Scene scene = new Scene(table, 1200, 800);
                final var fullscreen = new java.util.concurrent.atomic.AtomicBoolean();
                scene.getProperties().put(DiyMatchControls.FULLSCREEN_GET_PROPERTY,
                        (java.util.function.BooleanSupplier) fullscreen::get);
                scene.getProperties().put(DiyMatchControls.FULLSCREEN_SET_PROPERTY,
                        (java.util.function.Consumer<Boolean>) fullscreen::set);
                final var hostMethod = DiyMatchControls.class.getDeclaredMethod("host", TableScreen.class,
                        forge.neo.match.NeoMatchUI.class);
                hostMethod.setAccessible(true);
                final var settings = (forge.neo.ui.SettingsPanel.Host) hostMethod.invoke(null, table, null);
                if (settings.isFullScreen()) throw new AssertionError("Fullscreen bridge starts enabled");
                settings.setFullScreen(true);
                if (!fullscreen.get() || !settings.isFullScreen())
                    throw new AssertionError("Fullscreen setting bypassed the Chromium bridge");
                settings.setFullScreen(false);
                if (fullscreen.get() || settings.isFullScreen())
                    throw new AssertionError("Fullscreen bridge could not restore windowed mode");
                table.applyCss();
                table.layout();
                if (table.isNativeDialogShowing()) throw new AssertionError("Idle host steals focus");
                if (table.getChildren().stream().filter(n -> n instanceof Overlay).count() != 4)
                    throw new AssertionError("Native dialog layers missing");
                if (table.getSelfField().getScene() != null || table.getOpponentField().getScene() != null)
                    throw new AssertionError("Legacy battlefield attached");
                final CardView card = new CardView(1, null, "Test card");
                for (int i = 0; i < 100; i++) {
                    table.setHand(List.of(card));
                    table.setSelfBattlefield(List.of(card));
                    table.setOpponentBattlefield(List.of(card));
                    table.setCommandZone(List.of(card), null);
                    table.damagePop(card, 3, true, false);
                    table.impactArrive(card, List.of(javafx.scene.paint.Color.RED));
                    table.refreshAll();
                }
                if (!table.handNodes().isEmpty() || !table.selfFieldNodes().isEmpty()
                        || !table.oppFieldNodes().isEmpty() || table.impactCount() != 0)
                    throw new AssertionError("Legacy cards or effects were created");
                final AtomicInteger responses = new AtomicInteger();
                table.getActionBar().setButtons("Confirm", "Cancel", true, true);
                table.getActionBar().setOnOk(responses::incrementAndGet);
                if (!table.getActionBar().pressPrimary() || responses.get() != 1)
                    throw new AssertionError("Native action callback disconnected");
                final Button choose = new Button("Choose");
                choose.setOnAction(event -> { responses.incrementAndGet(); table.getOverlay().hide(); });
                table.getOverlay().show(new VBox(new Label("Engine choice"), choose));
                if (!table.isNativeDialogShowing()) throw new AssertionError("Engine choice not detected");
                choose.fire();
                if (responses.get() != 2 || table.isNativeDialogShowing())
                    throw new AssertionError("Engine choice could not finish");
                table.showInfo("Tools", "Native tool result");
                if (!table.isNativeDialogShowing()) throw new AssertionError("Native tools not detected");
                table.getMenuOverlay().hide();
                final NativeMatchBinder binder = new NativeMatchBinder(table);
                binder.setPrompt("Choose a target");
                for (int i = 0; i < 100; i++) binder.requestRefresh();
                Platform.runLater(() -> {
                    try {
                        if (!table.getLastPrompt().equals("Choose a target"))
                            throw new AssertionError("Engine prompt lost");
                        if (!table.handNodes().isEmpty() || !table.selfFieldNodes().isEmpty())
                            throw new AssertionError("Binder rebuilt legacy cards");
                        scene.setRoot(new javafx.scene.layout.Pane());
                        System.out.println("NATIVE_MATCH_HOST_OK noLegacyCards=true nativeChoices=true prompts=true fullscreenBridge=true");
                    } catch (Throwable error) { failure.set(error); }
                    finally { done.countDown(); }
                });
            } catch (Throwable error) { failure.set(error); done.countDown(); }
        });
        if (!done.await(15, TimeUnit.SECONDS)) throw new AssertionError("Native host check timed out");
        Platform.exit();
        if (failure.get() != null) throw new AssertionError("Native host check failed", failure.get());
    }
}
