package forge.toolbox;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.testng.Assert;
import org.testng.annotations.Test;

public class WarmwoodThemeTest {
    @Test
    public void approvedPaletteUsesExactReferenceColors() {
        Assert.assertEquals(WarmwoodTheme.WOOD_MID, Color.decode("#64442C"));
        Assert.assertEquals(WarmwoodTheme.WOOD_SHADOW, Color.decode("#543C24"));
        Assert.assertEquals(WarmwoodTheme.FRAME_SHADOW, Color.decode("#3C2C1C"));
        Assert.assertEquals(WarmwoodTheme.CREVICE, Color.decode("#1C140C"));
        Assert.assertEquals(WarmwoodTheme.TRAY, Color.decode("#34342C"));
        Assert.assertEquals(WarmwoodTheme.METAL_EDGE, Color.decode("#544C44"));
        Assert.assertEquals(WarmwoodTheme.BRASS, Color.decode("#B07A3C"));
        Assert.assertEquals(WarmwoodTheme.TEXT, Color.decode("#F2E7D4"));
    }

    @Test
    public void warmwoodSkinNameMatchingIsStable() {
        Assert.assertTrue(WarmwoodTheme.isSkinName("Warmwood"));
        Assert.assertTrue(WarmwoodTheme.isSkinName("warmwood"));
        Assert.assertTrue(WarmwoodTheme.isSkinName(" Warmwood "));
        Assert.assertFalse(WarmwoodTheme.isSkinName("Default"));
        Assert.assertFalse(WarmwoodTheme.isSkinName(null));
    }

    @Test
    public void scalableButtonPainterCoversEveryState() {
        for (final WarmwoodTheme.ButtonState state : WarmwoodTheme.ButtonState.values()) {
            final BufferedImage image = new BufferedImage(180, 44, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D graphics = image.createGraphics();
            WarmwoodTheme.paintButton(graphics, 180, 44, state);
            graphics.dispose();

            Assert.assertNotEquals(image.getRGB(90, 22) >>> 24, 0, state + " center must be painted");
            Assert.assertEquals(new Color(image.getRGB(1, 22), true), WarmwoodTheme.CREVICE,
                    state + " outer frame must use the approved crevice color");
        }
    }

    @Test
    public void scalablePanelPainterUsesApprovedOuterFrame() {
        final BufferedImage image = new BufferedImage(240, 120, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        WarmwoodTheme.paintPanel(graphics, 240, 120, 12, null, null);
        graphics.dispose();

        Assert.assertEquals(new Color(image.getRGB(1, 60), true), WarmwoodTheme.CREVICE);
        Assert.assertNotEquals(image.getRGB(120, 60) >>> 24, 0);
    }
}
