package forge.arena;

import forge.neo.ui.Overlay;
import forge.neo.ui.TableScreen;
import java.util.IdentityHashMap;
import java.util.Map;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/** Keeps the dormant board out of the renderer without changing its input/layout state. */
final class BoardLayers implements AutoCloseable {
    private final TableScreen table;
    private final Node browser, tools;
    private final Region background = new Region();
    private final Map<Node, Node> clips = new IdentityHashMap<>();
    private final ListChangeListener<Node> childrenChanged = change -> refresh();
    private boolean active;

    BoardLayers(TableScreen table, Node browser, Node tools) {
        this.table = table;
        this.browser = browser;
        this.tools = tools;
        background.setManaged(false);
        background.setBackground(new Background(new BackgroundFill(Color.web("#0b0f17"), CornerRadii.EMPTY, Insets.EMPTY)));
        background.setVisible(false);
        table.getChildren().add(table.getChildren().indexOf(browser), background);
        table.getChildren().addListener(childrenChanged);
        table.widthProperty().addListener((o, a, b) -> resize());
        table.heightProperty().addListener((o, a, b) -> resize());
        resize();
    }

    private void resize() { background.resize(table.getWidth(), table.getHeight()); }

    void setActive(boolean active) {
        this.active = active;
        refresh();
    }

    private boolean masked(Node node) {
        return active && node != browser && node != background && node != tools
                && !(node instanceof Overlay)
                && !(node == table.getPromptBanner() && table.getPromptBanner().isAlerting());
    }

    private void refresh() {
        background.setVisible(active);
        clips.entrySet().removeIf(entry -> {
            if (table.getChildren().contains(entry.getKey()) && masked(entry.getKey())) return false;
            entry.getKey().setClip(entry.getValue());
            return true;
        });
        for (Node node : table.getChildren()) {
            if (masked(node) && !clips.containsKey(node)) {
                // An empty clip skips the entire old subtree, including expensive effects.
                // Keep visibility/opacity untouched: Neo updates these while the board is dormant.
                clips.put(node, node.getClip());
                node.setClip(new Rectangle());
            }
        }
    }

    @Override public void close() {
        table.getChildren().removeListener(childrenChanged);
        setActive(false);
        table.getChildren().remove(background);
    }
}
