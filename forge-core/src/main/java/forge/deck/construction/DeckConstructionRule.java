package forge.deck.construction;

import forge.deck.DeckSection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable cardscript metadata consumed by the deck-construction service.
 * It intentionally stores names rather than resolved cards so CardRules loading
 * never needs CardDb.
 */
public final class DeckConstructionRule {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public enum Mode {
        ADD_FIXED,
        CHOOSE_ONE,
        ALLOW
    }

    /**
     * A narrowly scoped legality exception for one named card. COPY_LIMIT means
     * that the named card has no copy limit in schema version 1; a numeric limit
     * requires a future schema version.
     */
    public enum Constraint {
        FORMAT_CARD_POOL,
        COMMANDER_COLOR_IDENTITY,
        COPY_LIMIT,
        SECTION,
        BANNED_OR_RESTRICTED
    }

    public enum Cardinality {
        ONCE_PER_DECK
    }

    /** Collision-free map key for one source-card rule definition. */
    public static final class RuleKey implements Comparable<RuleKey> {
        private final String sourceCanonicalName;
        private final String ruleId;

        private RuleKey(final String sourceCanonicalName, final String ruleId) {
            this.sourceCanonicalName = sourceCanonicalName;
            this.ruleId = ruleId;
        }

        public static RuleKey of(final String sourceCardName, final String ruleId) {
            if (sourceCardName == null || ruleId == null
                    || DeckConstructionRuleParser.containsControlCharacter(sourceCardName)
                    || DeckConstructionRuleParser.containsControlCharacter(ruleId)) {
                throw new IllegalArgumentException("rule key fields are null or contain control characters");
            }
            final String normalizedSource = Normalizer.normalize(sourceCardName.strip(),
                    Normalizer.Form.NFC);
            final String normalizedId = Normalizer.normalize(ruleId.strip(), Normalizer.Form.NFC);
            if (normalizedSource.isEmpty() || normalizedId.isEmpty()) {
                throw new IllegalArgumentException("rule key fields must not be blank");
            }
            final String canonicalSource = canonicalCardNameKey(normalizedSource);
            if (DeckConstructionRuleParser.exceedsUtf8Limit(normalizedSource,
                    DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH)
                    || DeckConstructionRuleParser.exceedsUtf8Limit(canonicalSource,
                            DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH)
                    || DeckConstructionRuleParser.exceedsUtf8Limit(normalizedId,
                            DeckConstructionRuleParser.MAX_RULE_ID_LENGTH)) {
                throw new IllegalArgumentException("rule key field exceeds its UTF-8 size limit");
            }
            return new RuleKey(canonicalSource, normalizedId);
        }

        public String getSourceCanonicalName() {
            return sourceCanonicalName;
        }

        public String getRuleId() {
            return ruleId;
        }

