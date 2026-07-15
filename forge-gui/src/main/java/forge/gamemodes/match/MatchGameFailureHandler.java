package forge.gamemodes.match;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fails a match closed when its long-lived game task terminates unexpectedly.
 */
final class MatchGameFailureHandler {
    private final Runnable inputCleanup;
    private final Consumer<Throwable> reporter;
    private final Consumer<Throwable> secondaryFailureSink;
    private final Runnable shutdownScheduler;

    MatchGameFailureHandler(final Runnable inputCleanup, final Consumer<Throwable> reporter,
            final Consumer<Throwable> secondaryFailureSink, final Runnable shutdownScheduler) {
        this.inputCleanup = Objects.requireNonNull(inputCleanup);
        this.reporter = Objects.requireNonNull(reporter);
        this.secondaryFailureSink = Objects.requireNonNull(secondaryFailureSink);
        this.shutdownScheduler = Objects.requireNonNull(shutdownScheduler);
    }

    void handle(final Throwable failure) {
        Objects.requireNonNull(failure);
        rethrowFatalFailure(failure);

        try {
            inputCleanup.run();
        } catch (final Throwable cleanupFailure) {
            preserveSecondaryFailure(failure, cleanupFailure);
        }

        boolean reportSucceeded = false;
        try {
            reporter.accept(failure);
            reportSucceeded = true;
        } catch (final Throwable reporterFailure) {
            preserveSecondaryFailure(failure, reporterFailure);
        }

        try {
            shutdownScheduler.run();
        } catch (final Throwable schedulingFailure) {
            preserveSecondaryFailure(failure, schedulingFailure);
            if (reportSucceeded) {
                surfaceSecondaryFailure(schedulingFailure);
            }
        }

        if (!reportSucceeded) {
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

    private void surfaceSecondaryFailure(final Throwable secondaryFailure) {
        try {
            secondaryFailureSink.accept(secondaryFailure);
        } catch (final Throwable sinkFailure) {
            preserveSecondaryFailure(sinkFailure, secondaryFailure);
            rethrowFatalFailure(sinkFailure);
            rethrowUnchecked(sinkFailure);
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
