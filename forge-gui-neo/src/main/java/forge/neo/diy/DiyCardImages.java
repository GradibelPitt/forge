package forge.neo.diy;

import java.awt.image.BufferedImage;
import forge.ImageCache;
import forge.gui.FThreads;
import java.util.concurrent.FutureTask;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

/** Uses DIY's localized scans and generated custom-card faces in the Neo scene. */
public final class DiyCardImages {
    private DiyCardImages() { }

    public static Image read(final String imageKey) {
        final FutureTask<BufferedImage> task = new FutureTask<>(() -> ImageCache.getOriginalImage(imageKey, false, null));
        if (FThreads.isGuiThread()) { task.run(); }
        else { FThreads.invokeInEdtAndWait(task); }
        final BufferedImage face;
        try { face = task.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        catch (java.util.concurrent.ExecutionException e) { throw new IllegalStateException("Cannot load card image " + imageKey, e.getCause()); }
        return face == null ? null : SwingFXUtils.toFXImage(face, null);
    }
}
