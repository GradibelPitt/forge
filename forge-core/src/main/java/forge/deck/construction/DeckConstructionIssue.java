package forge.deck.construction;

import java.util.Objects;

/** A structured, non-throwing planning or commit failure. */
public final class DeckConstructionIssue {
    private static final int MAX_MESSAGE_LENGTH = 4_096;

    public enum Code {
        LEDGER_NOT_TRACKED,
        DEFERRED_DECK_NOT_LOADED,
        LEDGER_DRIFT,
        INVALID_EDIT,
        MANAGED_CARD_EDIT,
        MISSING_CARD,
        INACTIVE_RULE,
        CONFLICTING_RULE,
        CHOICE_REQUIRED,
        INVALID_CHOICE,
        CYCLIC_DEPENDENCY,
        MIGRATION_REQUIRED,
        RESOURCE_LIMIT,
        STALE_PLAN,
        LEGALITY_REJECTED,
        THREAD_CONFINEMENT,
        RESOLVER_FAILURE,
        COMMIT_FAILED,
        ROLLBACK_FAILED_STATE_UNKNOWN,
        INTERNAL_ERROR
    }

    private final Code code;
    private final String message;
    private final String sourceCanonicalName;
    private final String ruleId;

    public DeckConstructionIssue(final Code code, final String message) {
        this(code, message, null, null);
    }

    public DeckConstructionIssue(final Code code, final String message,
            final String sourceCanonicalName, final String ruleId) {
        this.code = Objects.requireNonNull(code, "code");
        final String safeMessage = message == null ? "" : message;
        this.message = safeMessage.length() <= MAX_MESSAGE_LENGTH
                ? safeMessage : safeMessage.substring(0, MAX_MESSAGE_LENGTH);
        this.sourceCanonicalName = sourceCanonicalName;
        this.ruleId = ruleId;
    }

    public Code getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getSourceCanonicalName() {
        return sourceCanonicalName;
    }

    public String getRuleId() {
        return ruleId;
    }

    @Override
    public String toString() {
        return code + (message.isEmpty() ? "" : ": " + message);
    }
}
