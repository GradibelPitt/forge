/*
 * Match-only Swing adaptation of NeoForge's arena presentation.
 * Based on AdrianLopez98/NeoForge, forge-gui-neo/neo.css and PlayerField,
 * revision 992c4bbfd8d5efe50d799e5780e721b85351f71b (GPL-3.0).
 * Distributed under the GNU GPL, version 3 or later, like Forge.
 */
package forge.screens.match;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;

/** Local board palette; deliberately does not change FSkin or lobby styling. */
public final class NeoBoardStyle {
    public static final Color BACKGROUND = new Color(0x0c1015);
    public static final Color TEXT = new Color(0xf0e9dc);
    public static final Color MUTED_TEXT = new Color(0xb0b3b8);
    public static final Color BRONZE = new Color(0xa48558);
    public static final Color ACCENT = new Color(0x66bde8);
    public static final Color DANGER = new Color(0xef7568);

    private NeoBoardStyle() { }

    public static void paintField(final Graphics2D graphics, final int width, final int height,
                                  final boolean opponent) {
        if (width <= 0 || height <= 0) { return; }
        final Graphics2D g = (Graphics2D) graphics.create();
        final float centerY = opponent ? height : 0;
        g.setPaint(new RadialGradientPaint(width * .5f, centerY,
                Math.max(1, Math.max(width * .73f, height * 1.8f)),
                new float[] {0f, .43f, .78f, 1f},
                new Color[] {new Color(0x34383a), new Color(0x20292e),
                        new Color(0x111820), new Color(0x0b1017)}));
        g.fillRect(0, 0, width, height);
        final int lineY = opponent ? height - 1 : 0;
        g.setPaint(new GradientPaint(0, lineY, new Color(179, 151, 106, 0),
                width / 2f, lineY, new Color(179, 151, 106, 110), true));
        g.fillRect(0, lineY, width, 1);
        g.dispose();
    }

    public static void paintSupport(final Graphics2D graphics, final int x, final int y,
                                    final int width, final int height, final boolean lands) {
        if (width <= 0 || height <= 0) { return; }
        final Graphics2D g = (Graphics2D) graphics.create();
        final Color tint = lands ? new Color(80, 92, 78, 46) : new Color(96, 76, 104, 46);
        g.setPaint(new GradientPaint(x, y, new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 0),
                x, y + height, tint));
        g.fillRect(x, y, width, height);
        g.setColor(lands ? new Color(176, 167, 115, 71) : new Color(173, 143, 179, 82));
        g.drawLine(x + 6, y + height - 1, x + width - 6, y + height - 1);
        g.dispose();
    }

    public static void paintPlayerBar(final Graphics2D graphics, final int width, final int height,
                                      final boolean opponent, final boolean active) {
        if (width <= 0 || height <= 0) { return; }
        final Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, active ? new Color(0x1e3f4f) : new Color(0x070c12),
                width, 0, new Color(0x0a1016)));
        g.fillRect(0, 0, width, height);
        g.setColor(active ? ACCENT : new Color(181, 150, 97, 100));
        g.fillRect(0, opponent ? height - (active ? 2 : 1) : 0, width, active ? 2 : 1);
        g.dispose();
    }
}
