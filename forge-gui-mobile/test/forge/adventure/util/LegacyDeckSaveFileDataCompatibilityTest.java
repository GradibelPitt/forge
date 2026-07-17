package forge.adventure.util;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.deck.construction.DeckConstructionLedger;
import forge.deck.construction.DeckPrintingKey;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class LegacyDeckSaveFileDataCompatibilityTest {
    /**
     * The fixture was emitted by the released pre-ledger fat JAR with SHA-256
     * EF73B18AE1906A0B2C20672DB27C988AB91FB0F8811A96BF8C388B1D71B26ED8 and Deck UID
     * -8539667316828440829L. AdventurePlayer writes this exact Deck[] through
     * SaveFileData.storeObject("boosters", boostersOwned.toArray(Deck.class)).
     */
    @Test
    void readsOldDeckArrayThroughTheRealSaveFileDataBoostersPath() throws Exception {
        final byte[] serialized;
        try (InputStream resource = getClass().getResourceAsStream(
                "/forge/adventure/util/legacy-deck-array-v1.b64")) {
            assertNotNull(resource);
            serialized = Base64.getMimeDecoder().decode(resource.readAllBytes());
        }
        final SaveFileData saveData = new SaveFileData();
        saveData.put("boosters", serialized);

        final Deck[] decks = assertInstanceOf(Deck[].class, saveData.readObject("boosters"));

        assertEquals(1, decks.length);
        assertEquals("Legacy UID Fixture", decks[0].getName());
        assertEquals(List.of("legacy-fixture"), List.copyOf(decks[0].getTags()));
        assertEquals(DeckConstructionLedger.Status.UNTRACKED,
                decks[0].getConstructionLedger().getStatus());
        assertEquals(List.of("2 Legacy Fixture Card|TST|1"), deferredSections(decks[0]).get("Main"));
    }

    @Test
    void currentTrackedLedgerRoundTripsThroughSaveFileDataWithoutAliasing() {
        final DeckPrintingKey printing = new DeckPrintingKey("Managed Target", "TST", 3, "9", true,
                Map.of("markedColors", "WU"), "diagnostic");
        final DeckConstructionLedger.ContributionKey contribution =
                new DeckConstructionLedger.ContributionKey("source", "rule", 0, 1, "sha256:abc");
        final DeckConstructionLedger.Entry entry = new DeckConstructionLedger.Entry(
                DeckSection.Main, printing, 3, Map.of(contribution, 2));
        final Deck original = new Deck("SaveFileData tracked deck");
        original.setConstructionLedger(DeckConstructionLedger.tracked("save-data-id", List.of(entry)));
        final SaveFileData saveData = new SaveFileData();

        saveData.storeObject("trackedDeck", original);
        final Deck restored = assertInstanceOf(Deck.class, saveData.readObject("trackedDeck"));

        assertEquals(DeckConstructionLedger.Status.TRACKED, restored.getConstructionLedger().getStatus());
        assertEquals(original.getConstructionLedger(), restored.getConstructionLedger());
        assertEquals(original.getConstructionLedger().toLines(), restored.getConstructionLedger().toLines());
        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        assertNotSame(original.getConstructionLedger(), restored.getConstructionLedger());
        assertNotSame(original.getConstructionLedger().getEntries().get(0),
                restored.getConstructionLedger().getEntries().get(0));
        assertNotSame(original.getConstructionLedger().getEntries().get(0).getManagedCounts(),
                restored.getConstructionLedger().getEntries().get(0).getManagedCounts());
        assertEquals(printing.encode(),
                restored.getConstructionLedger().getEntries().get(0).getPrintingKey().encode());
        assertEquals(3, restored.getConstructionLedger().getEntries().get(0).getManualCount());
        assertEquals(2, restored.getConstructionLedger().getEntries().get(0).getManagedCount());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> deferredSections(final Deck deck) throws Exception {
        final Field deferred = Deck.class.getDeclaredField("deferredSections");
        deferred.setAccessible(true);
        return (Map<String, List<String>>) deferred.get(deck);
    }
}
