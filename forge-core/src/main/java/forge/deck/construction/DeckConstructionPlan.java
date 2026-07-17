package forge.deck.construction;

import forge.deck.DeckSection;
import forge.item.PaperCard;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable plan produced without mutating its source deck. The source deck, its CardPools, context, and all writes
 * that can affect them must stay on the plan's owner thread, without concurrent bypass mutation, until commit ends.
 */
public final class DeckConstructionPlan {
    public enum Status {
        READY,
        NO_CHANGE,
        CHOICE_REQUIRED,
        BLOCKED,
        UNRESOLVED
    }

    static final class PoolEntry {
        private final PaperCard card;
        private final int count;

        PoolEntry(final PaperCard card, final int count) {
            this.card = card;
            this.count = count;
        }

        PaperCard card() {
            return card;
        }

        int count() {
            return count;
        }
    }

    /** Immutable public preview of one exact final deck-pool slot. */
    public static final class FinalPoolEntry {
        private final DeckSection section;
        private final DeckPrintingKey printingKey;
        private final int count;

        FinalPoolEntry(final DeckSection section, final PaperCard card, final int count) {
            this.section = section;
            this.printingKey = DeckPrintingKey.from(card);
            this.count = count;
        }

        public DeckSection getSection() {
            return section;
        }

        public DeckPrintingKey getPrintingKey() {
            return printingKey;
        }

        public int getCount() {
            return count;
        }
    }

    private final Status status;
    private final List<DeckConstructionIssue> issues;
    private final String sourceFingerprint;
    private final String baseFingerprint;
    private final Map<DeckSection, List<PoolEntry>> finalPools;
    private final DeckConstructionLedger finalLedger;
    private final DeckConstructionPolicy policy;
    private final List<FinalPoolEntry> finalPoolPreview;
    private final Map<DeckPrintingKey, String> exactManifest;
    private final Thread ownerThread;

    DeckConstructionPlan(final Status status, final List<DeckConstructionIssue> issues,
            final String sourceFingerprint, final String baseFingerprint,
            final Map<DeckSection, List<PoolEntry>> finalPools,
            final DeckConstructionLedger finalLedger, final DeckConstructionPolicy policy,
            final List<FinalPoolEntry> finalPoolPreview,
            final Map<DeckPrintingKey, String> exactManifest, final Thread ownerThread) {
        this.status = status;
        this.issues = List.copyOf(issues);
        this.sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
        this.baseFingerprint = baseFingerprint == null ? "" : baseFingerprint;
        final Map<DeckSection, List<PoolEntry>> pools = new EnumMap<>(DeckSection.class);
        if (finalPools != null) {
            finalPools.forEach((section, entries) -> pools.put(section, List.copyOf(entries)));
        }
        this.finalPools = Collections.unmodifiableMap(pools);
        this.finalLedger = finalLedger == null ? null : finalLedger.copy();
        this.policy = policy == null ? DeckConstructionPolicy.empty() : policy;
        this.finalPoolPreview = finalPoolPreview == null ? List.of() : List.copyOf(finalPoolPreview);
        final Map<DeckPrintingKey, String> manifest = new TreeMap<>();
        if (exactManifest != null) {
            exactManifest.forEach((printing, signature) -> manifest.put(printing.copy(), signature));
        }
        this.exactManifest = Collections.unmodifiableMap(manifest);
        this.ownerThread = ownerThread;
    }

    public Status getStatus() {
        return status;
    }

    public List<DeckConstructionIssue> getIssues() {
        return issues;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }

    public DeckConstructionPolicy getPolicy() {
        return policy;
    }

    public List<FinalPoolEntry> getFinalPool() {
        return finalPoolPreview;
    }

    public Map<DeckPrintingKey, String> getExactManifest() {
        return exactManifest;
    }

    public boolean canCommit() {
        return status == Status.READY || status == Status.NO_CHANGE;
    }

    String baseFingerprint() {
        return baseFingerprint;
    }

    Map<DeckSection, List<PoolEntry>> finalPools() {
        return finalPools;
    }

    DeckConstructionLedger finalLedger() {
        return finalLedger;
    }

    Thread ownerThread() {
        return ownerThread;
    }
}
