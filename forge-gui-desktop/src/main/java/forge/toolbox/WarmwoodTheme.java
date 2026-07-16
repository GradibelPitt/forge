package forge.toolbox;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;

/**
 * Shared, scalable painter and exact color contract for the Warmwood desktop skin.
 */
public final class WarmwoodTheme {
    public static final String SKIN_NAME = "warmwood";

    // Approved palette extracted from the accepted UI preview/reference.
    public static final Color WOOD_MID = Color.decode("#64442C");
    public static final Color WOOD_SHADOW = Color.decode("#543C24");
    public static final Color FRAME_SHADOW = Color.decode("#3C2C1C");
    public static final Color CREVICE = Color.decode("#1C140C");
    public static final Color TRAY = Color.decode("#34342C");
    public static final Color METAL_EDGE = Color.decode("#544C44");
    public static final Color BRASS = Color.decode("#B07A3C");
    public static final Color TEXT = Color.decode("#F2E7D4");

    private static final Color WOOD_HIGHLIGHT = Color.decode("#7B5637");
    private static final Color WOOD_LOWLIGHT = Color.decode("#2A1C13");
    private static final Color BRASS_HIGHLIGHT = Color.decode("#D29A58");
    private static final Color BRASS_SHADOW = Color.decode("#6A431F");
    private static final Color FIELD = Color.decode("#21160F");
    private static final Color FIELD_FOCUS = Color.decode("#2C1E14");

    public enum ButtonState {
        NORMAL,
        HOVER,
        PRESSED,
        TOGGLED,
        DISABLED
    }

    private WarmwoodTheme() {
    }

    public static boolean isSkinName(final String skinName) {
        return skinName != null && SKIN_NAME.equalsIgnoreCase(skinName.trim().replace(' ', '_'));
    }

    public static Color colorFor(final FSkin.Colors color) {
        return switch (color) {
            case CLR_THEME -> FRAME_SHADOW;
            case CLR_BORDERS -> BRASS;
            case CLR_ZEBRA -> WOOD_SHADOW;
            case CLR_HOVER -> WOOD_HIGHLIGHT;
            case CLR_ACTIVE -> Color.decode("#8A603C");
            case CLR_INACTIVE -> FRAME_SHADOW;
            case CLR_TEXT -> TEXT;
            case CLR_PHASE_INACTIVE_ENABLED -> Color.decode("#6A4A31");
            case CLR_PHASE_INACTIVE_DISABLED -> Color.decode("#33251B");
            case CLR_PHASE_ACTIVE_ENABLED -> BRASS_HIGHLIGHT;
            case CLR_PHASE_ACTIVE_DISABLED -> Color.decode("#75532F");
            case CLR_THEME2 -> FIELD;
            case CLR_OVERLAY -> Color.decode("#120D09");
            case CLR_COMBAT_TARGETING_ARROW -> Color.decode("#C45A3A");
            case CLR_NORMAL_TARGETING_ARROW -> BRASS_HIGHLIGHT;
            case CLR_PWATTK_TARGETING_ARROW -> Color.decode("#D9B56D");
        };
    }

    public static Color getFieldColor(final boolean focused) {
        return focused ? FIELD_FOCUS : FIELD;
    }

    public static void paintPanel(final Graphics2D source, final int width, final int height,
            final int cornerDiameter, final Image texture, final Color overlay) {
        if (width <= 0 || height <= 0) {
            return;
        }

        final Graphics2D g = (Graphics2D) source.create();
        try {
            applyQualityHints(g);
            final int chamfer = Math.max(2, Math.min(8,
                    Math.min(cornerDiameter / 2, Math.min(width, height) / 5)));

            g.setColor(CREVICE);
            g.fill(createChamferedShape(0, 0, width, height, chamfer));

            if (width <= 4 || height <= 4) {
                return;
            }

            g.setColor(FRAME_SHADOW);
            g.fill(createChamferedShape(2, 2, width - 4, height - 4, Math.max(1, chamfer - 1)));

            if (width <= 12 || height <= 12) {
                return;
            }

            final Shape content = createChamferedShape(6, 6, width - 12, height - 12,
                    Math.max(1, chamfer - 3));
            final Shape oldClip = g.getClip();
            g.clip(content);
            g.setColor(WOOD_MID);
            g.fill(content);
            tileImage(g, texture, 6, 6, width - 12, height - 12);
            g.setColor(new Color(CREVICE.getRed(), CREVICE.getGreen(), CREVICE.getBlue(), 118));
            g.fill(content);
            if (overlay != null) {
                g.setColor(overlay);
                g.fill(content);
            }
            g.setClip(oldClip);

            g.setColor(BRASS_SHADOW);
            g.draw(createChamferedShape(3, 3, width - 6, height - 6, Math.max(1, chamfer - 2)));
            g.setColor(new Color(BRASS.getRed(), BRASS.getGreen(), BRASS.getBlue(), 145));
            g.draw(createChamferedShape(5, 5, width - 10, height - 10, Math.max(1, chamfer - 3)));
            g.setColor(new Color(255, 230, 180, 40));
            g.drawLine(8, 6, Math.max(8, width - 9), 6);
            g.setColor(new Color(0, 0, 0, 100));
            g.drawLine(8, Math.max(6, height - 7), Math.max(8, width - 9), Math.max(6, height - 7));
        } finally {
            g.dispose();
        }
    }

