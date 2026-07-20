package forge.gamemodes.match;

import java.util.Objects;

/**
 * Runs every nonfatal failed-match cleanup step before surfacing cleanup
 * failures to the original match-failure reporter.
 */
public final class MatchGameFailureCleanup {
    private MatchGameFailureCleanup() {
    }

    public static void runAll(final Iterable<? extends Runnable> steps) {
        Throwable firstFailure = null;
        for (final Runnable step : Objects.requireNonNull(steps)) {
            try {
                Objects.requireNonNull(step).run();
            } catch (final Throwable failure) {
                MatchGameFailureHandler.rethrowFatalFailure(failure);
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    MatchGameFailureHandler.preserveSecondaryFailure(
                            firstFailure, failure);
                }
            }
        }
        rethrowCleanupFailure(firstFailure);
    }

    private static void rethrowCleanupFailure(final Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Unexpected checked cleanup failure",
                failure);
    }
}
