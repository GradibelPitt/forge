package forge.gamemodes.match;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Finishes and publishes a failed match from the UI thread.
 */
final class MatchGameFailureFinalizer {
    private final BooleanSupplier failedGameIsCurrent;
    private final Runnable gameShutdown;
    private final Runnable matchOverMarker;
    private final Consumer<Throwable> failurePublisher;
    private final Consumer<Throwable> secondaryFailureSink;

    MatchGameFailureFinalizer(final BooleanSupplier failedGameIsCurrent, final Runnable gameShutdown,
            final Runnable matchOverMarker, final Consumer<Throwable> failurePublisher,
            final Consumer<Throwable> secondaryFailureSink) {
        this.failedGameIsCurrent = Objects.requireNonNull(failedGameIsCurrent);
        this.gameShutdown = Objects.requireNonNull(gameShutdown);
        this.matchOverMarker = Objects.requireNonNull(matchOverMarker);
        this.failurePublisher = Objects.requireNonNull(failurePublisher);
        this.secondaryFailureSink = Objects.requireNonNull(secondaryFailureSink);
    }

    void finish(final Throwable originalFailure) {
        Objects.requireNonNull(originalFailure);
        MatchGameFailureHandler.rethrowFatalFailure(originalFailure);

        if (isFailedGameCurrent(originalFailure)) {
            shutdownAndMarkMatchOver(originalFailure);
        }
        publishFailure(originalFailure);
    }

    private boolean isFailedGameCurrent(final Throwable originalFailure) {
        try {
            return failedGameIsCurrent.getAsBoolean();
        } catch (final Throwable identityFailure) {
            MatchGameFailureHandler.rethrowFatalFailure(identityFailure);
            MatchGameFailureHandler.preserveSecondaryFailure(originalFailure, identityFailure);
            return false;
        }
    }

    private void shutdownAndMarkMatchOver(final Throwable originalFailure) {
        Throwable fatalShutdownFailure = null;
        try {
            gameShutdown.run();
        } catch (final Throwable shutdownFailure) {
            if (MatchGameFailureHandler.isFatalFailure(shutdownFailure)) {
                fatalShutdownFailure = shutdownFailure;
            } else {
                MatchGameFailureHandler.preserveSecondaryFailure(originalFailure, shutdownFailure);
            }
        } finally {
            if (fatalShutdownFailure == null) {
                try {
                    matchOverMarker.run();
                } catch (final Throwable markerFailure) {
                    MatchGameFailureHandler.rethrowFatalFailure(markerFailure);
                    MatchGameFailureHandler.preserveSecondaryFailure(originalFailure, markerFailure);
                }
            }
        }
        if (fatalShutdownFailure != null) {
            MatchGameFailureHandler.rethrowFatalFailure(fatalShutdownFailure);
        }
    }

    private void publishFailure(final Throwable originalFailure) {
        try {
            failurePublisher.accept(originalFailure);
        } catch (final Throwable publisherFailure) {
            MatchGameFailureHandler.rethrowFatalFailure(publisherFailure);
            MatchGameFailureHandler.preserveSecondaryFailure(originalFailure, publisherFailure);
            surfacePublisherFailure(publisherFailure);
        }
    }

    private void surfacePublisherFailure(final Throwable publisherFailure) {
        try {
            secondaryFailureSink.accept(publisherFailure);
        } catch (final Throwable sinkFailure) {
            MatchGameFailureHandler.rethrowFatalFailure(sinkFailure);
            MatchGameFailureHandler.preserveSecondaryFailure(sinkFailure, publisherFailure);
            System.err.println("A secondary failure sink failed while finalizing a match:");
            sinkFailure.printStackTrace(System.err);
        }
    }
}
