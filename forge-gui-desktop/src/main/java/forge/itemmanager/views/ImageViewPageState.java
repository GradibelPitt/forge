package forge.itemmanager.views;

final class ImageViewPageState {
    static final int PAGE_SIZE = IncrementalImageLoadState.BATCH_SIZE;

    private int totalCount;
    private int pageIndex;

    void reset(final int totalCount0) {
        totalCount = Math.max(0, totalCount0);
        pageIndex = 0;
    }

    boolean isPaging() {
        return totalCount > PAGE_SIZE;
    }

    int getPageNumber() {
        return pageIndex + 1;
    }

    int getPageCount() {
        return Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    int getStartOffset() {
        return pageIndex * PAGE_SIZE;
    }

    int getPageItemCount() {
        return Math.min(PAGE_SIZE, Math.max(0, totalCount - getStartOffset()));
    }

    boolean hasPreviousPage() {
        return pageIndex > 0;
    }

    boolean hasNextPage() {
        return pageIndex + 1 < getPageCount();
    }

    boolean previousPage() {
        if (!hasPreviousPage()) {
            return false;
        }
        pageIndex--;
        return true;
    }

    boolean nextPage() {
        if (!hasNextPage()) {
            return false;
        }
        pageIndex++;
        return true;
    }
}
