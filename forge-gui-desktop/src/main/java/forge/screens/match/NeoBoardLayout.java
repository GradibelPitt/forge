package forge.screens.match;

/** Geometry shared by the Swing battlefield and its layout checks. */
public final class NeoBoardLayout {
    private NeoBoardLayout() { }

    /** Spread rows toward the player bar and combat line without clipping crowded boards. */
    public static int[] rowTops(final int height, final int[] rowHeights,
                                final boolean opponent, final boolean singleSupportRow,
                                final int padding) {
        final int[] tops = new int[rowHeights.length];
        if (tops.length == 0) { return tops; }
        int occupied = 0;
        for (final int rowHeight : rowHeights) { occupied += rowHeight; }
        final int free = Math.max(0, height - padding * 2 - occupied);
        int y = padding;
        if (tops.length == 1 && (opponent != singleSupportRow)) { y += free; }
        for (int i = 0; i < tops.length; i++) {
            tops[i] = y + (tops.length > 1 ? free * i / (tops.length - 1) : 0);
            y += rowHeights[i];
        }
        return tops;
    }

    public static int centeredRowLeft(final int width, final int contentWidth, final int padding) {
        return Math.max(padding, (width - contentWidth) / 2);
    }
}
