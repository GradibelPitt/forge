package forge.gamemodes.match;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Cleans up game-thread inputs and hands a nonfatal match failure to an EDT finalizer.
 */
final class MatchGameFailureHandler {
    private final Runnable inputCleanup;
    private final Consumer<Throwable> finalizerScheduler;

    MatchGameFailureHandler(final Runnable inputCleanup, final Consumer<Throwable> finalizerScheduler) {
        this.inputCleanup = Objects.requireNonNull(inputCleanup);
        this.finalizerScheduler = Objects.requireNonNull(finalizerScheduler);
    }

    void handle(final Throwable failure) {
        Objects.requireNonNull(failure);
        rethrowFatalFailure(failure);

        try {
            inputCleanup.run();
        } catch (final Throwable cleanupFailure) {
            rethrowFatalFailure(cleanupFailure);
            preserveSecondaryFailure(failure, cleanupFailure);
        }

        try {
            finalizerScheduler.accept(failure);
        } catch (final Throwable schedulingFailure) {
            rethrowFatalFailure(schedulingFailure);
            preserveSecondaryFailure(failure, schedulingFailure);
            rethrowUnchecked(failure);
        }
    }

    static void rethrowFatalFailure(final Throwable failure) {
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

    static boolean isFatalFailure(final Throwable failure) {
        return failure instanceof VirtualMachineError
                || failure instanceof ThreadDeath
                || failure instanceof LinkageError;
    }

    static void preserveSecondaryFailure(final Throwable failure, final Throwable secondaryFailure) {
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
