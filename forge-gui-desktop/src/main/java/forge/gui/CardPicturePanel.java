/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;

import javax.swing.JPanel;
import javax.swing.Timer;

import forge.ImageCache;
import forge.ImageKeys;
import forge.game.card.CardView.CardStateView;
import forge.item.InventoryItem;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.toolbox.CardFaceSymbols;
import forge.toolbox.imaging.FImagePanel;
import forge.toolbox.imaging.FImagePanel.AutoSizeImageMode;
import forge.toolbox.imaging.FImageUtil;
import forge.util.ImageFetcher;

/**
 * Displays image associated with a card or inventory item.
 *
 * @version $Id: CardPicturePanel.java 25265 2014-03-27 02:18:47Z drdev $
 *
 */
public final class CardPicturePanel extends JPanel implements ImageFetcher.Callback {

    /**
     * Constant <code>serialVersionUID=-3160874016387273383L</code>.
     */
    private static final long serialVersionUID = -3160874016387273383L;

    private Object displayed;
    private boolean mayView = true;
    private boolean lastFlipped;
    private String lastImageKey;
    private long lastImageCacheGeneration = -1;
    private boolean currentImageResolved;

    private final FImagePanel panel;
    private final Timer resizeRefreshTimer;
    private BufferedImage currentImage;