        @Override
        public int compareTo(final RuleKey other) {
            final int sourceComparison = sourceCanonicalName.compareTo(other.sourceCanonicalName);
            return sourceComparison != 0 ? sourceComparison : ruleId.compareTo(other.ruleId);
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RuleKey)) {
                return false;
            }
            final RuleKey that = (RuleKey) other;
            return sourceCanonicalName.equals(that.sourceCanonicalName) && ruleId.equals(that.ruleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceCanonicalName, ruleId);
        }

        @Override
        public String toString() {
            return encodeGlobalKey(this);
        }
    }

    private final int schemaVersion;
    private final String sourceCardName;
    private final String sourceCanonicalName;
    private final String id;
    private final RuleKey ruleKey;
    private final String globalKey;
    private final Mode mode;
    private final DeckSection target;
    private final String cardName;
    private final int amount;
    private final List<String> candidates;
    private final Constraint constraint;
    private final Cardinality cardinality;
    private final String contentFingerprint;

    DeckConstructionRule(final String sourceCardName, final String id, final Mode mode,
            final DeckSection target, final String cardName, final int amount,
            final List<String> candidates, final Constraint constraint, final Cardinality cardinality) {
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.sourceCardName = Objects.requireNonNull(sourceCardName, "sourceCardName");
        this.ruleKey = RuleKey.of(sourceCardName, id);
        this.id = ruleKey.getRuleId();
        this.sourceCanonicalName = ruleKey.getSourceCanonicalName();
        this.globalKey = encodeGlobalKey(ruleKey);
        this.mode = Objects.requireNonNull(mode, "mode");
        this.target = target;
        this.cardName = cardName;
        this.amount = amount;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.constraint = constraint;
        this.cardinality = Objects.requireNonNull(cardinality, "cardinality");
        this.contentFingerprint = fingerprint();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getSourceCardName() {
        return sourceCardName;
    }

    public String getId() {
        return id;
    }

    public String getSourceCardKey() {
        return sourceCanonicalName;
    }

    public String getSourceCanonicalName() {
        return sourceCanonicalName;
    }

    /** Versioned Base64URL compatibility encoding; maps should use {@link #getRuleKey()}. */
    public String getGlobalKey() {
        return globalKey;
    }

    public RuleKey getRuleKey() {
        return ruleKey;
    }

    public Mode getMode() {
        return mode;
    }

    public DeckSection getTarget() {
        return target;
    }

    public String getCardName() {
        return cardName;
    }

    public int getAmount() {
        return amount;
    }

    public List<String> getCandidates() {
        return candidates;
    }

    public Constraint getConstraint() {
        return constraint;
    }

    public Cardinality getCardinality() {
        return cardinality;
    }

    /**
     * SHA-256 of a length-prefixed canonical encoding of all behavior fields.
     * Source card and rule id are deliberately excluded: they identify the
     * ledger slot, while this value tells that slot whether its content changed.
     */
    public String getContentFingerprint() {
        return contentFingerprint;
    }

    private String fingerprint() {
        final StringBuilder canonical = new StringBuilder(256);
        append(canonical, "schema", Integer.toString(schemaVersion));
        append(canonical, "mode", mode.name());
        append(canonical, "target", target == null ? null : target.name());
        append(canonical, "card", canonicalCardNameKey(cardName));
        append(canonical, "amount", Integer.toString(amount));
        append(canonical, "candidate-count", Integer.toString(candidates.size()));
        for (int i = 0; i < candidates.size(); i++) {
            append(canonical, "candidate-" + i, canonicalCardNameKey(candidates.get(i)));
        }
        append(canonical, "constraint", constraint == null ? null : constraint.name());
        append(canonical, "cardinality", cardinality.name());

        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            final StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
            for (final byte value : digest) {
                hexadecimal.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                hexadecimal.append(Character.forDigit(value & 0x0f, 16));
            }
            return hexadecimal.toString();
        } catch (final NoSuchAlgorithmException exception) {
            // SHA-256 is required by every Java runtime. Keep this explicit so a
            // broken runtime becomes a parser diagnostic rather than bad ledger data.
            throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
        }
    }

    private static void append(final StringBuilder output, final String key, final String value) {
        output.append(utf8Length(key)).append(':').append(key).append('=');
        if (value == null) {
            output.append("-1:");
        } else {
            output.append(utf8Length(value)).append(':').append(value);
        }
        output.append(';');
    }

    private static int utf8Length(final String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String encodeGlobalKey(final RuleKey key) {
        final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        final String source = encoder.encodeToString(
                key.sourceCanonicalName.getBytes(StandardCharsets.UTF_8));
        final String id = encoder.encodeToString(key.ruleId.getBytes(StandardCharsets.UTF_8));
        return "rk1." + source + '.' + id;
    }

    /**
     * Shared Java/Python contract for card-name identity: trim happens before
     * values reach this model, then NFC, Unicode ROOT uppercase, and NFC again.
     */
    public static String canonicalCardNameKey(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        return Normalizer.normalize(normalized.toUpperCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DeckConstructionRule)) {
            return false;
        }
        final DeckConstructionRule that = (DeckConstructionRule) other;
        return schemaVersion == that.schemaVersion
                && amount == that.amount
                && Objects.equals(sourceCardName, that.sourceCardName)
                && Objects.equals(sourceCanonicalName, that.sourceCanonicalName)
                && Objects.equals(id, that.id)
                && Objects.equals(ruleKey, that.ruleKey)
                && mode == that.mode
                && target == that.target
                && Objects.equals(cardName, that.cardName)
                && Objects.equals(candidates, that.candidates)
                && constraint == that.constraint
                && cardinality == that.cardinality
                && Objects.equals(contentFingerprint, that.contentFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, sourceCardName, sourceCanonicalName, id, ruleKey, mode, target,
                cardName, amount, candidates, constraint, cardinality, contentFingerprint);
    }

    @Override
    public String toString() {
        return "DeckConstructionRule{" + globalKey + ", mode=" + mode
                + ", fingerprint=" + contentFingerprint + '}';
    }
}