    public static void paintButton(final Graphics2D source, final int width, final int height,
            final ButtonState state) {
        if (width <= 0 || height <= 0) {
            return;
        }

        final Graphics2D g = (Graphics2D) source.create();
        try {
            applyQualityHints(g);
            final int chamfer = Math.max(4, Math.min(10, height / 4));
            final boolean pressed = state == ButtonState.PRESSED || state == ButtonState.TOGGLED;
            final int faceOffset = pressed ? 2 : 0;

            g.setColor(CREVICE);
            g.fill(createChamferedShape(0, 0, width, height, chamfer));

            if (width <= 6 || height <= 6) {
                return;
            }

            g.setColor(METAL_EDGE);
            g.fill(createChamferedShape(2, 1, width - 4, height - 3, Math.max(2, chamfer - 2)));
            g.setColor(BRASS_SHADOW);
            g.draw(createChamferedShape(3, 2, width - 6, height - 5, Math.max(2, chamfer - 3)));

            final Color top;
            final Color bottom;
            switch (state) {
                case HOVER -> {
                    top = BRASS_HIGHLIGHT;
                    bottom = Color.decode("#8A5C31");
                }
                case PRESSED, TOGGLED -> {
                    top = WOOD_SHADOW;
                    bottom = Color.decode("#704A2A");
                }
                case DISABLED -> {
                    top = Color.decode("#5A5147");
                    bottom = TRAY;
                }
                default -> {
                    top = Color.decode("#A46E39");
                    bottom = WOOD_MID;
                }
            }

            final int x = 5;
            final int y = 4 + faceOffset;
            final int faceWidth = width - 10;
            final int faceHeight = Math.max(1, height - 9 - faceOffset);
            g.setPaint(new GradientPaint(0, y, top, 0, y + faceHeight, bottom));
            g.fill(createChamferedShape(x, y, faceWidth, faceHeight, Math.max(2, chamfer - 4)));

            g.setColor(BRASS_HIGHLIGHT);
            g.draw(createChamferedShape(x, y, faceWidth - 1, faceHeight - 1, Math.max(2, chamfer - 4)));
            g.setColor(new Color(255, 235, 190, state == ButtonState.DISABLED ? 25 : 90));
            g.drawLine(x + 6, y + 2, Math.max(x + 6, x + faceWidth - 7), y + 2);
            g.setColor(new Color(0, 0, 0, 125));
            g.drawLine(x + 6, y + faceHeight - 2, Math.max(x + 6, x + faceWidth - 7), y + faceHeight - 2);
        } finally {
            g.dispose();
        }
    }

    public static void paintInsetField(final Graphics2D source, final int width, final int height,
            final boolean focused) {
        if (width <= 0 || height <= 0) {
            return;
        }
        final Graphics2D g = (Graphics2D) source.create();
        try {
            applyQualityHints(g);
            g.setColor(CREVICE);
            g.fillRoundRect(0, 0, width, height, 6, 6);
            g.setColor(focused ? BRASS : FRAME_SHADOW);
            g.drawRoundRect(1, 1, width - 3, height - 3, 5, 5);
            g.setColor(getFieldColor(focused));
            g.fillRoundRect(3, 3, width - 6, height - 6, 3, 3);
            g.setColor(new Color(0, 0, 0, 100));
            g.drawLine(4, 4, Math.max(4, width - 5), 4);
        } finally {
            g.dispose();
        }
    }

    private static void tileImage(final Graphics2D g, final Image texture, final int x, final int y,
            final int width, final int height) {
        if (texture == null) {
            return;
        }
        final int textureWidth = texture.getWidth(null);
        final int textureHeight = texture.getHeight(null);
        if (textureWidth <= 0 || textureHeight <= 0) {
            return;
        }
        for (int drawX = x; drawX < x + width; drawX += textureWidth) {
            for (int drawY = y; drawY < y + height; drawY += textureHeight) {
                g.drawImage(texture, drawX, drawY, null);
            }
        }
    }

    private static void applyQualityHints(final Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    private static Shape createChamferedShape(final float x, final float y, final float width,
            final float height, final float requestedChamfer) {
        final float chamfer = Math.max(0, Math.min(requestedChamfer, Math.min(width, height) / 2f));
        final Path2D.Float path = new Path2D.Float();
        path.moveTo(x + chamfer, y);
        path.lineTo(x + width - chamfer, y);
        path.lineTo(x + width, y + chamfer);
        path.lineTo(x + width, y + height - chamfer);
        path.lineTo(x + width - chamfer, y + height);
        path.lineTo(x + chamfer, y + height);
        path.lineTo(x, y + height - chamfer);
        path.lineTo(x, y + chamfer);
        path.closePath();
        return path;
    }
}
