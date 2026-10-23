package forge.screens.home;

import java.awt.BorderLayout;
import java.util.function.IntConsumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;

/** Owns the Swing/JavaFX boundary without shutting down the shared FX toolkit. */
@SuppressWarnings("serial")
public final class HearthHomePanel extends JPanel {
    private final String[] labels;
    private final IntConsumer navigate;
    private final Runnable sound;
    private JFXPanel host;
    private HearthHomeScene scene;
    private volatile boolean attached;

    public HearthHomePanel(String[] labels, IntConsumer navigate, Runnable sound) {
        super(new BorderLayout());
        this.labels = labels.clone(); this.navigate = navigate; this.sound = sound;
        setBackground(new java.awt.Color(25, 16, 10));
    }

    @Override public void addNotify() {
        super.addNotify(); attached = true;
        if (host == null) { host = new JFXPanel(); add(host, BorderLayout.CENTER); }
        Platform.runLater(() -> {
            Platform.setImplicitExit(false);
            if (scene == null) {
                scene = new HearthHomeScene(labels,
                        index -> SwingUtilities.invokeLater(() -> { if (attached) { navigate.accept(index); } }),
                        () -> SwingUtilities.invokeLater(() -> { if (attached) { sound.run(); } }));
                host.setScene(scene.getScene());
            }
            scene.setActive(attached);
        });
        revalidate();
    }

    @Override public void removeNotify() {
        attached = false;
        if (host != null) { Platform.runLater(() -> { if (scene != null) { scene.setActive(false); } }); }
        super.removeNotify();
    }
}
