package forge.deck.construction;

import java.util.List;

/** Result of the optimistic transactional commit step. */
public final class DeckConstructionCommitResult {
    public enum Status {
        COMMITTED,
        NO_CHANGE,
        STALE_PLAN,
        BLOCKED,
        ROLLED_BACK,
        ROLLBACK_FAILED
    }

    private final Status status;
    private final List<DeckConstructionIssue> issues;

    DeckConstructionCommitResult(final Status status, final List<DeckConstructionIssue> issues) {
        this.status = status;
        this.issues = List.copyOf(issues);
    }

    public Status getStatus() {
        return status;
    }

    public List<DeckConstructionIssue> getIssues() {
        return issues;
    }

    public boolean isCommitted() {
        return status == Status.COMMITTED || status == Status.NO_CHANGE;
    }
}