    public CardPicturePanel() {
        super(new BorderLayout());

        this.panel = new FImagePanel();
        this.add(this.panel);
        this.resizeRefreshTimer = new Timer(100, event -> refreshForCurrentSize());
        this.resizeRefreshTimer.setRepeats(false);
        this.panel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent event) {
                if (displayed != null) {
                    resizeRefreshTimer.restart();
                }
            }
        });
    }

    public Object getDisplayed() { return displayed; }

    public void setItem(final InventoryItem item) {
        setImage(item, true, false);
    }

    public void setItem(final BufferedImage image) {
        this.currentImage = image;
        this.currentImageResolved = image != null;
        this.panel.setImage(image, getAutoSizeImageMode());
        this.displayed = null;
        this.mayView = false;
        this.lastImageKey = null;
        this.lastImageCacheGeneration = ImageCache.getCacheGeneration();
    }

    public void setCard(final CardStateView c) {
        setCard(c, true);
    }

    public void setCard(final CardStateView c, final boolean mayView) {
        setCard(c, mayView, false);
    }

    public void setCard(final CardStateView c, final boolean mayView, boolean isFlipped) {
        setImage(c, mayView, isFlipped);
    }

    private void setImage(final Object display, final boolean mayView, final boolean isFlipped) {
        final boolean previousMayView = this.mayView;
        final boolean previousFlipped = this.lastFlipped;
        this.displayed = display;
        this.mayView = mayView;

        final Dimension thumbnailSize = CardPreviewSizePolicy.getThumbnailSize(
                panel.getWidth(), panel.getHeight(), GuiBase.getInterface().getScreenScale());
        final String imageKey = CardPreviewSizePolicy.getRequestKey(
                getImageKey(display, mayView), thumbnailSize, getDecorationState(display, mayView));
        final long imageCacheGeneration = ImageCache.getCacheGeneration();
        if (!CardPictureRefreshPolicy.shouldRefresh(lastImageKey, previousMayView, previousFlipped,
                lastImageCacheGeneration, imageKey, mayView, isFlipped, imageCacheGeneration,
                currentImageResolved && currentImage != null)) {
            return;
        }

        this.lastFlipped = isFlipped;
        this.lastImageKey = imageKey;
        this.lastImageCacheGeneration = imageCacheGeneration;

        final BufferedImage image = getImage(thumbnailSize);
        if (CardPictureRefreshPolicy.shouldApplyImage(this.currentImage, image, previousFlipped, isFlipped)) {
            this.currentImage = image;
            this.panel.setImage(isFlipped ? rotateImage180(image) : image, getAutoSizeImageMode());
        }
    }

    private BufferedImage getImage(final Dimension thumbnailSize) {
        currentImageResolved = false;
        final int width = thumbnailSize.width;
        final int height = thumbnailSize.height;
        if (!mayView) {
            // Pass the card even though its face is hidden: ImageCache reads only the owner's sleeve
            // index from it to pick the card-back. The face is never shown because the key stays HIDDEN_CARD.
            final BufferedImage image = ImageCache.scaleImage(ImageKeys.getTokenKey(ImageKeys.HIDDEN_CARD),
                    width, height, true, displayed instanceof CardStateView csv ? csv.getCard() : null);
            currentImageResolved = image != null && !ImageCache.isDefaultImage(image);
            return image;
        }

        if (displayed instanceof InventoryItem) {
            final InventoryItem item = (InventoryItem) displayed;
            BufferedImage image = ImageCache.scaleImage(item.getImageKey(false), width, height, false, null);
            currentImageResolved = image != null && !ImageCache.isDefaultImage(image);
            if (!currentImageResolved && item instanceof PaperCard) {
                GuiBase.getInterface().getImageFetcher().fetchImage(item.getImageKey(false), this);
            }
            if (image == null) {
                image = ImageCache.scaleImage(item.getImageKey(false), width, height, true, null);
            }
            return decoratePreview(image, currentImageResolved);
        } else if (displayed instanceof CardStateView) {
            CardStateView card = (CardStateView) displayed;
            BufferedImage image = CardPreviewImageLoader.loadOnce(
                    () -> ImageCache.scaleImage(card.getImageKey(), width, height, false, card.getCard()),
                    original -> decoratePreview(original, true));
            currentImageResolved = image != null && !ImageCache.isDefaultImage(image);
            if (!currentImageResolved) {
                GuiBase.getInterface().getImageFetcher().fetchImage(card.getImageKey(), this);
            }
            if (image == null) {
                image = CardPreviewImageLoader.loadOnce(
                        () -> ImageCache.scaleImage(card.getImageKey(), width, height, true, card.getCard()),
                        original -> decoratePreview(original, false));
            }
            return image;
        }
        return null;
    }

    private static String getImageKey(final Object display, final boolean mayView) {
        if (!mayView) {
            final int sleeveIndex = display instanceof CardStateView csv && csv.getCard().getOwner() != null
                    ? csv.getCard().getOwner().getSleeveIndex() : 0;
            return ImageKeys.getTokenKey(ImageKeys.HIDDEN_CARD) + '#' + sleeveIndex;
        }
        if (display instanceof InventoryItem item) {
            return item.getImageKey(false);
        }
        if (display instanceof CardStateView card) {
            return card.getImageKey();
        }
        return null;
    }

    private static String getDecorationState(final Object display, final boolean mayView) {
        if (!mayView || !isFoilOverlayEnabled()) {
            return null;
        }
        if (display instanceof CardStateView card && card.getFoilIndex() > 0) {
            return "foil@" + card.getFoilIndex() + ":on";
        }
        if (display instanceof PaperCard card && card.isFoil()) {
            return "foil@paper:on";
        }
        return null;
    }

    private BufferedImage decoratePreview(final BufferedImage image, final boolean cacheable) {
        if (image == null || !isFoilOverlayEnabled()) {
            return image;
        }

        if (displayed instanceof CardStateView card && card.getFoilIndex() > 0) {
            return processDecoration(cacheable,
                    () -> FImageUtil.applyFoilEffect(image, card.getFoilIndex()));
        }
        if (displayed instanceof PaperCard card && card.isFoil()) {
            return processDecoration(cacheable, () -> applyPaperFoilEffect(image));
        }
        return image;
    }

    private BufferedImage processDecoration(final boolean cacheable,
            final java.util.function.Supplier<BufferedImage> decorator) {
        return cacheable ? ImageCache.getOrCreateProcessedImage(lastImageKey + "#decorated", decorator)
                : decorator.get();
    }

    private static BufferedImage applyPaperFoilEffect(final BufferedImage image) {
        final ColorModel colorModel = image.getColorModel();
        final boolean alphaPremultiplied = colorModel.isAlphaPremultiplied();
        final WritableRaster raster = image.copyData(image.getRaster().createCompatibleWritableRaster());
        final BufferedImage decorated = new BufferedImage(colorModel, raster, alphaPremultiplied, null);
        final Graphics2D graphics = decorated.createGraphics();
        try {
            CardFaceSymbols.drawOther(graphics, String.format("foil%02d", 1), 0, 0,
                    decorated.getWidth(), decorated.getHeight());
        } finally {
            graphics.dispose();
        }
        return decorated;
    }

    private void refreshForCurrentSize() {
        if (displayed != null) {
            setImage(displayed, mayView, lastFlipped);
        }
    }

    private static boolean isFoilOverlayEnabled() {
        return FModel.getPreferences().getPrefBoolean(FPref.UI_OVERLAY_FOIL_EFFECT);
    }

    @Override
    public void onImageFetched() {
        setImage(displayed, mayView, lastFlipped);
        repaint();
    }

    private static AutoSizeImageMode getAutoSizeImageMode() {
        return (isUIScaleLarger() ? AutoSizeImageMode.PANEL : AutoSizeImageMode.SOURCE);
    }

    private static boolean isUIScaleLarger() {
        return FModel.getPreferences().getPrefBoolean(FPref.UI_SCALE_LARGER);
    }

    public void showAsDisabled(){
        this.panel.setAlpha(0.5f);
    }

    public void showAsEnabled(){ this.panel.setAlpha(0.0f); }

    private BufferedImage rotateImage180(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Create a new image to hold the rotated version
        BufferedImage rotated = new BufferedImage(width, height, image.getType());

        // Graphics2D to draw the rotated image
        Graphics2D g2d = rotated.createGraphics();

        // Rotate 180 degrees around the center of the image
        AffineTransform transform = new AffineTransform();
        transform.rotate(Math.toRadians(180), width / 2.0, height / 2.0);

        // Draw the original image onto the rotated canvas
        g2d.drawImage(image, transform, null);
        g2d.dispose();

        return rotated;
    }
}
