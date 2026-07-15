package forge.gamemodes.match;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fails a match closed when its long-lived game task terminates unexpectedly.
 */
final class MatchGameFailureHandler {
    private final Runnable inputCleanup;
    private final Consumer<Throwable> reporter;
    private final Runnable shutdownScheduler;

    MatchGameFailureHandler(final Runnable inputCleanup, final Consumer<Throwable> reporter,
            final Runnable shutdownScheduler) {
        this.inputCleanup = Objects.requireNonNull(inputCleanup);
        this.reporter = Objects.requireNonNull(reporter);
        this.shutdownScheduler = Objects.requireNonNull(shutdownScheduler);
    }

    void handle(final Throwable failure) {
        Objects.requireNonNull(failure);
        rethrowFatalFailure(failure);

        boolean mustRethrow = false;
        try {
            inputCleanup.run();
        } catch (final Throwable cleanupFailure) {
            preserveSecondaryFailure(failure, cleanupFailure);
        }

        try {
            reporter.accept(failure);
        } catch (final Throwable reporterFailure) {
            preserveSecondaryFailure(failure, reporterFailure);
            mustRethrow = true;
        }

        try {
            shutdownScheduler.run();
        } catch (final Throwable schedulingFailure) {
            preserveSecondaryFailure(failure, schedulingFailure);
            mustRethrow = true;
        }

        if (mustRethrow) {
            rethrowUnchecked(failure);
        }
    }

    private static void rethrowFatalFailure(final Throwable failure) {
        if (failure instanceof VirtualMachineError) {
            throw (VirtualMachineError) failure;
        }
        if (failure instanceof ThreadDeath) {
            throw (ThreadDeath) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
    }

    private static void preserveSecondaryFailure(final Throwable failure, final Throwable secondaryFailure) {
        if (secondaryFailure != failure) {
            failure.addSuppressed(secondaryFailure);
        }
    }

    private static void rethrowUnchecked(final Throwable failure) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Unexpected checked game failure", failure);
    }
}
