package forge.itemmanager.views;

final class IncrementalImageLoadState {
    static final int BATCH_SIZE = 50;

    private int totalCount;
    private int loadedCount;
    private boolean incremental;

    void reset(final int totalCount0, final boolean incremental0) {
        totalCount = Math.max(0, totalCount0);
        loadedCount = 0;
        incremental = incremental0;
    }

    int claimNextBatch() {
        final int remaining = totalCount - loadedCount;
        if (remaining <= 0) {
            return 0;
        }

        final int claimed = incremental ? Math.min(BATCH_SIZE, remaining) : remaining;
        loadedCount += claimed;
        return claimed;
    }

    int getLoadedCount() {
        return loadedCount;
    }

    int getTotalCount() {
        return totalCount;
    }

    boolean hasMore() {
        return loadedCount < totalCount;
    }

    static boolean isNearEnd(final int value, final int extent, final int maximum) {
        if (extent <= 0 || maximum <= 0) {
            return false;
        }
        return value + extent >= maximum - Math.max(1, extent / 2);
    }

    static boolean shouldLoadMore(final int previousValue, final int value,
            final int extent, final int maximum) {
        return value > previousValue && isNearEnd(value, extent, maximum);
    }
}
