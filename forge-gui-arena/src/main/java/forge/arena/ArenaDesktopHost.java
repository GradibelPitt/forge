package forge.arena;

/** Keeps browser lifecycle separate from the game and native dialog bridge. */
final class ArenaDesktopHost implements AutoCloseable {
    private ChromiumWindow window;
    private final String url;
    private final Runnable stopped;
    private volatile boolean restartable;
    private Boolean fullscreen;

    ArenaDesktopHost(String url, Runnable stopped) {
        this.url = url;
        this.stopped = stopped;
        window = newWindow();
    }

    private ChromiumWindow newWindow() {
        ChromiumWindow next = new ChromiumWindow(() -> { restartable = true; stopped.run(); });
        if (fullscreen != null) next.setFullscreen(fullscreen);
        return next;
    }

    void open() {
        if (restartable) { window = newWindow(); restartable = false; }
        window.open(url);
    }
    void reload() { if (restartable || !window.running()) open(); else window.reload(); }
    boolean running() { return window.running(); }
    String failure() { return window.failure(); }
    boolean isFullscreen() { return restartable && fullscreen != null ? fullscreen : window.isFullscreen(); }
    void setFullscreen(boolean value) { fullscreen = value; window.setFullscreen(value); }
    void update(boolean nativeDialog, long focusSequence) { window.update(nativeDialog, focusSequence); }
    @Override public void close() { window.close(); }
}
