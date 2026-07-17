package forge.deck.construction;

import forge.item.IPaperCard;
import forge.item.PaperCard;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Stable, database-independent identity for one {@link PaperCard} printing in a deck pool.
 *
 * <p>{@code functionalVariant} is intentionally diagnostics-only. {@link PaperCard#equals(Object)} does not use it,
 * so neither {@link #matches(PaperCard)}, {@link #equals(Object)}, nor {@link #hashCode()} treats it as pool identity.
 * The field is nevertheless persisted to make a future mismatch explainable without retaining a PaperCard.</p>
 */
public final class DeckPrintingKey implements Comparable<DeckPrintingKey>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int BINARY_MAGIC = 0x44504B31; // DPK1
    public static final int MAX_ENCODED_LENGTH = 65_536;
    public static final int MAX_NAME_LENGTH = 4_096;
    public static final int MAX_EDITION_LENGTH = 256;
    public static final int MAX_COLLECTOR_NUMBER_LENGTH = 1_024;
    public static final int MAX_FUNCTIONAL_VARIANT_LENGTH = 4_096;
    public static final int MAX_FLAGS = 64;
    public static final int MAX_FLAG_KEY_LENGTH = 256;
    public static final int MAX_FLAG_VALUE_LENGTH = 4_096;

    private final String name;
    private final String edition;
    private final int artIndex;
    private final String collectorNumber;
    private final boolean foil;
    private final SortedMap<String, String> flags;
    private final String functionalVariant;
    private final int encodedLength;

    public DeckPrintingKey(final String name, final String edition, final int artIndex,
                           final String collectorNumber, final boolean foil,
                           final Map<String, String> flags, final String functionalVariant) {
        this.name = requireText(name, "name", MAX_NAME_LENGTH, false);
        this.edition = requireText(edition, "edition", MAX_EDITION_LENGTH, false);
        if (artIndex < IPaperCard.DEFAULT_ART_INDEX) {
            throw new IllegalArgumentException("artIndex is below the supported minimum");
        }
        this.artIndex = artIndex;
        this.collectorNumber = requireText(collectorNumber, "collectorNumber",
                MAX_COLLECTOR_NUMBER_LENGTH, true);
        this.foil = foil;
        this.flags = immutableFlags(flags);
        this.functionalVariant = requireText(functionalVariant, "functionalVariant",
                MAX_FUNCTIONAL_VARIANT_LENGTH, true);
        this.encodedLength = calculateEncodedLength();
    }

    public static DeckPrintingKey from(final PaperCard card) {
        Objects.requireNonNull(card, "card");
        return new DeckPrintingKey(card.getName(), card.getEdition(), card.getArtIndex(),
                card.getCollectorNumber(), card.isFoil(), card.getMarkedFlags().toMap(),
                card.getFunctionalVariant());
    }

    public boolean matches(final PaperCard card) {
        if (card == null) {
            return false;
        }
        return name.equals(card.getName())
                && edition.equals(card.getEdition())
                && artIndex == card.getArtIndex()
                && collectorNumber.equals(card.getCollectorNumber())
                && foil == card.isFoil()
                && flags.equals(card.getMarkedFlags().toMap());
    }

    /**
     * Returns a canonical Base64URL encoding of a length-prefixed binary record.
     */
    public String encode() {
        try {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(BINARY_MAGIC);
                writeString(output, name);
                writeString(output, edition);
                output.writeInt(artIndex);
                writeString(output, collectorNumber);
                output.writeBoolean(foil);
                output.writeInt(flags.size());
                for (Map.Entry<String, String> flag : flags.entrySet()) {
                    writeString(output, flag.getKey());
                    writeString(output, flag.getValue());
                }
                writeString(output, functionalVariant);
            }
            final String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            if (encoded.length() != encodedLength) {
                throw new IllegalStateException("printing key encoding length differs from its checked estimate");
            }
            return encoded;
        } catch (IOException ex) {
            throw new IllegalStateException("unexpected in-memory printing key encoding failure", ex);
        }
    }

    /**
     * Parses a canonical encoding without consulting CardDb. Invalid input is reported as an
     * {@link IllegalArgumentException}; callers loading untrusted deck files should convert that into an unresolved
     * construction ledger rather than failing deck loading.
     */
    public static DeckPrintingKey decode(final String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("printing key encoding is empty or exceeds the size limit");
        }
        try {
            final byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            final String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!canonical.equals(encoded)) {
                throw new IllegalArgumentException("printing key encoding is not canonical Base64URL");
            }
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readInt() != BINARY_MAGIC) {
                    throw new IllegalArgumentException("unknown printing key version");
                }
                final String name = readString(input, MAX_NAME_LENGTH, "name");
                final String edition = readString(input, MAX_EDITION_LENGTH, "edition");
                final int artIndex = input.readInt();
                final String collectorNumber = readString(input, MAX_COLLECTOR_NUMBER_LENGTH,
                        "collectorNumber");
                final boolean foil = input.readBoolean();
                final int flagCount = input.readInt();
                if (flagCount < 0 || flagCount > MAX_FLAGS) {
                    throw new IllegalArgumentException("printing key flag count exceeds the limit");
                }
                final Map<String, String> flags = new TreeMap<>();
                for (int i = 0; i < flagCount; i++) {
                    final String key = readString(input, MAX_FLAG_KEY_LENGTH, "flag key");
                    final String value = readString(input, MAX_FLAG_VALUE_LENGTH, "flag value");
                    if (flags.put(key, value) != null) {
                        throw new IllegalArgumentException("duplicate printing key flag");
                    }
                }
                final String functionalVariant = readString(input, MAX_FUNCTIONAL_VARIANT_LENGTH,
                        "functionalVariant");
                if (input.available() != 0) {
                    throw new IllegalArgumentException("trailing printing key data");
                }
                final DeckPrintingKey decoded = new DeckPrintingKey(name, edition, artIndex, collectorNumber,
                        foil, flags, functionalVariant);
                if (!decoded.encode().equals(encoded)) {
                    throw new IllegalArgumentException("printing key binary record is not canonical");
                }
                return decoded;
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalArgumentException("truncated printing key encoding", ex);
        }
    }

    public String getName() {
        return name;
    }

    public String getEdition() {
        return edition;
    }

    public int getArtIndex() {
        return artIndex;
    }

    public String getCollectorNumber() {
        return collectorNumber;
    }

    public boolean isFoil() {
        return foil;
    }

    public SortedMap<String, String> getFlags() {
        return flags;
    }

    /**
     * Diagnostics-only metadata; it is not part of CardPool/PaperCard identity.
     */
    public String getFunctionalVariant() {
        return functionalVariant;
    }

    public DeckPrintingKey copy() {
        return this; // Deeply immutable leaf; ledger/container copies cannot mutate or alias its state.
    }

    int encodedLength() {
        return encodedLength;
    }

    @Override
    public int compareTo(final DeckPrintingKey other) {
        int result = name.compareTo(other.name);
        if (result != 0) {
            return result;
        }
        result = edition.compareTo(other.edition);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(artIndex, other.artIndex);
        if (result != 0) {
            return result;
        }
        result = collectorNumber.compareTo(other.collectorNumber);
        if (result != 0) {
            return result;
        }
        result = Boolean.compare(foil, other.foil);
        if (result != 0) {
            return result;
        }
        return compareMaps(flags, other.flags);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DeckPrintingKey other)) {
            return false;
        }
        return artIndex == other.artIndex
                && foil == other.foil
                && name.equals(other.name)
                && edition.equals(other.edition)
                && collectorNumber.equals(other.collectorNumber)
                && flags.equals(other.flags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, edition, artIndex, collectorNumber, foil, flags);
    }

    @Override
    public String toString() {
        return encode();
    }

    private static SortedMap<String, String> immutableFlags(final Map<String, String> source) {
        if (source == null) {
            throw new IllegalArgumentException("flags must not be null");
        }
        if (source.size() > MAX_FLAGS) {
            throw new IllegalArgumentException("printing key flag count exceeds the limit");
        }
        final SortedMap<String, String> copy = new TreeMap<>();
        for (Map.Entry<String, String> flag : source.entrySet()) {
            final String key = requireText(flag.getKey(), "flag key", MAX_FLAG_KEY_LENGTH, false);
            final String value = requireText(flag.getValue(), "flag value", MAX_FLAG_VALUE_LENGTH, true);
            if (copy.put(key, value) != null) {
                throw new IllegalArgumentException("duplicate printing key flag");
            }
        }
        return Collections.unmodifiableSortedMap(copy);
    }

    private static String requireText(final String value, final String field, final int maxLength,
                                      final boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.isEmpty()) || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is empty or exceeds the size limit");
        }
        return value;
    }

    private static void writeString(final DataOutputStream output, final String value) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private int calculateEncodedLength() {
        long bytes = Integer.BYTES + Integer.BYTES + 1L + Integer.BYTES;
        bytes = addEncodedStringSize(bytes, name);
        bytes = addEncodedStringSize(bytes, edition);
        bytes = addEncodedStringSize(bytes, collectorNumber);
        for (Map.Entry<String, String> flag : flags.entrySet()) {
            bytes = addEncodedStringSize(bytes, flag.getKey());
            bytes = addEncodedStringSize(bytes, flag.getValue());
        }
        bytes = addEncodedStringSize(bytes, functionalVariant);
        final long encodedCharacters = base64UrlLength(bytes);
        if (encodedCharacters > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("printing key encoding exceeds the size limit");
        }
        return Math.toIntExact(encodedCharacters);
    }

    private static long addEncodedStringSize(final long current, final String value) {
        return Math.addExact(current, Math.addExact(Integer.BYTES,
                value.getBytes(StandardCharsets.UTF_8).length));
    }

    static long base64UrlLength(final long byteCount) {
        if (byteCount < 0) {
            throw new IllegalArgumentException("byte count must not be negative");
        }
        final long completeGroups = Math.floorDiv(byteCount, 3L);
        final int remainder = (int) Math.floorMod(byteCount, 3L);
        final long completeCharacters = Math.multiplyExact(completeGroups, 4L);
        return Math.addExact(completeCharacters, remainder == 0 ? 0L : remainder + 1L);
    }

    private static String readString(final DataInputStream input, final int maxCharacters,
                                     final String field) throws IOException {
        final int byteLength = input.readInt();
        if (byteLength < 0 || byteLength > Math.multiplyExact(maxCharacters, 4)
                || byteLength > input.available()) {
            throw new IllegalArgumentException(field + " byte length exceeds the limit");
        }
        final byte[] bytes = input.readNBytes(byteLength);
        final String value;
        try {
            value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException(field + " is not valid UTF-8", ex);
        }
        if (value.length() > maxCharacters) {
            throw new IllegalArgumentException(field + " character length exceeds the limit");
        }
        return value;
    }

    private static int compareMaps(final SortedMap<String, String> first,
                                   final SortedMap<String, String> second) {
        final Comparator<Map.Entry<String, String>> comparator = Map.Entry.comparingByKey();
        final java.util.Iterator<Map.Entry<String, String>> left = first.entrySet().iterator();
        final java.util.Iterator<Map.Entry<String, String>> right = second.entrySet().iterator();
        while (left.hasNext() && right.hasNext()) {
            final Map.Entry<String, String> leftEntry = left.next();
            final Map.Entry<String, String> rightEntry = right.next();
            int result = comparator.compare(leftEntry, rightEntry);
            if (result == 0) {
                result = leftEntry.getValue().compareTo(rightEntry.getValue());
            }
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(first.size(), second.size());
    }
}
