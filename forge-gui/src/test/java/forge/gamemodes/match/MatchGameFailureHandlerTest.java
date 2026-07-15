package forge.gamemodes.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchGameFailureHandlerTest {
    @Test
    void nonFatalFailureClearsInputsAndSchedulesFinalizerInOrder() {
        final RuntimeException failure = new RuntimeException("game failed");
        final List<String> calls = new ArrayList<>();
        final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                () -> calls.add("cleanup"),
                scheduled -> {
                    assertSame(failure, scheduled);
                    calls.add("schedule-finalizer");
                });

        handler.handle(failure);

        assertEquals(List.of("cleanup", "schedule-finalizer"), calls);
    }

    @Test
    void cleanupFailureIsSuppressedBeforeFinalizerIsScheduled() {
        final RuntimeException failure = new RuntimeException("game failed");
        final RuntimeException cleanupFailure = new RuntimeException("cleanup failed");
        final List<String> calls = new ArrayList<>();
        final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                () -> {
                    calls.add("cleanup");
                    throw cleanupFailure;
                },
                scheduled -> {
                    assertSame(failure, scheduled);
                    assertEquals(List.of(cleanupFailure), List.of(scheduled.getSuppressed()));
                    calls.add("schedule-finalizer");
                });

        handler.handle(failure);

        assertEquals(List.of("cleanup", "schedule-finalizer"), calls);
    }

    @Test
    void finalizerSchedulingFailureReachesFallbackReporterExactlyOnce() {
        final RuntimeException failure = new RuntimeException("game failed");
        final RuntimeException schedulingFailure = new RuntimeException("scheduling failed");
        final AtomicInteger fallbackReports = new AtomicInteger();
        final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                () -> { },
                scheduled -> {
                    throw schedulingFailure;
                });

        final RuntimeException thrown;
        try {
            handler.handle(failure);
            throw new AssertionError("Expected the original failure to reach the fallback reporter");
        } catch (final RuntimeException uncaughtFailure) {
            fallbackReports.incrementAndGet();
            thrown = uncaughtFailure;
        }

        assertSame(failure, thrown);
        assertEquals(List.of(schedulingFailure), List.of(failure.getSuppressed()));
        assertEquals(1, fallbackReports.get());
    }

    @Test
    void fatalPrimaryFailuresAreRethrownUntouchedWithoutCallbacks() {
        for (final Error failure : fatalFailures()) {
            final List<String> calls = new ArrayList<>();
            final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                    () -> calls.add("cleanup"),
                    scheduled -> calls.add("schedule-finalizer"));

            final Throwable thrown = assertThrows(Throwable.class, () -> handler.handle(failure));

            assertSame(failure, thrown);
            assertEquals(List.of(), calls);
        }
    }

    @Test
    void fatalCleanupFailuresEscapeUntouchedWithoutScheduling() {
        for (final Error fatalCleanup : fatalFailures()) {
            final List<String> calls = new ArrayList<>();
            final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                    () -> {
                        calls.add("cleanup");
                        throw fatalCleanup;
                    },
                    scheduled -> calls.add("schedule-finalizer"));

            final Throwable thrown = assertThrows(Throwable.class,
                    () -> handler.handle(new RuntimeException("game failed")));

            assertSame(fatalCleanup, thrown);
            assertEquals(List.of("cleanup"), calls);
        }
    }

    @Test
    void fatalSchedulingFailuresEscapeUntouched() {
        for (final Error fatalScheduling : fatalFailures()) {
            final List<String> calls = new ArrayList<>();
            final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                    () -> calls.add("cleanup"),
                    scheduled -> {
                        calls.add("schedule-finalizer");
                        throw fatalScheduling;
                    });

            final Throwable thrown = assertThrows(Throwable.class,
                    () -> handler.handle(new RuntimeException("game failed")));

            assertSame(fatalScheduling, thrown);
            assertEquals(List.of("cleanup", "schedule-finalizer"), calls);
        }
    }

    private static List<Error> fatalFailures() {
        return List.of(new TestVirtualMachineError(), new ThreadDeath(), new LinkageError("linkage failed"));
    }

    private static final class TestVirtualMachineError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }
}
