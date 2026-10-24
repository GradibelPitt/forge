package forge.neo.diy;

import forge.game.GameView;
import forge.game.player.PlayerView;
import forge.neo.match.TableBinder;
import forge.neo.ui.TableScreen;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/** Retains native prompts and dialog state without projecting a second board. */
public final class NativeMatchBinder extends TableBinder {
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile GameView game;
    private volatile PlayerView self;

    public NativeMatchBinder(final TableScreen table) {
        super(table);
        if (!table.isNativeOnly()) throw new IllegalArgumentException("Native dialog host required");
    }

    @Override public void setGameView(final GameView view) {
        game = view;
        requestRefresh();
    }

    @Override public void setSelf(final PlayerView player) {
        self = player;
        requestRefresh();
    }

    @Override public void requestRefresh() {
        if (!pending.compareAndSet(false, true)) return;
        try {
            Platform.runLater(() -> {
                pending.set(false);
                final GameView view = game;
                getTable().setStackGame(view == null ? null : view.getGame());
                getTable().setStack(view == null ? null : view.getStack(), self);
            });
        } catch (IllegalStateException stopped) {
            pending.set(false);
        }
    }
}
