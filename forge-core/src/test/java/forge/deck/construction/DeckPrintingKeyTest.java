package forge.deck.construction;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckPrintingKeyTest {
    @Test
    void everyCardPoolIdentityFieldParticipatesInMatchingAndEquality() {
        final PaperCard base = card("Name|=卡", "SET|=1", 2, false, "CN|=7", "variant-one")
                .copyWithFlags(Map.of("markedColors", "WU"));
        final DeckPrintingKey key = DeckPrintingKey.from(base);

        assertTrue(key.matches(base));
        assertNotEquals(key, DeckPrintingKey.from(card("Other", "SET|=1", 2, false, "CN|=7", "variant-one")
                .copyWithFlags(Map.of("markedColors", "WU"))));
        assertNotEquals(key, DeckPrintingKey.from(card("Name|=卡", "SET2", 2, false, "CN|=7", "variant-one")
                .copyWithFlags(Map.of("markedColors", "WU"))));
        assertNotEquals(key, DeckPrintingKey.from(card("Name|=卡", "SET|=1", 3, false, "CN|=7", "variant-one")
                .copyWithFlags(Map.of("markedColors", "WU"))));
        assertNotEquals(key, DeckPrintingKey.from(card("Name|=卡", "SET|=1", 2, false, "different", "variant-one")
                .copyWithFlags(Map.of("markedColors", "WU"))));
        assertNotEquals(key, DeckPrintingKey.from(card("Name|=卡", "SET|=1", 2, true, "CN|=7", "variant-one")
                .copyWithFlags(Map.of("markedColors", "WU"))));
        assertNotEquals(key, DeckPrintingKey.from(card("Name|=卡", "SET|=1", 2, false, "CN|=7", "variant-one")
                .copyWithFlags(Map.of("noSellValue", "true"))));
    }

    @Test
    void functionalVariantIsRoundTrippedForDiagnosticsButIsNotPoolIdentity() {
        final PaperCard first = card("Variant Card", "TST", 1, false, "8", "variant-one");
        final PaperCard second = card("Variant Card", "TST", 1, false, "8", "variant-two");
        final DeckPrintingKey firstKey = DeckPrintingKey.from(first);
        final DeckPrintingKey secondKey = DeckPrintingKey.from(second);

        assertEquals(firstKey, secondKey);
        assertTrue(firstKey.matches(second));
        assertEquals("variant-one", DeckPrintingKey.decode(firstKey.encode()).getFunctionalVariant());
        assertEquals("variant-two", DeckPrintingKey.decode(secondKey.encode()).getFunctionalVariant());
    }

    @Test
    void encodingIsCanonicalAndRoundTripsUnicodeAndDelimiterCharacters() {
        final DeckPrintingKey key = new DeckPrintingKey(
                "名|=\n称", "版|=", 42, "编|=\n号", true,
                Map.of("z|=", "值\n二", "a", "值|=一"), "诊断|=\n");

        final String encoded = key.encode();
        final DeckPrintingKey decoded = DeckPrintingKey.decode(encoded);

        assertEquals(key, decoded);
        assertEquals(key.getFunctionalVariant(), decoded.getFunctionalVariant());
        assertEquals(encoded, decoded.encode());
        assertFalse(encoded.contains("="));
    }

    @Test
    void flagsAreSortedImmutableAndDoNotAliasInput() {
        final Map<String, String> flags = new HashMap<>();
        flags.put("z", "last");
        flags.put("a", "first");
        final DeckPrintingKey key = new DeckPrintingKey("Card", "TST", 1, "1", false, flags, "");

        flags.put("new", "must-not-appear");

        assertEquals(List.of("a", "z"), List.copyOf(key.getFlags().keySet()));
        assertFalse(key.getFlags().containsKey("new"));
        assertThrows(UnsupportedOperationException.class, () -> key.getFlags().put("x", "y"));
    }

    @Test
    void malformedOrOversizedEncodingFailsInAControlledWay() {
        assertThrows(IllegalArgumentException.class, () -> DeckPrintingKey.decode("not+base64"));
        assertThrows(IllegalArgumentException.class, () -> DeckPrintingKey.decode("A".repeat(DeckPrintingKey.MAX_ENCODED_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> new DeckPrintingKey(
                "Card", "TST", 1, "1", false,
                Map.of("x".repeat(DeckPrintingKey.MAX_FLAG_KEY_LENGTH + 1), "value"), ""));
    }

    @Test
    void decodeRejectsAlternateBooleanBytesAndNonCanonicalFlagOrder() throws Exception {
        final DeckPrintingKey plain = new DeckPrintingKey("Card", "TST", 1, "1", true, Map.of(), "");
        final byte[] alternateBoolean = Base64.getUrlDecoder().decode(plain.encode());
        final ByteBuffer buffer = ByteBuffer.wrap(alternateBoolean);
        buffer.getInt();
        skipString(buffer);
        skipString(buffer);
        buffer.getInt();
        skipString(buffer);
        alternateBoolean[buffer.position()] = 2;
        final String alternateBooleanEncoding = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(alternateBoolean);

        assertThrows(IllegalArgumentException.class,
                () -> DeckPrintingKey.decode(alternateBooleanEncoding));
        assertThrows(IllegalArgumentException.class,
                () -> DeckPrintingKey.decode(reversedFlagEncoding()));
    }

    @Test
    void ledgerConstructorChecksExactCanonicalEncodingBudgetBeforeCreatingOutputLines() {
        final DeckPrintingKey key = new DeckPrintingKey("长".repeat(2_000), "TST", 1, "1", false,
                Map.of(), "diagnostic");
        final DeckConstructionLedger.ContributionKey contribution =
                new DeckConstructionLedger.ContributionKey("source", "rule", 0, 1, "a".repeat(4_000));
        final DeckConstructionLedger.Entry entry = new DeckConstructionLedger.Entry(
                DeckSection.Main, key, 1, Map.of(contribution, 1));
        final DeckConstructionLedger ledger = DeckConstructionLedger.tracked("budget-id", List.of(entry));
        final long estimated = ledger.estimateEncodedCharacters(DeckConstructionLedger.MAX_SECTION_CHARACTERS);
        final long actual = ledger.toLines().stream().mapToLong(String::length).sum();

        assertEquals(key.encodedLength(), key.encode().length());
        assertEquals(actual, estimated);
        assertThrows(IllegalArgumentException.class,
                () -> DeckConstructionLedger.trackedWithBudgetForTest("budget-id", List.of(entry), estimated - 1));

        final AtomicInteger consumed = new AtomicInteger();
        final Iterable<DeckConstructionLedger.Entry> lazyEntries = () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return consumed.get() < 100;
            }

            @Override
            public DeckConstructionLedger.Entry next() {
                consumed.incrementAndGet();
                return entry;
            }
        };
        assertThrows(IllegalArgumentException.class,
                () -> DeckConstructionLedger.trackedWithBudgetForTest("budget-id", lazyEntries, 100));
        assertEquals(1, consumed.get());
    }

    private static PaperCard card(final String name, final String edition, final int artIndex,
                                  final boolean foil, final String collectorNumber,
                                  final String functionalVariant) {
        return new PaperCard(CardRules.fromScript(List.of(
                "Name:" + name,
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:Test card."
        )), edition, CardRarity.Common, artIndex, foil, collectorNumber, "Artist", functionalVariant);
    }

    private static void skipString(final ByteBuffer buffer) {
        final int length = buffer.getInt();
        buffer.position(Math.addExact(buffer.position(), length));
    }

    private static String reversedFlagEncoding() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(0x44504B31); // DPK1
            writeString(output, "Card");
            writeString(output, "TST");
            output.writeInt(1);
            writeString(output, "1");
            output.writeBoolean(false);
            output.writeInt(2);
            writeString(output, "z");
            writeString(output, "last");
            writeString(output, "a");
            writeString(output, "first");
            writeString(output, "");
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private static void writeString(final DataOutputStream output, final String value) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
