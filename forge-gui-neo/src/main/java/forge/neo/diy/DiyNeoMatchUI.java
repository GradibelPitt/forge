package forge.neo.diy;

import java.awt.BorderLayout;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import forge.Singletons;
import forge.game.player.PlayerView;
import forge.gui.framework.FScreen;
import forge.gui.framework.ICDoc;
import forge.gui.framework.IVTopLevelUI;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.neo.NeoSettings;
import forge.neo.card.CardImages;
import forge.neo.card.CardNode;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.platform.NeoFonts;
import forge.neo.ui.TableScreen;
import forge.sound.MusicPlaylist;
import forge.sound.SoundSystem;
import forge.trackable.TrackableCollection;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;

/** Hosts NeoForge's match scene inside Forge's existing Swing match tab. */
public final class DiyNeoMatchUI extends NeoMatchUI implements ICDoc, IVTopLevelUI {
    private JFXPanel panel;
    private FScreen screen;
    private TableScreen table;
    private TableBinder binder;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CardImages.Listener artListener = () -> {
        if (table != null && !closed.get()) { table.refreshAll(); }
    };

    private DiyNeoMatchUI() {
        super(Mode.HUMAN, false);
    }

    public static DiyNeoMatchUI create() {
        final DiyNeoMatchUI gui = new DiyNeoMatchUI();
        final Runnable createPanel = () -> {
            gui.panel = new JFXPanel();
            gui.screen = FScreen.getEmbeddedMatchScreen(gui, gui);
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) { createPanel.run(); }
            else { SwingUtilities.invokeAndWait(createPanel); }
        } catch (Exception e) { throw new IllegalStateException("Cannot create the NeoForge match panel", e); }
        Platform.runLater(() -> {
            try { gui.createScene(); gui.ready.complete(null); }
            catch (Throwable e) { gui.ready.completeExceptionally(e); }
        });
        gui.ready.join();
        return gui;
    }

    private void createScene() {
        Platform.setImplicitExit(false);
        System.setProperty("neo.language", FModel.getPreferences().getPref(FPref.UI_LANGUAGE));
        forge.neo.NeoText.reload();
        CardNode.setAnimationsEnabled(NeoSettings.getBool(NeoSettings.ANIMATIONS, true));
        CardNode.setFoilEffectEnabled(NeoSettings.getBool(NeoSettings.FOIL_EFFECT, true));
        forge.neo.ui.ImpactLayer.setEnabled(NeoSettings.getBool(NeoSettings.IMPACT_EFFECTS, true));
        forge.neo.ui.ImpactLayer.setEntranceEnabled(NeoSettings.getBool(NeoSettings.COMMANDER_ENTRANCE, true));
        forge.neo.ui.HandFan.setFanned(NeoSettings.handFan());
        table = new TableScreen(150, 300);
        table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        binder = new TableBinder(table);
        binder.setMatchUi(this);
        setBinder(binder);
        setTable(table);
        setAutoPayMana(NeoSettings.autoPayMana());
        final Scene scene = new Scene(table);
        scene.getStylesheets().add(DiyNeoMatchUI.class.getResource("/forge/neo/neo.css").toExternalForm());
        NeoFonts.apply(scene);
        scene.heightProperty().addListener((o, a, b) ->
                table.setStyle(forge.neo.ui.UiScale.rootStyle(b.doubleValue())));
        DiyMatchControls.install(scene, table, this);
        CardImages.addListener(artListener);
        panel.setScene(scene);
        setOnClosed(this::closeMatchTab);
        setOnAbandon(this::closeMatchTab);
    }

    @Override
    public void openView(final TrackableCollection<PlayerView> players) {
        ready.join();
        if (isNetGame()) { setEnding(Ending.NET); }
        super.openView(players);
        SwingUtilities.invokeLater(() -> {
            if (!closed.get()) {
                screen.setTabCaption(getGameView().getTitle());
                Singletons.getControl().setCurrentScreen(screen);
            }
        });
    }

    private void closeMatchTab() {
        if (!closed.compareAndSet(false, true)) { return; }
        shutdown();
        Platform.runLater(() -> {
            CardImages.removeListener(artListener);
            table.forgetEngineDialogs();
            binder.setGameView(null);
            binder.setMatchUi(null);
            setBinder(null);
            setTable(null);
            panel.getScene().setRoot(new javafx.scene.layout.Pane());
            panel.setScene(null);
        });
        SwingUtilities.invokeLater(() -> {
            SoundSystem.instance.setBackgroundMusic(MusicPlaylist.MENUS);
            Singletons.getView().getNavigationBar().closeTab(screen, true);
        });
    }

    @Override public void afterGameFailure(final String message) {
        closeMatchTab();
        SwingUtilities.invokeLater(() -> forge.toolbox.FOptionPane.showErrorDialog(message));
    }

    @Override public void register() { }
    @Override public void initialize() { }
    @Override public void update() { }
    @Override public void instantiate() { }
    @Override public void populate() {
        final javax.swing.JPanel content = Singletons.getView().getPnlInsets();
        content.removeAll();
        content.setBorder(null);
        content.setLayout(new BorderLayout());
        content.add(panel, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }
    @Override public boolean onSwitching(final FScreen from, final FScreen to) { return true; }
    @Override public boolean onClosing(final FScreen closing) {
        if (closed.get()) { return true; }
        Platform.runLater(() -> DiyMatchControls.askQuit(table, this));
        return false;
    }
}
