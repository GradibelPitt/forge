package forge.screens.match;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NeoBoardLayoutTest {
    @Test
    public void creaturesFaceTheCombatLineOnBothSides() {
        Assert.assertEquals(NeoBoardLayout.rowTops(300, new int[] {100}, false, false, 5), new int[] {5});
        Assert.assertEquals(NeoBoardLayout.rowTops(300, new int[] {100}, true, false, 5), new int[] {195});
    }

    @Test
    public void supportStaysBesideThePlayerBarWhenThereAreNoCreatures() {
        Assert.assertEquals(NeoBoardLayout.rowTops(300, new int[] {100}, false, true, 5), new int[] {195});
        Assert.assertEquals(NeoBoardLayout.rowTops(300, new int[] {100}, true, true, 5), new int[] {5});
    }

    @Test
    public void rowsSpreadWithoutOverlappingAndCrowdedBoardsRemainScrollable() {
        Assert.assertEquals(NeoBoardLayout.rowTops(300, new int[] {100, 100}, false, false, 5), new int[] {5, 195});
        Assert.assertEquals(NeoBoardLayout.rowTops(100, new int[] {80, 80, 80}, false, false, 5), new int[] {5, 85, 165});
        Assert.assertEquals(NeoBoardLayout.rowTops(0, new int[0], false, false, 5), new int[0]);
    }

    @Test
    public void centeredRowsNeverPushCardsOutsideTheLeftEdge() {
        Assert.assertEquals(NeoBoardLayout.centeredRowLeft(800, 400, 5), 200);
        Assert.assertEquals(NeoBoardLayout.centeredRowLeft(200, 400, 5), 5);
    }

    @Test
    public void fieldPainterMirrorsItsLightAndDoesNotLeakGraphicsState() {
        final BufferedImage self = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        final BufferedImage opponent = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = self.createGraphics();
        g.setColor(java.awt.Color.MAGENTA);
        NeoBoardStyle.paintField(g, 400, 200, false);
        Assert.assertEquals(g.getColor(), java.awt.Color.MAGENTA);
        g.dispose();
        final Graphics2D other = opponent.createGraphics();
        NeoBoardStyle.paintField(other, 400, 200, true);
        other.dispose();
        Assert.assertEquals(self.getRGB(200, 50), opponent.getRGB(200, 150));
        Assert.assertEquals(self.getRGB(200, 50) >>> 24, 255);
    }
}
