package forge.deck.construction;

import java.util.Objects;

/**
 * A non-fatal, structured reason why a deck-construction rule is inactive.
 * Card loading keeps these diagnostics instead of failing the whole card database.
 */
public final class DeckConstructionDiagnostic {
    public static final int MAX_SOURCE_UTF8_BYTES = 4_096;
    public static final int MAX_RULE_ID_UTF8_BYTES = 1_024;
    public static final int MAX_RAW_RULE_UTF8_BYTES = 512;
    public static final int MAX_MESSAGE_UTF8_BYTES = 4_096;

    public enum Status {
        INACTIVE
    }

    public enum Code {
        EMPTY_RULE_SET,
        EMPTY_RULE,
        MISSING_SOURCE_CARD,
        MALFORMED_FIELD,
        DUPLICATE_FIELD,
        UNKNOWN_FIELD,
        MISSING_FIELD,
        DUPLICATE_ID,
        UNKNOWN_MODE,
        UNKNOWN_CONSTRAINT,
        UNSUPPORTED_CARDINALITY,
        INVALID_CONTROL_CHARACTER,
        INVALID_SOURCE_LAYOUT,
        FIELD_NOT_ALLOWED,
        INVALID_TARGET,
        INVALID_AMOUNT,
        EMPTY_CANDIDATE,
        TOO_MANY_CANDIDATES,
        RESOURCE_LIMIT,
        MISSING_CARD,
        CYCLIC_DEPENDENCY,
        MIGRATION_REQUIRED,
        INTERNAL_ERROR
    }

    private final Status status;
    private final Code code;
    private final String sourceCardName;
    private final String ruleId;
    private final int ruleIndex;
    private final String rawRule;
    private final String message;

    public DeckConstructionDiagnostic(final Code code, final String sourceCardName, final String ruleId,
            final int ruleIndex, final String rawRule, final String message) {
        this.status = Status.INACTIVE;
        this.code = Objects.requireNonNull(code, "code");
        this.sourceCardName = truncateUtf8(sourceCardName, MAX_SOURCE_UTF8_BYTES);
        this.ruleId = truncateUtf8(ruleId, MAX_RULE_ID_UTF8_BYTES);
        this.ruleIndex = ruleIndex;
        this.rawRule = truncateUtf8(rawRule, MAX_RAW_RULE_UTF8_BYTES);
        this.message = truncateUtf8(message == null ? "" : message, MAX_MESSAGE_UTF8_BYTES);
    }

    private static String truncateUtf8(final String value, final int maximumBytes) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maximumBytes
                && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maximumBytes) {
            return value;
        }
        int bytes = 0;
        int end = 0;
        while (end < value.length()) {
            final int codePoint = value.codePointAt(end);
            final int codePointBytes = codePoint <= 0x7f ? 1
                    : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            if (bytes + codePointBytes > maximumBytes) {
                break;
            }
            bytes += codePointBytes;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    public Status getStatus() {
        return status;
    }

    public Code getCode() {
        return code;
    }

    public String getSourceCardName() {
        return sourceCardName;
    }

    public String getRuleId() {
        return ruleId;
    }

    public int getRuleIndex() {
        return ruleIndex;
    }

    public String getRawRule() {
        return rawRule;
    }

    public String getMessage() {
        return message;
    }

    public boolean isInactive() {
        return status == Status.INACTIVE;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DeckConstructionDiagnostic)) {
            return false;
        }
        final DeckConstructionDiagnostic that = (DeckConstructionDiagnostic) other;
        return ruleIndex == that.ruleIndex
                && status == that.status
                && code == that.code
                && Objects.equals(sourceCardName, that.sourceCardName)
                && Objects.equals(ruleId, that.ruleId)
                && Objects.equals(rawRule, that.rawRule)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, code, sourceCardName, ruleId, ruleIndex, rawRule, message);
    }

    @Override
    public String toString() {
        return "DeckConstructionDiagnostic{" + code + ", source=" + sourceCardName
                + ", ruleId=" + ruleId + ", index=" + ruleIndex + ", message=" + message + '}';
    }
}
