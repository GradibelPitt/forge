package forge.neo.diy;

import forge.neo.NeoShortcuts;
import forge.neo.match.NeoMatchUI;
import forge.neo.ui.CardZoom;
import forge.neo.ui.PauseMenu;
import forge.neo.ui.SettingsPanel;
import forge.neo.ui.TableKeys;
import forge.neo.ui.TableScreen;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.EnumSet;
import java.util.Set;

/** NeoForge's original match menu and shortcuts, scoped to one embedded scene. */
public final class DiyMatchControls {

    public static final String FULLSCREEN_GET_PROPERTY = "forge.arena.fullscreen.get";
    public static final String FULLSCREEN_SET_PROPERTY = "forge.arena.fullscreen.set";

    private DiyMatchControls() {
    }

    private final Set<NeoShortcuts.Action> held = EnumSet.noneOf(NeoShortcuts.Action.class);

    public static void install(final Scene scene, final TableScreen table, final NeoMatchUI ui) {
        new DiyMatchControls().bind(scene, table, ui);
    }

    private void bind(final Scene scene, final TableScreen table, final NeoMatchUI ui) {
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                if (table.isZoomShowing()) {
                    table.hideZoom();
                } else if (table.getOverlay().isPeeking()) {
                    table.getOverlay().setPeeking(false);
                } else if (table.getMenuOverlay().isShowing()) {
                    table.getMenuOverlay().hide();
                } else {
                    openPause(table, ui);
                }
                ev.consume();
                return;
            }
            if (shortcut(ev, scene, table, ui)) {
                ev.consume();
            }
        });
        scene.setOnKeyReleased(ev -> held.clear());
        TableKeys.guardFocusedButtons(scene, () -> table,
                ev -> shortcut(ev, scene, table, ui), held::clear);
        table.enablePauseButton(() -> openPause(table, ui));
    }

    private static PauseMenu openPause(final TableScreen table, final NeoMatchUI ui) {
        if (table.getMenuOverlay().isShowing()) {
            table.getMenuOverlay().hide();
        }
        final PauseMenu menu = PauseMenu.forMatch(new PauseMenu.Actions() {
            @Override
            public void resume() {
                table.getMenuOverlay().hide();
            }

            @Override
            public void restart() {
                table.getMenuOverlay().hide();
            }

            @Override
            public void quitToMenu() {
                table.getMenuOverlay().hide();
                ui.leaveMatch(NeoMatchUI.Exit.MENU);
            }

            @Override
            public boolean hasSavedOrders() {
                return ui.hasSavedOrders();
            }

            @Override
            public void forgetSavedOrders() {
                ui.forgetSavedOrders();
            }
        }, host(table, ui), false, ui.isNetGame(), false);
        table.getMenuOverlay().show(menu);
        return menu;
    }

    public static void askQuit(TableScreen table, NeoMatchUI ui) { openPause(table, ui).askQuit(); }

    private static SettingsPanel.Host host(final TableScreen table, final NeoMatchUI ui) {
        return new SettingsPanel.Host() {
            @Override
            public void setAutoPayMana(final boolean on) {
                ui.setAutoPayMana(on);
            }

            @Override
            public void setPauseMode(final int mode) {
                ui.setPauseMode(mode);
            }

            @Override
            public void setAiSpeed(final int hundredths) {
                ui.setAiSpeed(hundredths);
            }

            @Override
            public void applyScale() {
                final Scene s = table.getScene();
                if (s != null && s.getRoot() != null) {
                    DiyMatchAppearance.apply(s);
                }
            }

            @Override
            public boolean isFullScreen() {
                final Scene scene = table.getScene();
                final Object bridge = scene == null ? null : scene.getProperties().get(FULLSCREEN_GET_PROPERTY);
                if (bridge instanceof java.util.function.BooleanSupplier query) {
                    return query.getAsBoolean();
                }
                return forge.Singletons.getView().getFrame().isFullScreen();
            }

            @Override
            @SuppressWarnings("unchecked")
            public void setFullScreen(final boolean on) {
                final Scene scene = table.getScene();
                final Object bridge = scene == null ? null : scene.getProperties().get(FULLSCREEN_SET_PROPERTY);
                if (bridge instanceof java.util.function.Consumer<?> request) {
                    ((java.util.function.Consumer<Boolean>) request).accept(on);
                    return;
                }
                javax.swing.SwingUtilities.invokeLater(() ->
                        forge.Singletons.getView().getFrame().setFullScreen(on));
            }

            @Override
            public double getTextZoom() {
                return table.getDetailPanel().getTextZoom();
            }

            @Override
            public void setTextZoom(final double zoom) {
                table.getDetailPanel().setTextZoom(zoom);
            }

            @Override
            public void setDraftRankingVisible(final boolean on) {
            }

            @Override
            public boolean isInMatch() {
                return true;
            }
        };
    }

    /** Los atajos de partida principales, igual que NeoApp.runShortcut. */
    private boolean shortcut(final KeyEvent ev, final Scene scene, final TableScreen table,
                                    final NeoMatchUI ui) {
        if (ev.getTarget() instanceof javafx.scene.control.TextInputControl
                || scene.getFocusOwner() instanceof javafx.scene.control.TextInputControl) {
            return false;
        }
        final NeoShortcuts.Action action = NeoShortcuts.actionFor(ev);
        if (action == null) {
            return false;
        }
        if (!action.repeats() && !held.add(action)) {
            return true;
        }
        final boolean free = !table.isModalShowing();
        switch (action) {
            case PASS_PRIORITY:
                // El aviso de "esto te acaba de pasar" va primero: mientras esta
                // puesto el motor espera SOLO su boton, asi que es lo unico que
                // esta tecla puede querer decir (reportado: "I have to click on
                // it, again no shortcut"). Ver PromptBanner.acknowledge.
                if (free && table.getPromptBanner().acknowledge()) {
                    return true;
                }
                // Pasar prioridad. Es lo que acaba usando todo el mundo.
                return free && (forge.gamemodes.match.PriorityShortcuts.isEnabled()
                        ? ui != null && ui.passPriorityShortcut(forge.gamemodes.match.PriorityPass.Mode.OFF)
                        : table.getActionBar().pressPrimary());
            case PASS_TURN:
                return free && ui != null && (forge.gamemodes.match.PriorityShortcuts.isEnabled()
                        ? ui.passPriorityShortcut(forge.gamemodes.match.PriorityPass.Mode.WHOLE_TURN) : ui.passTurn());
            case PASS_UNTIL_RESPONSE:
                return free && ui != null && ui.passPriorityShortcut(forge.gamemodes.match.PriorityPass.Mode.UNTIL_RESPONSE);
            case PASS_END_STEP:
                return free && ui != null && ui.passPriorityShortcut(forge.gamemodes.match.PriorityPass.Mode.UNTIL_END_STEP);
            case ALPHA_STRIKE:
                return free && ui != null && ui.alphaStrike();
            case ATTACK_NON_TOKENS:
                return free && ui != null && ui.attackWithNonTokens();
            case UNDO:
                // Deshace lo ultimo (tapear una tierra por error).
                ui.undoLast();
                return true;
            case ZOOM_CARD: {
                // Amplia la carta que tenga el raton encima, sin soltarlo. El
                // click derecho ya hace lo mismo; esto es para quien tiene la
                // mano en el teclado.
                //
                // OJO: en la mesa la ampliacion es table.showZoom(card), NO
                // CardZoom.show(...) — ese es el mecanismo generico de las demas
                // pantallas (cambia la raiz de la escena) y aqui haria dos cosas
                // mal: se veria distinto del click derecho de siempre, y sobre
                // todo NO dispara Gesture.ZOOM_CARD, que es lo que el tutorial
                // espera para cerrar su paso de "click derecho para leer".
                final forge.neo.card.CardNode hovered = forge.neo.ui.CardZoom.hoveredCardNode(table);
                if (hovered != null && hovered.getCard() != null) {
                    table.showZoom(hovered.getCard());
                    return true;
                }
                return false;
            }
            case GAME_LOG:
                // El registro de la partida: el mismo boton, para que el gesto
                // del tutorial salte igual.
                table.getLogButton().fire();
                return true;
            case EXPAND_STACK:
                return table.toggleAllStackEntries();
            case STACK_MENU:
                return free && ui != null && ui.openTopStackMenu();
            case AUTO_YIELD_YES:
            case AUTO_YIELD_NO: {
                if (!free || ui == null) {
                    return false;
                }
                final String said = ui.autoYieldTop(action == forge.neo.NeoShortcuts.Action.AUTO_YIELD_YES);
                if (said == null) {
                    return false;
                }
                table.getActionBar().setWarning(said, false);
                return true;
            }
            case FULL_CONTROL: {
                if (ui == null) {
                    return false;
                }
                final Boolean on = ui.toggleFullControl();
                if (on == null) {
                    return false;
                }
                table.getActionBar().setWarning(forge.neo.NeoText.get(on
                        ? "shortcuts.fullControl.on" : "shortcuts.fullControl.off"), false);
                return true;
            }
            case CONCEDE:
                // Rendirse no se deshace: abre la MISMA pregunta que el menu.
                if (table.getMenuOverlay().isShowing()) {
                    return false;
                }
                openPause(table, ui).askQuit();
                return true;
            case MACRO_RECORD:
                return free && ui != null && ui.pressMacroRecord();
            case MACRO_PLAY:
                return free && ui != null && ui.pressMacroPlay();
            case MACRO_NEXT:
                return free && ui != null && ui.nextMacroAction();
            case SHOW_SHORTCUTS:
                if (table.getMenuOverlay().isShowing()) {
                    return false;
                }
                table.getMenuOverlay().show(new forge.neo.ui.ShortcutsPanel(true,
                        forge.neo.NeoText.get("common.close"), () -> table.getMenuOverlay().hide()));
                return true;
            default:
                return false;
        }
    }
}
