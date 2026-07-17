package forge.deck.construction;

import forge.deck.DeckSection;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Immutable, CardDb-independent ownership ledger for construction-managed deck entries.
 *
 * <p>The line codec is deliberately bounded and fail-closed. A malformed section is represented by an empty
 * {@link Status#UNRESOLVED} ledger; it never changes a deck's card pools.</p>
 */
public final class DeckConstructionLedger implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final int CURRENT_VERSION = 1;
    public static final String MARKER_PREFIX = "__forge_construction_v1:";
    public static final int MAX_LINES = 100_000;
    public static final int MAX_LINE_LENGTH = 1_048_576;
    public static final long MAX_SECTION_CHARACTERS = 16L * 1_024L * 1_024L;
    public static final int MAX_ENTRIES = 100_000;
    public static final int MAX_CONTRIBUTIONS_PER_ENTRY = 10_000;
    public static final int MAX_TOTAL_MANAGED_COUNT = 100_000;
    public static final int MAX_TOTAL_MANUAL_COUNT = 100_000;
    public static final int MAX_TOTAL_CARD_COUNT = MAX_TOTAL_MANUAL_COUNT + MAX_TOTAL_MANAGED_COUNT;
    public static final int MAX_SLOT_CARD_COUNT = Integer.MAX_VALUE;
    public static final int MAX_LEDGER_ID_LENGTH = 128;
    public static final int MAX_REASON_LENGTH = 4_096;

    private static final int MAX_SOURCE_NAME_LENGTH = 4_096;
    private static final int MAX_RULE_ID_LENGTH = 1_024;
    private static final int MAX_FINGERPRINT_LENGTH = 4_096;
    private static final int HEADER_LINE_COUNT = 4;

    public enum Status {
        TRACKED,
        UNTRACKED,
        ORPHANED,
        UNRESOLVED
    }

    /**
     * Stable identity of one managed contribution. Schema and normalized fingerprint are explicit so callers can
     * distinguish a stale rule revision from the rule that originally contributed the cards.
     */
    public static final class ContributionKey implements Comparable<ContributionKey>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String sourceCanonicalName;
        private final String ruleId;
        private final int contributionRevision;
        private final int ruleSchemaVersion;
        private final String ruleFingerprint;

        public ContributionKey(final String sourceCanonicalName, final String ruleId, final int contributionRevision,
                               final int ruleSchemaVersion, final String ruleFingerprint) {
            final DeckConstructionRule.RuleKey normalizedRuleKey =
                    DeckConstructionRule.RuleKey.of(sourceCanonicalName, ruleId);
            this.sourceCanonicalName = requireField(normalizedRuleKey.getSourceCanonicalName(),
                    "canonical source name", MAX_SOURCE_NAME_LENGTH, false);
            this.ruleId = requireField(normalizedRuleKey.getRuleId(), "normalized ruleId",
                    MAX_RULE_ID_LENGTH, false);
            if (contributionRevision < 0) {
                throw new IllegalArgumentException("contribution revision must not be negative");
            }
            if (ruleSchemaVersion < 1) {
                throw new IllegalArgumentException("rule schema version must be positive");
            }
            this.contributionRevision = contributionRevision;
            this.ruleSchemaVersion = ruleSchemaVersion;
            final String fingerprint = requireField(ruleFingerprint, "ruleFingerprint",
                    MAX_FINGERPRINT_LENGTH, false).trim().toLowerCase(Locale.ROOT);
            if (fingerprint.isEmpty()) {
                throw new IllegalArgumentException("rule fingerprint must not be blank");
            }
            this.ruleFingerprint = requireField(fingerprint, "normalized ruleFingerprint",
                    MAX_FINGERPRINT_LENGTH, false);
        }

        public String getSourceCanonicalName() {
            return sourceCanonicalName;
        }

        public String getRuleId() {
            return ruleId;
        }

        /**
         * Explicit migration generation for this source/rule contribution. Schema/fingerprint changes do not
         * increment it automatically; schema version 1 planning uses zero until a future migration API opts in.
         */
        public int getContributionRevision() {
            return contributionRevision;
        }

        /**
         * Compatibility alias for callers written before the migration-generation name was made explicit.
         */
        public int getRevision() {
            return getContributionRevision();
        }

        public int getRuleSchemaVersion() {
            return ruleSchemaVersion;
        }

        public String getRuleFingerprint() {
            return ruleFingerprint;
        }

        public ContributionKey copy() {
            return this; // Deeply immutable leaf; sharing it does not share a mutable container.
        }

        @Override
        public int compareTo(final ContributionKey other) {
            int result = sourceCanonicalName.compareTo(other.sourceCanonicalName);
            if (result != 0) {
                return result;
            }
            result = ruleId.compareTo(other.ruleId);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(contributionRevision, other.contributionRevision);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(ruleSchemaVersion, other.ruleSchemaVersion);
            if (result != 0) {
                return result;
            }
            return ruleFingerprint.compareTo(other.ruleFingerprint);
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ContributionKey other)) {
                return false;
            }
            return contributionRevision == other.contributionRevision
                    && ruleSchemaVersion == other.ruleSchemaVersion
                    && sourceCanonicalName.equals(other.sourceCanonicalName)
                    && ruleId.equals(other.ruleId)
                    && ruleFingerprint.equals(other.ruleFingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceCanonicalName, ruleId, contributionRevision, ruleSchemaVersion,
                    ruleFingerprint);
        }
    }

    /**
     * One exact target printing in one deck section, split into user-owned and rule-managed quantities.
     */
    public static final class Entry implements Comparable<Entry>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final DeckSection section;
        private final DeckPrintingKey printingKey;
        private final int manualCount;
        private final SortedMap<ContributionKey, Integer> managedCounts;
        private final int managedCount;
        private final int totalCount;

        public Entry(final DeckSection section, final DeckPrintingKey printingKey, final int manualCount,
                     final Map<ContributionKey, Integer> managedCounts) {
            this.section = Objects.requireNonNull(section, "section");
            this.printingKey = Objects.requireNonNull(printingKey, "printingKey").copy();
            if (manualCount < 0) {
                throw new IllegalArgumentException("manual count must not be negative");
            }
            this.manualCount = manualCount;
            if (managedCounts == null || managedCounts.size() > MAX_CONTRIBUTIONS_PER_ENTRY) {
                throw new IllegalArgumentException("managed contributions are null or exceed the per-entry limit");
            }
            final SortedMap<ContributionKey, Integer> copiedCounts = new TreeMap<>();
            long managedTotal = 0;
            for (Map.Entry<ContributionKey, Integer> managed : managedCounts.entrySet()) {
                final ContributionKey key = Objects.requireNonNull(managed.getKey(), "managed contribution key").copy();
                final Integer count = Objects.requireNonNull(managed.getValue(), "managed contribution count");
                if (count <= 0) {
                    throw new IllegalArgumentException("managed contribution count must be positive");
                }
                if (copiedCounts.put(key, count) != null) {
                    throw new IllegalArgumentException("duplicate managed contribution identity");
                }
                managedTotal = checkedAdd(managedTotal, count, MAX_SLOT_CARD_COUNT,
                        "managed count exceeds the per-entry limit");
            }
            final long slotTotal = checkedAdd(manualCount, managedTotal, MAX_SLOT_CARD_COUNT,
                    "combined entry count exceeds Integer.MAX_VALUE");
            if (slotTotal == 0) {
                throw new IllegalArgumentException("construction ledger entry must own at least one card");
            }
            this.managedCounts = Collections.unmodifiableSortedMap(copiedCounts);
            this.managedCount = checkedInt(managedTotal, "managed count exceeds Integer.MAX_VALUE");
            this.totalCount = checkedInt(slotTotal, "combined entry count exceeds Integer.MAX_VALUE");
        }

        public DeckSection getSection() {
            return section;
        }

        public DeckPrintingKey getPrintingKey() {
            return printingKey;
        }

        public int getManualCount() {
            return manualCount;
        }

        public SortedMap<ContributionKey, Integer> getManagedCounts() {
            return managedCounts;
        }

        public int getManagedCount() {
            return managedCount;
        }

        public int getManagedCount(final ContributionKey key) {
            return managedCounts.getOrDefault(key, 0);
        }

        public int getTotalCount() {
            return totalCount;
        }

        public Entry withManualCount(final int count) {
            return new Entry(section, printingKey, count, managedCounts);
        }

        public Entry withManagedCount(final ContributionKey key, final int count) {
            Objects.requireNonNull(key, "key");
            if (count < 0) {
                throw new IllegalArgumentException("managed contribution count must not be negative");
            }
            final Map<ContributionKey, Integer> changed = new TreeMap<>(managedCounts);
            if (count == 0) {
                changed.remove(key);
            } else {
                changed.put(key, count);
            }
            return new Entry(section, printingKey, manualCount, changed);
        }

        public Entry withoutManagedCount(final ContributionKey key) {
            return withManagedCount(key, 0);
        }

        public Entry copy() {
            return new Entry(section, printingKey, manualCount, managedCounts);
        }

        private SlotKey slotKey() {
            return new SlotKey(section, printingKey);
        }

        @Override
        public int compareTo(final Entry other) {
            return slotKey().compareTo(other.slotKey());
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Entry other)) {
                return false;
            }
            return manualCount == other.manualCount
                    && section == other.section
                    && printingKey.equals(other.printingKey)
                    && managedCounts.equals(other.managedCounts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(section, printingKey, manualCount, managedCounts);
        }
    }

    /**
     * Pool identity for an entry. The diagnostics-only functional variant is excluded through DeckPrintingKey's
     * equality and ordering contract.
     */
    public static final class SlotKey implements Comparable<SlotKey>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final DeckSection section;
        private final DeckPrintingKey printingKey;

        public SlotKey(final DeckSection section, final DeckPrintingKey printingKey) {
            this.section = Objects.requireNonNull(section, "section");
            this.printingKey = Objects.requireNonNull(printingKey, "printingKey").copy();
        }

        public DeckSection getSection() {
            return section;
        }

        public DeckPrintingKey getPrintingKey() {
            return printingKey;
        }

        @Override
        public int compareTo(final SlotKey other) {
            final int sectionResult = Integer.compare(section.ordinal(), other.section.ordinal());
            return sectionResult != 0 ? sectionResult : printingKey.compareTo(other.printingKey);
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SlotKey other)) {
                return false;
            }
            return section == other.section && printingKey.equals(other.printingKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(section, printingKey);
        }
    }

    private final int version;
    private final String ledgerId;
    private final Status status;
    private final String reason;
    private final SortedMap<SlotKey, Entry> entries;
    private final long encodedCharacterCount;

    private DeckConstructionLedger(final int version, final String ledgerId, final Status status,
                                   final String reason, final Iterable<Entry> entries) {
        this(version, ledgerId, status, reason, entries, MAX_SECTION_CHARACTERS);
    }

    private DeckConstructionLedger(final int version, final String ledgerId, final Status status,
                                   final String reason, final Iterable<Entry> entries,
                                   final long maximumEncodedCharacters) {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported construction ledger version");
        }
        this.version = version;
        this.ledgerId = requireLedgerId(ledgerId);
        this.status = Objects.requireNonNull(status, "status");
        this.reason = requireField(reason == null ? "" : reason, "reason", MAX_REASON_LENGTH, true);
        final SortedMap<SlotKey, Entry> copiedEntries = new TreeMap<>();
        long manualTotal = 0;
        long managedTotal = 0;
        long contributionIdentities = 0;
        long minimumEncodedCharacters = estimateHeaderCharacters(maximumEncodedCharacters);
        for (Entry entry : Objects.requireNonNull(entries, "entries")) {
            final Entry source = Objects.requireNonNull(entry, "entry");
            minimumEncodedCharacters = addMinimumEntryCharacters(minimumEncodedCharacters, source,
                    maximumEncodedCharacters);
            final SlotKey slot = source.slotKey();
            if (copiedEntries.containsKey(slot)) {
                throw new IllegalArgumentException("duplicate construction ledger slot");
            }
            final Entry copy = source.copy();
            copiedEntries.put(slot, copy);
            if (copiedEntries.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("construction ledger entry count exceeds the limit");
            }
            manualTotal = checkedAdd(manualTotal, copy.getManualCount(), MAX_TOTAL_MANUAL_COUNT,
                    "construction ledger manual count exceeds the limit");
            managedTotal = checkedAdd(managedTotal, copy.getManagedCount(), MAX_TOTAL_MANAGED_COUNT,
                    "construction ledger managed count exceeds the limit");
            contributionIdentities = checkedAdd(contributionIdentities, copy.getManagedCounts().size(),
                    MAX_LINES, "construction ledger contribution identity count exceeds the limit");
        }
        final long encodedLineCount = checkedAdd(HEADER_LINE_COUNT, copiedEntries.size(), MAX_LINES,
                "construction ledger encoded line count exceeds the limit");
        checkedAdd(encodedLineCount, contributionIdentities, MAX_LINES,
                "construction ledger encoded line count exceeds the limit");
        checkedAdd(manualTotal, managedTotal, MAX_TOTAL_CARD_COUNT,
                "construction ledger total card count exceeds the limit");
        this.entries = Collections.unmodifiableSortedMap(copiedEntries);
        this.encodedCharacterCount = estimateEncodedCharacters(maximumEncodedCharacters);
    }

    public static DeckConstructionLedger tracked() {
        return tracked(newLedgerId());
    }

    public static DeckConstructionLedger tracked(final String ledgerId) {
        return new DeckConstructionLedger(CURRENT_VERSION, ledgerId, Status.TRACKED, "", List.of());
    }

    /**
     * Consumes {@code entries} once, then performs bounded canonical TreeMap ordering. Bulk construction is
     * O(n log n), with n capped by {@link #MAX_ENTRIES} and the encoded ledger capped by
     * {@link #MAX_SECTION_CHARACTERS}; use it instead of repeated {@link #withEntry(Entry)} calls, which would
     * repeatedly rebuild the immutable container.
     */
    public static DeckConstructionLedger tracked(final String ledgerId, final Iterable<Entry> entries) {
        return new DeckConstructionLedger(CURRENT_VERSION, ledgerId, Status.TRACKED, "",
                Objects.requireNonNull(entries, "entries"));
    }

    static DeckConstructionLedger trackedWithBudgetForTest(final String ledgerId,
                                                           final Iterable<Entry> entries,
                                                           final long maximumEncodedCharacters) {
        return new DeckConstructionLedger(CURRENT_VERSION, ledgerId, Status.TRACKED, "",
                Objects.requireNonNull(entries, "entries"), maximumEncodedCharacters);
    }

    public static DeckConstructionLedger untracked() {
        return untracked(newLedgerId());
    }

    public static DeckConstructionLedger untracked(final String ledgerId) {
        return new DeckConstructionLedger(CURRENT_VERSION, ledgerId, Status.UNTRACKED, "", List.of());
    }

    public static DeckConstructionLedger orphaned(final String ledgerId, final String reason) {
        return new DeckConstructionLedger(CURRENT_VERSION, validOrNewLedgerId(ledgerId), Status.ORPHANED,
                boundedReason(reason), List.of());
    }

    public static DeckConstructionLedger unresolved(final String ledgerId, final String reason) {
        return new DeckConstructionLedger(CURRENT_VERSION, validOrNewLedgerId(ledgerId), Status.UNRESOLVED,
                boundedReason(reason), List.of());
    }

    public int getVersion() {
        return version;
    }

    public String getLedgerId() {
        return ledgerId;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public List<Entry> getEntries() {
        return List.copyOf(entries.values());
    }

    public Optional<Entry> getEntry(final DeckSection section, final DeckPrintingKey printingKey) {
        return Optional.ofNullable(entries.get(new SlotKey(section, printingKey)));
    }

    public DeckConstructionLedger withEntry(final Entry entry) {
        final Map<SlotKey, Entry> changed = new TreeMap<>(entries);
        changed.put(entry.slotKey(), entry);
        return new DeckConstructionLedger(version, ledgerId, status, reason, changed.values());
    }

    /**
     * Replaces or adds a batch while consuming the caller's iterable exactly once. The bounded operation is
     * O((n + m) log(n + m)) because canonical slot ordering is required; duplicate slots inside the batch are
     * rejected rather than silently using the last value.
     */
    public DeckConstructionLedger withEntries(final Iterable<Entry> replacementEntries) {
        Objects.requireNonNull(replacementEntries, "replacementEntries");
        final Map<SlotKey, Entry> changed = new TreeMap<>(entries);
        final Map<SlotKey, Boolean> incomingSlots = new HashMap<>();
        long incomingMinimumCharacters = 0;
        for (Entry entry : replacementEntries) {
            final Entry nonNullEntry = Objects.requireNonNull(entry, "entry");
            incomingMinimumCharacters = addMinimumEntryCharacters(incomingMinimumCharacters, nonNullEntry,
                    MAX_SECTION_CHARACTERS);
            final SlotKey slot = nonNullEntry.slotKey();
            if (incomingSlots.put(slot, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("duplicate construction ledger slot in replacement batch");
            }
            changed.put(slot, nonNullEntry);
        }
        return new DeckConstructionLedger(version, ledgerId, status, reason, changed.values());
    }

    public DeckConstructionLedger withoutEntry(final DeckSection section, final DeckPrintingKey printingKey) {
        final Map<SlotKey, Entry> changed = new TreeMap<>(entries);
        changed.remove(new SlotKey(section, printingKey));
        return new DeckConstructionLedger(version, ledgerId, status, reason, changed.values());
    }

    public DeckConstructionLedger withStatus(final Status newStatus, final String newReason) {
        return new DeckConstructionLedger(version, ledgerId, newStatus, boundedReason(newReason), entries.values());
    }

    public DeckConstructionLedger copy() {
        return new DeckConstructionLedger(version, ledgerId, status, reason, entries.values());
    }

    /**
     * Equality used by Deck dirty-state checks. The transport marker id does not make two otherwise identical decks
     * different, while status, reason, schema version, entries, and managed contribution fingerprints do.
     */
    public boolean semanticallyEquals(final DeckConstructionLedger other) {
        return other != null
                && version == other.version
                && status == other.status
                && reason.equals(other.reason)
                && entries.equals(other.entries);
    }

    /**
     * Deterministic versioned representation for the {@code [construction]} deck section.
     */
    public List<String> toLines() {
        int lineCount = HEADER_LINE_COUNT + entries.size();
        for (Entry entry : entries.values()) {
            lineCount = Math.addExact(lineCount, entry.getManagedCounts().size());
        }
        final List<String> lines = new ArrayList<>(lineCount);
        long writtenCharacters = 0;
        writtenCharacters = appendEncodedLine(lines, "version=" + version, writtenCharacters);
        writtenCharacters = appendEncodedLine(lines, "id=" + encodeField(ledgerId), writtenCharacters);
        writtenCharacters = appendEncodedLine(lines, "status=" + status.name(), writtenCharacters);
        writtenCharacters = appendEncodedLine(lines, "reason=" + encodeField(reason), writtenCharacters);
        int index = 0;
        for (Entry entry : entries.values()) {
            writtenCharacters = appendEncodedLine(lines,
                    "entry=" + index + "|" + encodeField(entry.getSection().name()) + "|"
                            + entry.getPrintingKey().encode() + "|" + entry.getManualCount(),
                    writtenCharacters);
            for (Map.Entry<ContributionKey, Integer> managed : entry.getManagedCounts().entrySet()) {
                final ContributionKey key = managed.getKey();
                writtenCharacters = appendEncodedLine(lines,
                        "managed=" + index + "|" + encodeField(key.getSourceCanonicalName()) + "|"
                                + encodeField(key.getRuleId()) + "|" + key.getContributionRevision() + "|"
                                + key.getRuleSchemaVersion() + "|" + encodeField(key.getRuleFingerprint()) + "|"
                                + managed.getValue(), writtenCharacters);
            }
            index++;
        }
        if (writtenCharacters != encodedCharacterCount) {
            throw new IllegalStateException("construction encoding differs from its checked size estimate");
        }
        return Collections.unmodifiableList(lines);
    }

    long estimateEncodedCharacters(final long maximumEncodedCharacters) {
        if (maximumEncodedCharacters < 0 || maximumEncodedCharacters > MAX_SECTION_CHARACTERS) {
            throw new IllegalArgumentException("construction encoding budget is outside supported bounds");
        }
        long total = estimateHeaderCharacters(maximumEncodedCharacters);
        int index = 0;
        for (Entry entry : entries.values()) {
            long entryLine = checkedLength("entry=".length(), decimalLength(index));
            entryLine = checkedLength(entryLine, 1L, encodedFieldLength(entry.getSection().name()), 1L,
                    entry.getPrintingKey().encodedLength(), 1L, decimalLength(entry.getManualCount()));
            total = addEstimatedLine(total, entryLine, maximumEncodedCharacters);
            for (Map.Entry<ContributionKey, Integer> managed : entry.getManagedCounts().entrySet()) {
                final ContributionKey key = managed.getKey();
                long managedLine = checkedLength("managed=".length(), decimalLength(index));
                managedLine = checkedLength(managedLine, 1L, encodedFieldLength(key.getSourceCanonicalName()),
                        1L, encodedFieldLength(key.getRuleId()), 1L,
                        decimalLength(key.getContributionRevision()), 1L,
                        decimalLength(key.getRuleSchemaVersion()), 1L,
                        encodedFieldLength(key.getRuleFingerprint()), 1L, decimalLength(managed.getValue()));
                total = addEstimatedLine(total, managedLine, maximumEncodedCharacters);
            }
            index++;
        }
        return total;
    }

    /**
     * Parses untrusted section lines. Any malformed, duplicated, oversized, or unsupported content becomes an empty
     * unresolved ledger. {@code fallbackLedgerId} is used only when the section id itself cannot be decoded.
     */
    public static DeckConstructionLedger parseLines(final List<String> lines, final String fallbackLedgerId) {
        String detectedLedgerId = validOrNewLedgerId(fallbackLedgerId);
        try {
            validateSectionShape(lines);
            detectedLedgerId = detectLedgerId(lines, detectedLedgerId);
            return parseLinesStrict(lines);
        } catch (RuntimeException ex) {
            return unresolved(detectedLedgerId, "Invalid construction section: " + safeMessage(ex));
        }
    }

    public static boolean isValidLedgerId(final String ledgerId) {
        if (ledgerId == null || ledgerId.isEmpty() || ledgerId.length() > MAX_LEDGER_ID_LENGTH) {
            return false;
        }
        for (int i = 0; i < ledgerId.length(); i++) {
            final char c = ledgerId.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == ':')) {
                return false;
            }
        }
        return true;
    }

    public static String newLedgerId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DeckConstructionLedger other)) {
            return false;
        }
        return version == other.version
                && ledgerId.equals(other.ledgerId)
                && status == other.status
                && reason.equals(other.reason)
                && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, ledgerId, status, reason, entries);
    }

    private static DeckConstructionLedger parseLinesStrict(final List<String> lines) {
        String rawVersion = null;
        String rawId = null;
        String rawStatus = null;
        String rawReason = null;
        final Map<Integer, PendingEntry> pendingEntries = new HashMap<>();
        final Map<SlotKey, Integer> slotIndexes = new HashMap<>();
        final Map<Integer, Map<ContributionKey, Integer>> pendingManaged = new HashMap<>();

        for (String line : lines) {
            final int equalsIndex = line.indexOf('=');
            if (equalsIndex <= 0) {
                throw new IllegalArgumentException("construction line has no field separator");
            }
            final String field = line.substring(0, equalsIndex);
            final String value = line.substring(equalsIndex + 1);
            switch (field) {
            case "version":
                if (rawVersion != null) {
                    throw new IllegalArgumentException("duplicate construction version");
                }
                rawVersion = value;
                break;
            case "id":
                if (rawId != null) {
                    throw new IllegalArgumentException("duplicate construction ledger id");
                }
                rawId = value;
                break;
            case "status":
                if (rawStatus != null) {
                    throw new IllegalArgumentException("duplicate construction status");
                }
                rawStatus = value;
                break;
            case "reason":
                if (rawReason != null) {
                    throw new IllegalArgumentException("duplicate construction reason");
                }
                rawReason = value;
                break;
            case "entry":
                parseEntry(value, pendingEntries, slotIndexes);
                break;
            case "managed":
                parseManaged(value, pendingManaged);
                break;
            default:
                throw new IllegalArgumentException("unknown construction field");
            }
        }

        if (rawVersion == null || rawId == null || rawStatus == null || rawReason == null) {
            throw new IllegalArgumentException("construction section is missing a required header");
        }
        final int version = parseBoundedInt(rawVersion, 0, Integer.MAX_VALUE, "version");
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported construction version");
        }
        final String ledgerId = requireLedgerId(decodeField(rawId, MAX_LEDGER_ID_LENGTH, false));
        final Status status;
        try {
            status = Status.valueOf(rawStatus);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown construction status", ex);
        }
        final String reason = decodeField(rawReason, MAX_REASON_LENGTH, true);
        if (pendingEntries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("construction entry count exceeds the limit");
        }
        for (Integer index : pendingManaged.keySet()) {
            if (!pendingEntries.containsKey(index)) {
                throw new IllegalArgumentException("managed contribution references a missing entry");
            }
        }
        final List<Entry> entries = new ArrayList<>(pendingEntries.size());
        for (int index = 0; index < pendingEntries.size(); index++) {
            final PendingEntry pending = pendingEntries.get(index);
            if (pending == null) {
                throw new IllegalArgumentException("construction entry indexes are not contiguous");
            }
            entries.add(new Entry(pending.section, pending.printingKey, pending.manualCount,
                    pendingManaged.getOrDefault(index, Map.of())));
        }
        final DeckConstructionLedger parsed = new DeckConstructionLedger(version, ledgerId, status, reason, entries);
        // Both lists are bounded by MAX_SECTION_CHARACTERS. Keeping this final canonical comparison here prevents
        // alternate header/entry orderings from becoming a second external representation of the same ledger.
        if (!parsed.toLines().equals(lines)) {
            throw new IllegalArgumentException("construction section is not in canonical order or encoding");
        }
        return parsed;
    }

    private static void parseEntry(final String value, final Map<Integer, PendingEntry> pendingEntries,
                                   final Map<SlotKey, Integer> slotIndexes) {
        final String[] fields = value.split("\\|", -1);
        if (fields.length != 4) {
            throw new IllegalArgumentException("construction entry has the wrong field count");
        }
        final int index = parseBoundedInt(fields[0], 0, MAX_ENTRIES - 1, "entry index");
        final DeckSection section;
        try {
            section = DeckSection.valueOf(decodeField(fields[1], 128, false));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown deck section in construction entry", ex);
        }
        final DeckPrintingKey printingKey = DeckPrintingKey.decode(fields[2]);
        final int manualCount = parseBoundedInt(fields[3], 0, Integer.MAX_VALUE, "manual count");
        final PendingEntry pending = new PendingEntry(section, printingKey, manualCount);
        if (pendingEntries.put(index, pending) != null) {
            throw new IllegalArgumentException("duplicate construction entry index");
        }
        final SlotKey slot = new SlotKey(section, printingKey);
        if (slotIndexes.put(slot, index) != null) {
            throw new IllegalArgumentException("duplicate construction ledger slot");
        }
    }

    private static void parseManaged(final String value,
                                     final Map<Integer, Map<ContributionKey, Integer>> pendingManaged) {
        final String[] fields = value.split("\\|", -1);
        if (fields.length != 7) {
            throw new IllegalArgumentException("managed contribution has the wrong field count");
        }
        final int index = parseBoundedInt(fields[0], 0, MAX_ENTRIES - 1, "managed entry index");
        final String sourceName = decodeField(fields[1], MAX_SOURCE_NAME_LENGTH, false);
        final String ruleId = decodeField(fields[2], MAX_RULE_ID_LENGTH, false);
        final int revision = parseBoundedInt(fields[3], 0, Integer.MAX_VALUE, "rule revision");
        final int schemaVersion = parseBoundedInt(fields[4], 1, Integer.MAX_VALUE, "rule schema version");
        final String fingerprint = decodeField(fields[5], MAX_FINGERPRINT_LENGTH, false);
        final int count = parseBoundedInt(fields[6], 1, Integer.MAX_VALUE, "managed count");
        final ContributionKey contribution = new ContributionKey(sourceName, ruleId, revision,
                schemaVersion, fingerprint);
        if (!sourceName.equals(contribution.getSourceCanonicalName())
                || !ruleId.equals(contribution.getRuleId())
                || !fingerprint.equals(contribution.getRuleFingerprint())) {
            throw new IllegalArgumentException("managed contribution identity is not canonically normalized");
        }
        final Map<ContributionKey, Integer> counts = pendingManaged.computeIfAbsent(index,
                ignored -> new LinkedHashMap<>());
        if (counts.size() >= MAX_CONTRIBUTIONS_PER_ENTRY || counts.put(contribution, count) != null) {
            throw new IllegalArgumentException("duplicate or excessive managed contribution");
        }
    }

    private static String detectLedgerId(final List<String> lines, final String fallback) {
        String detected = fallback;
        boolean found = false;
        for (String line : lines) {
            if (!line.startsWith("id=")) {
                continue;
            }
            if (found) {
                return fallback;
            }
            found = true;
            try {
                final String candidate = decodeField(line.substring(3), MAX_LEDGER_ID_LENGTH, false);
                if (isValidLedgerId(candidate)) {
                    detected = candidate;
                }
            } catch (RuntimeException ignored) {
                // The strict parser reports the error; this pass only preserves a safe id for diagnostics.
            }
        }
        return detected;
    }

    private static void validateSectionShape(final List<String> lines) {
        if (lines == null || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("construction section line count exceeds the limit");
        }
        long totalCharacters = 0;
        for (String line : lines) {
            if (line == null || line.length() > MAX_LINE_LENGTH) {
                throw new IllegalArgumentException("construction line exceeds the size limit");
            }
            totalCharacters = checkedAdd(totalCharacters, line.length(), MAX_SECTION_CHARACTERS,
                    "construction section exceeds the total size limit");
        }
    }

    private long estimateHeaderCharacters(final long maximumEncodedCharacters) {
        if (maximumEncodedCharacters < 0 || maximumEncodedCharacters > MAX_SECTION_CHARACTERS) {
            throw new IllegalArgumentException("construction encoding budget is outside supported bounds");
        }
        long total = 0;
        total = addEstimatedLine(total,
                checkedLength("version=".length(), decimalLength(version)), maximumEncodedCharacters);
        total = addEstimatedLine(total,
                checkedLength("id=".length(), encodedFieldLength(ledgerId)), maximumEncodedCharacters);
        total = addEstimatedLine(total,
                checkedLength("status=".length(), status.name().length()), maximumEncodedCharacters);
        return addEstimatedLine(total,
                checkedLength("reason=".length(), encodedFieldLength(reason)), maximumEncodedCharacters);
    }

    private static long addMinimumEntryCharacters(final long currentCharacters, final Entry entry,
                                                  final long maximumEncodedCharacters) {
        long entryLine = checkedLength("entry=".length(), 1L, 1L,
                encodedFieldLength(entry.getSection().name()), 1L, entry.getPrintingKey().encodedLength(),
                1L, decimalLength(entry.getManualCount()));
        long total = addEstimatedLine(currentCharacters, entryLine, maximumEncodedCharacters);
        for (Map.Entry<ContributionKey, Integer> managed : entry.getManagedCounts().entrySet()) {
            final ContributionKey key = managed.getKey();
            final long managedLine = checkedLength("managed=".length(), 1L, 1L,
                    encodedFieldLength(key.getSourceCanonicalName()), 1L, encodedFieldLength(key.getRuleId()),
                    1L, decimalLength(key.getContributionRevision()), 1L,
                    decimalLength(key.getRuleSchemaVersion()), 1L,
                    encodedFieldLength(key.getRuleFingerprint()), 1L, decimalLength(managed.getValue()));
            total = addEstimatedLine(total, managedLine, maximumEncodedCharacters);
        }
        return total;
    }

    private long appendEncodedLine(final List<String> lines, final String line, final long currentCharacters) {
        if (line.length() > MAX_LINE_LENGTH) {
            throw new IllegalArgumentException("construction line exceeds the size limit");
        }
        final long updatedCharacters = checkedAdd(currentCharacters, line.length(), encodedCharacterCount,
                "construction section exceeds its checked encoding budget");
        lines.add(line);
        return updatedCharacters;
    }

    private static long addEstimatedLine(final long currentCharacters, final long lineCharacters,
                                         final long maximumEncodedCharacters) {
        if (lineCharacters < 0 || lineCharacters > MAX_LINE_LENGTH) {
            throw new IllegalArgumentException("construction line exceeds the size limit");
        }
        return checkedAdd(currentCharacters, lineCharacters, maximumEncodedCharacters,
                "construction section exceeds the total size limit");
    }

    private static long checkedLength(final long... parts) {
        long length = 0;
        try {
            for (long part : parts) {
                if (part < 0) {
                    throw new IllegalArgumentException("encoded length component must not be negative");
                }
                length = Math.addExact(length, part);
            }
            return length;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("construction encoded length overflow", ex);
        }
    }

    private static long encodedFieldLength(final String value) {
        return DeckPrintingKey.base64UrlLength(value.getBytes(StandardCharsets.UTF_8).length);
    }

    private static int decimalLength(final int value) {
        if (value < 0) {
            throw new IllegalArgumentException("construction numeric field must not be negative");
        }
        if (value < 10) {
            return 1;
        }
        int remaining = value;
        int digits = 0;
        while (remaining > 0) {
            remaining /= 10;
            digits++;
        }
        return digits;
    }

    private static String encodeField(final String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeField(final String encoded, final int maxCharacters, final boolean allowEmpty) {
        if (encoded == null || encoded.length() > Math.multiplyExact(maxCharacters, 6)) {
            throw new IllegalArgumentException("encoded construction field exceeds the size limit");
        }
        try {
            final byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded)
                    || bytes.length > Math.multiplyExact(maxCharacters, 4)) {
                throw new IllegalArgumentException("construction field is not canonical Base64URL");
            }
            final String value;
            try {
                value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException ex) {
                throw new IllegalArgumentException("construction field is not valid UTF-8", ex);
            }
            return requireField(value, "decoded construction field", maxCharacters, allowEmpty);
        } catch (IllegalArgumentException ex) {
            throw ex;
        }
    }

    private static int parseBoundedInt(final String raw, final int minimum, final int maximum,
                                       final String field) {
        try {
            final long parsed = Long.parseLong(raw);
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException(field + " exceeds the numeric limit");
            }
            final int value = Math.toIntExact(parsed);
            if (!Integer.toString(value).equals(raw)) {
                throw new IllegalArgumentException(field + " is not in canonical decimal form");
            }
            return value;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException(field + " is not a bounded integer", ex);
        }
    }

    private static String requireLedgerId(final String ledgerId) {
        if (!isValidLedgerId(ledgerId)) {
            throw new IllegalArgumentException("construction ledger id is invalid");
        }
        return ledgerId;
    }

    private static String validOrNewLedgerId(final String candidate) {
        return isValidLedgerId(candidate) ? candidate : newLedgerId();
    }

    private static String requireField(final String value, final String field, final int maxLength,
                                       final boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.isEmpty()) || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is empty or exceeds the size limit");
        }
        return value;
    }

    private static long checkedAdd(final long left, final long right, final long maximum,
                                   final String message) {
        if (left < 0 || right < 0 || left > maximum - right) {
            throw new IllegalArgumentException(message);
        }
        return left + right;
    }

    private static int checkedInt(final long value, final String message) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }

    private static String boundedReason(final String reason) {
        final String value = reason == null ? "" : reason;
        return value.length() <= MAX_REASON_LENGTH ? value : value.substring(0, MAX_REASON_LENGTH);
    }

    private static String safeMessage(final RuntimeException exception) {
        final String message = exception.getMessage();
        return boundedReason(message == null || message.isBlank() ? exception.getClass().getSimpleName() : message);
    }

    private static final class PendingEntry {
        private final DeckSection section;
        private final DeckPrintingKey printingKey;
        private final int manualCount;

        private PendingEntry(final DeckSection section, final DeckPrintingKey printingKey, final int manualCount) {
            this.section = section;
            this.printingKey = printingKey;
            this.manualCount = manualCount;
        }
    }
}
