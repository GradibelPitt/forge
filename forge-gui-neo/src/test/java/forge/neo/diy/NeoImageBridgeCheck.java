import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import forge.neo.card.CardImages;
import forge.neo.card.CardNode;
import forge.neo.diy.DiyMatchAppearance;
import forge.util.CardImagePolicy;
import forge.util.ImageFetcher;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences.FPref;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class NeoImageBridgeCheck {
    private static final class Fetcher extends forge.util.SwingImageFetcher {
        final BlockingQueue<Callback> callbacks = new LinkedBlockingQueue<>();
        @Override public void fetchImage(String key, Callback callback) {
            if (!SwingUtilities.isEventDispatchThread()) throw new AssertionError("Download requested outside EDT");
            if (callback != null) callbacks.add(callback);
        }
    }

    public static void run(forge.StaticData data, Path testRoot) throws Exception {
        Path cache = Files.createTempDirectory(testRoot, "image-cache-");
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var cacheField = ForgeConstants.class.getDeclaredField("CACHE_CARD_PICS_DIR");
        unsafe.putObject(unsafe.staticFieldBase(cacheField), unsafe.staticFieldOffset(cacheField), cache + "/");
        var fetcher = new Fetcher();
        NeoSceneProbe.imageFetcher = fetcher;
        var prefs = forge.model.FModel.getPreferences();
        prefs.setPref(FPref.UI_ENABLE_ONLINE_IMAGE_FETCHER, true);
        prefs.setPref(FPref.UI_LANGUAGE, "zh-CN");
        prefs.setPref(FPref.UI_RENDER_BLACK_BORDERS, true);
        Platform.startup(() -> Platform.setImplicitExit(false));
        CardImages.clear();
        var card = data.getCommonCards().getCard("Lightning Bolt");
        String key = card.getImageKey(false);
        var ready = new CountDownLatch(1);
        CardImages.Listener listener = () -> {
            if (!Platform.isFxApplicationThread()) throw new AssertionError("Image notification outside FX");
            if (CardImages.isCached(key)) ready.countDown();
        };
        CardImages.addListener(listener);
        if (CardImages.get(key) != null) throw new AssertionError("Cold cache was not empty");
        ImageFetcher.Callback callback = fetcher.callbacks.poll(5, TimeUnit.SECONDS);
        if (callback == null) throw new AssertionError("Missing callback for uncached desktop card");
        Path destination = CardImagePolicy.officialImageFile(CardImagePolicy.officialImageKey(card.getCardImageKey())).toPath();
        Files.createDirectories(destination.getParent());
        ImageIO.write(new BufferedImage(745, 1040, BufferedImage.TYPE_INT_RGB), "png", destination.toFile());
        long started = System.nanoTime();
        SwingUtilities.invokeAndWait(callback::onImageFetched);
        if (!ready.await(3, TimeUnit.SECONDS)) throw new AssertionError("Downloaded image did not reach Neo UI");
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (CardImages.peek(key).getWidth() != 745) throw new AssertionError("Original resolution lost");
        CardImages.removeListener(listener);
        System.out.println("DOWNLOAD_CALLBACK_OK milliseconds=" + millis + " size=745x1040");

        var cached = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> new forge.util.SwingImageFetcher().fetchImage(key, cached::countDown));
        if (!cached.await(1, TimeUnit.SECONDS)) throw new AssertionError("Late subscriber missed cached image");

        // An old scan remains usable with downloads off; no user's real cache is touched.
        var forest = data.getCommonCards().getCard("Forest");
        Path legacy = CardImagePolicy.officialImageFile(CardImagePolicy.legacyOfficialImageKey(forest.getCardImageKey())).toPath();
        Files.createDirectories(legacy.getParent());
        ImageIO.write(new BufferedImage(488, 680, BufferedImage.TYPE_INT_RGB), "jpg", legacy.toFile());
        prefs.setPref(FPref.UI_ENABLE_ONLINE_IMAGE_FETCHER, false);
        if (forge.neo.diy.DiyCardImages.read(forest.getImageKey(false)) == null)
            throw new AssertionError("Offline legacy scan was lost");
        System.out.println("LEGACY_CACHE_OFFLINE_OK");

        var checked = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                var label = new Label("手牌 墓场 放逐");
                label.getStyleClass().add("caption");
                var root = new VBox(label);
                var scene = new Scene(root, 1200, 900);
                scene.getStylesheets().add(forge.neo.ui.TableScreen.class.getResource("/forge/neo/neo.css").toExternalForm());
                DiyMatchAppearance.install(scene);
                root.applyCss(); root.layout();
                if (label.getFont().getSize() < 11) throw new AssertionError("Chinese caption is too small: " + label.getFont());
                if (!label.getFont().getFamily().contains("YaHei") && !label.getFont().getFamily().contains("CJK")
                        && !label.getFont().getFamily().contains("Han Sans") && !label.getFont().getFamily().contains("PingFang"))
                    throw new AssertionError("Chinese font fallback: " + label.getFont());
                forge.neo.ui.UiScale.setOverride(1.15);
                DiyMatchAppearance.apply(scene);
                root.applyCss();
                if (label.getFont().getSize() < 12) throw new AssertionError("Manual scale was ignored");
                forge.neo.ui.UiScale.setOverride(null);
                System.out.println("CHINESE_TYPOGRAPHY_OK font=" + label.getFont());
                var zoom = forge.neo.ui.CardZoom.compose(forge.game.card.Card.getCardForUi(card).getView(), 550, 1200, null);
                root.getChildren().setAll(zoom);
                root.applyCss(); root.layout();
                CardNode.refreshAllIn(root);
                var enlarged = findCard(zoom);
                var artField = CardNode.class.getDeclaredField("art"); artField.setAccessible(true);
                var art = (javafx.scene.image.ImageView) artField.get(enlarged);
                if (art.getImage().getWidth() != 745) throw new AssertionError("Zoom enlarged a thumbnail");
                System.out.println("ZOOM_ORIGINAL_IMAGE_OK width=" + art.getImage().getWidth());
                if (java.awt.Window.getWindows().length != 0 || !javafx.stage.Window.getWindows().isEmpty())
                    throw new AssertionError("Unexpected window");
                checked.complete(null);
            } catch (Throwable t) { checked.completeExceptionally(t); }
        });
        checked.get(10, TimeUnit.SECONDS);
        Platform.exit();
        System.exit(0);
    }

    private static CardNode findCard(javafx.scene.Node node) {
        if (node instanceof CardNode card) return card;
        if (node instanceof javafx.scene.Parent parent) {
            for (var child : parent.getChildrenUnmodifiable()) {
                CardNode card = findCard(child);
                if (card != null) return card;
            }
        }
        return null;
    }
}
