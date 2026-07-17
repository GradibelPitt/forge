package forge.gui;

import java.util.concurrent.atomic.AtomicLong;

/** Rejects asynchronous search completions from an older query or a closed dialog. */
public final class LatestSearchGeneration {
    private final AtomicLong generation = new AtomicLong();

    public long begin() {
        return generation.incrementAndGet();
    }

    public boolean isCurrent(final long candidate) {
        return generation.get() == candidate;
    }

    public void invalidate() {
        generation.incrementAndGet();
    }
}
