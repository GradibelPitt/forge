package forge.deck.io;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.deck.construction.DeckConstructionLedger;
import forge.deck.construction.DeckConstructionLedger.ContributionKey;
import forge.deck.construction.DeckConstructionLedger.Entry;
import forge.deck.construction.DeckConstructionLedger.Status;
import forge.deck.construction.DeckPrintingKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckConstructionSerializationTest {
    @TempDir
    Path tempDir;

    @Test
    void saveLoadRoundTripPreservesLedgerAndUserTags() {
        final Deck deck = deckWithLedger("round-trip-id");
        deck.getTags().add("User|Tag=Preserved");
        deck.getTags().add("alpha");
        final Path file = tempDir.resolve("round-trip.dck");

        DeckSerializer.writeDeck(deck, file.toFile());
        final Deck loaded = DeckSerializer.fromFile(file.toFile());

        assertEquals(deck.getConstructionLedger(), loaded.getConstructionLedger());
        assertEquals(deck.getTags(), loaded.getTags());
        assertFalse(loaded.getTags().stream().anyMatch(t -> t.startsWith(DeckConstructionLedger.MARKER_PREFIX)));
        final String serialized = assertDoesNotThrow(() -> java.nio.file.Files.readString(file));
        assertTrue(serialized.contains("[construction]"));
        assertTrue(serialized.contains(DeckConstructionLedger.MARKER_PREFIX + "round-trip-id"));
    }

    @Test
    void deckFilesAreWrittenAsUtf8() throws Exception {
        final Deck deck = deckWithLedger("utf8-id");
        deck.setName("中文牌组 😀");
        deck.getTags().add("标签-雪");
        final Path file = tempDir.resolve("utf8.dck");

        DeckSerializer.writeDeck(deck, file.toFile());

        final String serialized = StandardCharsets.UTF_8.newDecoder()
                .decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
        assertTrue(serialized.contains("中文牌组 😀"));
        assertTrue(serialized.contains("标签-雪"));
        assertEquals(deck.getName(), DeckSerializer.fromFile(file.toFile()).getName());
    }

    @Test
    void serializationFailureLeavesAnExistingTargetFileUntouched() throws Exception {
        final Path target = tempDir.resolve("must-survive.dck");
        Files.writeString(target, "existing deck bytes");
        final Deck failing = new Deck("serialization failure") {
            @Override
            public Iterator<Map.Entry<DeckSection, CardPool>> iterator() {
                throw new IllegalStateException("synthetic serialization failure");
            }
        };

        assertThrows(IllegalStateException.class, () -> DeckSerializer.writeDeck(failing, target.toFile()));

        assertEquals("existing deck bytes", Files.readString(target));
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".forge-deck-")));
        }
    }

    @Test
    void partialTemporaryWriteFailureLeavesTargetByteExactAndRemovesTemporaryFile() throws Exception {
        final Path target = tempDir.resolve("partial-write.dck");
        final byte[] original = new byte[] {0, 1, 2, 3, -1, 127};
        Files.write(target, original);
        final Deck deck = deckWithLedger("writer-fault-id");

        final RuntimeException failure = assertThrows(RuntimeException.class,
                () -> DeckSerializer.writeDeck(deck, target.toFile(), (temporary, lines) -> {
                    Files.write(temporary, new byte[] {9, 8, 7});
                    throw new IOException("synthetic partial writer failure");
                }));

        assertInstanceOf(IOException.class, failure.getCause());
        assertArrayEquals(original, Files.readAllBytes(target));
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".forge-deck-")));
        }
    }

    @Test
    void copyToDeepCopiesLedgerAndLedgerOnlyChangesAffectDeckEquality() {
        final Deck original = deckWithLedger("copy-id");
        final Deck copied = (Deck) original.copyTo(original.getName());

        assertEquals(original, copied);
        assertEquals(original.getConstructionLedger(), copied.getConstructionLedger());
        assertNotSame(original.getConstructionLedger(), copied.getConstructionLedger());
        assertNotSame(original.getConstructionLedger().getEntries().get(0),
                copied.getConstructionLedger().getEntries().get(0));

        final Entry changedEntry = copied.getConstructionLedger().getEntries().get(0).withManualCount(4);
        copied.setConstructionLedger(copied.getConstructionLedger().withEntry(changedEntry));

        assertNotEquals(original, copied);
        assertEquals(3, original.getConstructionLedger().getEntries().get(0).getManualCount());
    }

    @Test
    void trackedLedgerSurvivesStandardJavaSerializationWithoutContainerAliasing() throws Exception {
        final Deck original = deckWithLedger("java-object-id");
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        final Deck restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (Deck) input.readObject();
        }

        assertEquals(Status.TRACKED, restored.getConstructionLedger().getStatus());
        assertEquals(original.getConstructionLedger(), restored.getConstructionLedger());
        assertEquals(original.getConstructionLedger().toLines(), restored.getConstructionLedger().toLines());
        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        assertNotSame(original.getConstructionLedger(), restored.getConstructionLedger());
        assertNotSame(original.getConstructionLedger().getEntries().get(0),
                restored.getConstructionLedger().getEntries().get(0));
        assertNotSame(original.getConstructionLedger().getEntries().get(0).getManagedCounts(),
                restored.getConstructionLedger().getEntries().get(0).getManagedCounts());
        assertEquals(original.getConstructionLedger().getEntries().get(0).getPrintingKey().encode(),
                restored.getConstructionLedger().getEntries().get(0).getPrintingKey().encode());
        assertEquals(3, restored.getConstructionLedger().getEntries().get(0).getManualCount());
        assertEquals(2, restored.getConstructionLedger().getEntries().get(0).getManagedCount());
    }

    @Test
    void transportLedgerIdAloneDoesNotMakeOtherwiseEqualDecksUnequal() {
        final Deck first = new Deck("same");
        final Deck second = new Deck("same");

        assertNotEquals(first.getConstructionLedger().getLedgerId(), second.getConstructionLedger().getLedgerId());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        second.setConstructionLedger(DeckConstructionLedger.untracked("different-id"));
        assertNotEquals(first, second);
    }

    @Test
    void missingConstructionSectionUsesMarkerToDetectOrphanedDeck() {
        final Map<String, List<String>> sections = sectionsWithTags(
                "user," + DeckConstructionLedger.MARKER_PREFIX + "orphan-id");
        final Deck deck = DeckSerializer.fromSections(sections);

        assertEquals(Status.ORPHANED, deck.getConstructionLedger().getStatus());
        assertEquals("orphan-id", deck.getConstructionLedger().getLedgerId());
        assertEquals(List.of("user"), List.copyOf(deck.getTags()));
    }

    @Test
    void constructionSectionWithoutMarkerFailsClosed() {
        final DeckConstructionLedger ledger = deckWithLedger("missing-marker-id").getConstructionLedger();
        final Map<String, List<String>> sections = sectionsWithTags("user");
        sections.put("construction", ledger.toLines());

        final Deck deck = DeckSerializer.fromSections(sections);

        assertEquals(Status.UNRESOLVED, deck.getConstructionLedger().getStatus());
        assertTrue(deck.getConstructionLedger().getEntries().isEmpty());
        assertEquals(List.of("user"), List.copyOf(deck.getTags()));
    }

    @Test
    void legacyDeckWithoutMarkerOrSectionIsUntracked() {
        final Deck deck = DeckSerializer.fromSections(sectionsWithTags("legacy"));
        final String ledgerId = deck.getConstructionLedger().getLedgerId();

        assertEquals(Status.UNTRACKED, deck.getConstructionLedger().getStatus());
        assertEquals(List.of("legacy"), List.copyOf(deck.getTags()));
        deck.setDeferredSections(sectionsWithTags("legacy"));
        assertEquals(ledgerId, deck.getConstructionLedger().getLedgerId());
    }

    @Test
    void corruptUnknownAndDuplicateSectionsFailClosedWithoutTouchingPoolsOrInputs() throws Exception {
        final Map<String, List<String>> input = new LinkedHashMap<>(sectionsWithTags("user"));
        input.put("construction", List.of("version=999", "id=Y29ycnVwdA"));
        input.put("Construction", List.of("version=1"));
        final Map<String, List<String>> snapshot = deepCopy(input);

        final Deck deck = assertDoesNotThrow(() -> DeckSerializer.fromSections(input));

        assertEquals(Status.UNRESOLVED, deck.getConstructionLedger().getStatus());
        assertTrue(deck.getConstructionLedger().getEntries().isEmpty());
        assertEquals(0, rawParts(deck).get(DeckSection.Main).countAll());
        assertEquals(snapshot, input);
    }

    @Test
    void constructionIsExtractedWhileMainAndUnknownSectionsStayLazyAndInputIsUntouched() throws Exception {
        final DeckConstructionLedger ledger = deckWithLedger("lazy-id").getConstructionLedger();
        final Map<String, List<String>> input = new LinkedHashMap<>();
        input.put("metadata", List.of(
                "Name=lazy deck",
                "Tags=user," + DeckConstructionLedger.MARKER_PREFIX + ledger.getLedgerId()));
        input.put("construction", ledger.toLines());
        input.put("Main", List.of("2 Lazy Main Card|TST|1"));
        input.put("FutureUnknownSection", List.of("opaque future data"));
        final Map<String, List<String>> snapshot = deepCopy(input);

        final Deck deck = DeckSerializer.fromSections(input);
        final Map<String, List<String>> deferred = rawDeferredSections(deck);

        assertEquals(ledger, deck.getConstructionLedger());
        assertFalse(deferred.containsKey("construction"));
        assertEquals(List.of("2 Lazy Main Card|TST|1"), deferred.get("Main"));
        assertEquals(List.of("opaque future data"), deferred.get("FutureUnknownSection"));
        assertEquals(0, rawParts(deck).get(DeckSection.Main).countAll());
        assertEquals(snapshot, input);
    }

    @Test
    void corruptDuplicateOverflowAndOversizedLinesNeverThrowAndProduceEmptyUnresolvedLedger() {
        final DeckConstructionLedger valid = deckWithLedger("parse-id").getConstructionLedger();
        final DeckConstructionLedger validParsed = DeckConstructionLedger.parseLines(valid.toLines(), null);
        assertEquals(valid.toLines(), validParsed.toLines());
        assertEquals("diagnostic-variant",
                validParsed.getEntries().get(0).getPrintingKey().getFunctionalVariant());
        final List<String> duplicateSlot = new ArrayList<>(valid.toLines());
        duplicateSlot.add(duplicateSlot.stream().filter(line -> line.startsWith("entry=")).findFirst().orElseThrow());
        final List<String> duplicateContribution = new ArrayList<>(valid.toLines());
        duplicateContribution.add(duplicateContribution.stream().filter(line -> line.startsWith("managed=")).findFirst().orElseThrow());
        final List<String> oversized = List.of("x".repeat(DeckConstructionLedger.MAX_LINE_LENGTH + 1));

        for (List<String> malformed : List.of(
                duplicateSlot,
                duplicateContribution,
                List.of("version=999", "id=aWQ", "status=TRACKED", "reason="),
                List.of("version=1", "id=aWQ", "status=TRACKED", "reason=", "entry=0|TWFpbg|bad|2147483648"),
                oversized,
                List.of("version=1", "version=1", "id=aWQ", "status=TRACKED", "reason=")
        )) {
            final DeckConstructionLedger parsed = assertDoesNotThrow(
                    () -> DeckConstructionLedger.parseLines(malformed, "fallback-id"));
            assertEquals(Status.UNRESOLVED, parsed.getStatus());
            assertTrue(parsed.getEntries().isEmpty());
        }
    }

    @Test
    void nonCanonicalPrintingAndManagedIdentityEncodingsFailClosed() {
        final List<String> canonical = deckWithLedger("canonical-id").getConstructionLedger().toLines();
        final List<List<String>> nonCanonicalSections = List.of(
                withAlternatePrintingBoolean(canonical),
                withManagedField(canonical, 1, "source"),
                withManagedField(canonical, 2, "Cafe\u0301"),
                withManagedField(canonical, 5, "SHA256:ABC|123"),
                withSwappedFirstTwoLines(canonical)
        );

        for (List<String> section : nonCanonicalSections) {
            final DeckConstructionLedger parsed = DeckConstructionLedger.parseLines(section, "fallback-id");
            assertEquals(Status.UNRESOLVED, parsed.getStatus());
            assertTrue(parsed.getEntries().isEmpty());
        }
    }

    @Test
    void codecEnforcesTotalSectionCharacterAndLineCountBoundsBeforeDecoding() {
        final String chunk = "x".repeat(DeckConstructionLedger.MAX_LINE_LENGTH);
        final List<String> tooManyCharacters = new ArrayList<>();
        while ((long) tooManyCharacters.size() * chunk.length() <= DeckConstructionLedger.MAX_SECTION_CHARACTERS) {
            tooManyCharacters.add(chunk);
        }
        final List<String> tooManyLines = java.util.Collections.nCopies(
                DeckConstructionLedger.MAX_LINES + 1, "x");

        assertEquals(Status.UNRESOLVED,
                DeckConstructionLedger.parseLines(tooManyCharacters, "fallback-id").getStatus());
        assertEquals(Status.UNRESOLVED,
                DeckConstructionLedger.parseLines(tooManyLines, "fallback-id").getStatus());
    }

    @Test
    void validNonTrackedStatusAndReasonRoundTripDeterministically() {
        final DeckConstructionLedger ledger = deckWithLedger("status-id").getConstructionLedger()
                .withStatus(Status.UNRESOLVED, "需要显式修复|=\nreason");
        final DeckConstructionLedger parsed = DeckConstructionLedger.parseLines(ledger.toLines(), null);

        assertEquals(ledger, parsed);
        assertEquals(ledger.toLines(), parsed.toLines());
    }

    @Test
    void invalidCountsAndAliasedCollectionsAreRejectedOrIsolated() {
        final DeckPrintingKey key = printingKey();
        final ContributionKey contribution = contribution();
        assertThrows(IllegalArgumentException.class,
                () -> new Entry(DeckSection.Main, key, -1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Entry(DeckSection.Main, key, Integer.MAX_VALUE, Map.of(contribution, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Entry(DeckSection.Main, key, 0, Map.of(contribution, -1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Entry(DeckSection.Main, key, 0, Map.of()));

        final Map<ContributionKey, Integer> managed = new LinkedHashMap<>();
        managed.put(contribution, 2);
        final Entry entry = new Entry(DeckSection.Main, key, 3, managed);
        managed.clear();

        assertEquals(2, entry.getManagedCount());
        assertThrows(UnsupportedOperationException.class,
                () -> entry.getManagedCounts().put(contribution, 9));

        final DeckConstructionLedger atManualLimit = DeckConstructionLedger.tracked("limit-id")
                .withEntry(new Entry(DeckSection.Main, key, DeckConstructionLedger.MAX_TOTAL_MANUAL_COUNT,
                        Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> atManualLimit.withEntry(new Entry(DeckSection.Sideboard, key, 1, Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> DeckConstructionLedger.tracked("managed-limit-id")
                        .withEntry(new Entry(DeckSection.Main, key, 0,
                                Map.of(contribution, DeckConstructionLedger.MAX_TOTAL_MANAGED_COUNT + 1))));
    }

    @Test
    void ruleSchemaAndFingerprintArePartOfManagedContributionIdentity() {
        final ContributionKey first = new ContributionKey("source", "rule", 7, 2, "sha256:first");
        final ContributionKey second = new ContributionKey("source", "rule", 7, 3, "sha256:first");
        final ContributionKey third = new ContributionKey("source", "rule", 7, 2, "sha256:second");
        final Entry entry = new Entry(DeckSection.Main, printingKey(), 0,
                Map.of(first, 1, second, 1, third, 1));

        assertEquals(3, entry.getManagedCounts().size());
        assertEquals(3, entry.getManagedCount());
    }

    @Test
    void contributionSourceNamesUseTheSharedUnicodeCanonicalFormAndReasonsAreBounded() {
        final ContributionKey lower = new ContributionKey("  source ", "rule", 1, 1, "ABC");
        final ContributionKey upper = new ContributionKey("SOURCE", "rule", 1, 1, "abc");
        final ContributionKey decomposedId = new ContributionKey("source", "Cafe\u0301", 1, 1, "abc");
        final ContributionKey precomposedId = new ContributionKey("source", "Café", 1, 1, "abc");
        final ContributionKey differentCaseId = new ContributionKey("source", "CAFÉ", 1, 1, "abc");

        assertEquals(lower, upper);
        assertEquals(decomposedId, precomposedId);
        assertNotEquals(precomposedId, differentCaseId);
        assertEquals("SOURCE", lower.getSourceCanonicalName());
        assertEquals(DeckConstructionLedger.MAX_REASON_LENGTH,
                DeckConstructionLedger.orphaned("id", "x".repeat(DeckConstructionLedger.MAX_REASON_LENGTH + 10))
                        .getReason().length());
        assertEquals(DeckConstructionLedger.MAX_REASON_LENGTH,
                DeckConstructionLedger.tracked("id").withStatus(Status.UNRESOLVED,
                        "x".repeat(DeckConstructionLedger.MAX_REASON_LENGTH + 10)).getReason().length());
    }

    @Test
    void bulkFactoriesConsumeCallerIterablesOnceAndRejectDuplicateReplacementSlots() {
        final Entry first = new Entry(DeckSection.Main, printingKey(), 1, Map.of());
        final Entry second = new Entry(DeckSection.Sideboard, printingKey(), 1, Map.of());
        final AtomicInteger iterations = new AtomicInteger();
        final Iterable<Entry> oneShot = () -> {
            if (iterations.incrementAndGet() != 1) {
                throw new AssertionError("bulk source was iterated more than once");
            }
            return List.of(first, second).iterator();
        };

        final DeckConstructionLedger ledger = DeckConstructionLedger.tracked("bulk-id", oneShot);

        assertEquals(1, iterations.get());
        assertEquals(2, ledger.getEntries().size());
        assertThrows(IllegalArgumentException.class,
                () -> ledger.withEntries(List.of(first.withManualCount(2), first.withManualCount(3))));
    }

    /**
     * These objects were generated by GenerateLegacyDeckFixture.java.txt against the released pre-ledger
     * fat JAR SHA-256 EF73B18AE1906A0B2C20672DB27C988AB91FB0F8811A96BF8C388B1D71B26ED8. Its Deck UID is
     * -8539667316828440829L. AdventurePlayer stores boosters through SaveFileData.storeObject as the same Deck[]
     * ObjectOutputStream shape, so this core fixture exercises the compatibility boundary without mobile runtime
     * dependencies.
     */
    @Test
    void realLegacyStandaloneDeckFixtureRetainsIdentityAndDeferredCards() throws Exception {
        assertEquals(-8539667316828440829L, ObjectStreamClass.lookup(Deck.class).getSerialVersionUID());
        final Deck deck = (Deck) readLegacyFixture("/forge/deck/io/legacy-deck-single-v1.b64");

        assertLegacyFixtureDeck(deck);
    }

    @Test
    void realLegacyDeckArrayFixtureMatchesAdventureSaveFileDataObjectShape() throws Exception {
        final Deck[] graph = (Deck[]) readLegacyFixture("/forge/deck/io/legacy-deck-array-v1.b64");


        assertEquals(1, graph.length);
        assertLegacyFixtureDeck(graph[0]);
    }

    private static Deck deckWithLedger(final String ledgerId) {
        final Deck deck = new Deck("ledger deck");
        final Entry entry = new Entry(DeckSection.Main, printingKey(), 3, Map.of(contribution(), 2));
        deck.setConstructionLedger(DeckConstructionLedger.tracked(ledgerId).withEntry(entry));
        return deck;
    }

    private static DeckPrintingKey printingKey() {
        return new DeckPrintingKey("Target|Card", "TST", 4, "C|9", true,
                Map.of("markedColors", "WU"), "diagnostic-variant");
    }

    private static ContributionKey contribution() {
        return new ContributionKey("来源|card", "rule|id", 7, 2, "sha256:abc|123");
    }

    private static Map<String, List<String>> sectionsWithTags(final String tags) {
        final Map<String, List<String>> sections = new LinkedHashMap<>();
        final List<String> metadata = new ArrayList<>(List.of("Name=test"));
        if (tags != null && !tags.isEmpty()) {
            metadata.add("Tags=" + tags);
        }
        sections.put("metadata", metadata);
        return sections;
    }

    private static Map<String, List<String>> deepCopy(final Map<String, List<String>> source) {
        final Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }

    private static List<String> withManagedField(final List<String> canonical, final int fieldIndex,
                                                 final String decodedValue) {
        final List<String> changed = new ArrayList<>(canonical);
        for (int i = 0; i < changed.size(); i++) {
            final String line = changed.get(i);
            if (!line.startsWith("managed=")) {
                continue;
            }
            final String[] fields = line.substring("managed=".length()).split("\\|", -1);
            fields[fieldIndex] = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(decodedValue.getBytes(StandardCharsets.UTF_8));
            changed.set(i, "managed=" + String.join("|", fields));
            return changed;
        }
        throw new AssertionError("canonical fixture has no managed contribution");
    }

    private static List<String> withAlternatePrintingBoolean(final List<String> canonical) {
        final List<String> changed = new ArrayList<>(canonical);
        for (int i = 0; i < changed.size(); i++) {
            final String line = changed.get(i);
            if (!line.startsWith("entry=")) {
                continue;
            }
            final String[] fields = line.substring("entry=".length()).split("\\|", -1);
            final byte[] printing = Base64.getUrlDecoder().decode(fields[2]);
            final ByteBuffer buffer = ByteBuffer.wrap(printing);
            buffer.getInt();
            skipLengthPrefixedString(buffer);
            skipLengthPrefixedString(buffer);
            buffer.getInt();
            skipLengthPrefixedString(buffer);
            printing[buffer.position()] = 2;
            fields[2] = Base64.getUrlEncoder().withoutPadding().encodeToString(printing);
            changed.set(i, "entry=" + String.join("|", fields));
            return changed;
        }
        throw new AssertionError("canonical fixture has no entry");
    }

    private static List<String> withSwappedFirstTwoLines(final List<String> canonical) {
        final List<String> changed = new ArrayList<>(canonical);
        java.util.Collections.swap(changed, 0, 1);
        return changed;
    }

    private static void skipLengthPrefixedString(final ByteBuffer buffer) {
        final int length = buffer.getInt();
        buffer.position(Math.addExact(buffer.position(), length));
    }

    @SuppressWarnings("unchecked")
    private static Map<DeckSection, CardPool> rawParts(final Deck deck) throws Exception {
        final Field parts = Deck.class.getDeclaredField("parts");
        parts.setAccessible(true);
        return (Map<DeckSection, CardPool>) parts.get(deck);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> rawDeferredSections(final Deck deck) throws Exception {
        final Field deferred = Deck.class.getDeclaredField("deferredSections");
        deferred.setAccessible(true);
        return (Map<String, List<String>>) deferred.get(deck);
    }

    private static Object readLegacyFixture(final String resourceName) throws Exception {
        final byte[] serialized;
        try (InputStream resource = DeckConstructionSerializationTest.class.getResourceAsStream(resourceName)) {
            assertNotNull(resource);
            serialized = Base64.getMimeDecoder().decode(resource.readAllBytes());
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return input.readObject();
        }
    }

    private static void assertLegacyFixtureDeck(final Deck deck) throws Exception {
        assertEquals("Legacy UID Fixture", deck.getName());
        assertEquals(List.of("legacy-fixture"), List.copyOf(deck.getTags()));
        assertEquals(Status.UNTRACKED, deck.getConstructionLedger().getStatus());
        assertEquals(List.of("2 Legacy Fixture Card|TST|1"), rawDeferredSections(deck).get("Main"));
    }
}
