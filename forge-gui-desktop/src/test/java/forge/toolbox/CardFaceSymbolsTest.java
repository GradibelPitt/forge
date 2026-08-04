package forge.toolbox;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CardFaceSymbolsTest {
    @DataProvider(name = "genericCostsWithoutSprites")
    public Object[][] genericCostsWithoutSprites() {
        return new Object[][] { { "21" }, { "100" }, { "9999" } };
    }

    @Test(dataProvider = "genericCostsWithoutSprites")
    public void rendersGenericManaCostsWithoutPrebuiltSprites(final String genericCost) {
        final BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            CardFaceSymbols.drawSymbol(genericCost, graphics, 8, 8, 48);
        } finally {
            graphics.dispose();
        }

        Assert.assertEquals(image.getRGB(8, 8) >>> 24, 0,
                "The antialiased circular symbol should leave its corner transparent");
        Assert.assertTrue(image.getRGB(32, 32) >>> 24 > 0,
                "The center of the generated generic mana symbol should be visible");
    }
}
