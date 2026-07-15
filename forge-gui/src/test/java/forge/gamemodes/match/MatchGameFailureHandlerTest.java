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
    void nonFatalFailureClearsInputsReportsAndSchedulesShutdownInOrder() {
        final RuntimeException failure = new RuntimeException("game failed");
        final List<String> calls = new ArrayList<>();
        final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                () -> calls.add("cleanup"),
                reported -> {
                    assertSame(failure, reported);
                    calls.add("report");
                },
                secondary -> calls.add("secondary"),
                () -> calls.add("shutdown"));

        handler.handle(failure);

        assertEquals(List.of("cleanup", "report", "shutdown"), calls);
    }

    @Test
    void cleanupFailureStillReportsOriginalAndSchedulesShutdown() {
        final RuntimeException failure = new RuntimeException("game failed");
        final RuntimeException cleanupFailure = new RuntimeException("cleanup failed");
        final List<String> calls = new ArrayList<>();
        final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                () -> {
                    calls.add("cleanup");
                    throw cleanupFailure;
                },
                reported -> {
                    assertSame(failure, reported);
                    assertEquals(List.of(cleanupFailure), List.of(reported.getSuppressed()));
                    calls.add("report");
                },
                secondary -> calls.add("secondary"),
                () -> calls.add("shutdown"));

        handler.handle(failure);

        assertEquals(List.of("cleanup", "report", "shutdown"), calls);
        assertEquals(List.of(cleanupFailure), List.of(failure.getSuppressed()));
    }

    @Test
    void reporterFailureStillSchedulesShutdown() {
        final RuntimeException failure = new RuntimeException("game failed");
        final RuntimeException reporterFailure = new RuntimeException("report failed");
        final List<String> calls = new ArrayList<>();
        final AtomicInteger primaryReporterInvocations = new AtomicInteger();
        final AtomicInteger fallbackReporterInvocations = new AtomicInteger();
        final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                () -> calls.add("cleanup"),
                reported -> {
                    primaryReporterInvocations.incrementAndGet();
                    calls.add("report");
                    throw reporterFailure;
                },
                secondary -> calls.add("secondary"),
                () -> calls.add("shutdown"));

        final RuntimeException thrown;
        try {
            handler.handle(failure);
            throw new AssertionError("Expected the original failure to reach the fallback reporter");
        } catch (final RuntimeException uncaughtFailure) {
            fallbackReporterInvocations.incrementAndGet();
            thrown = uncaughtFailure;
        }

        assertSame(failure, thrown);
        assertEquals(List.of(reporterFailure), List.of(failure.getSuppressed()));
        assertEquals(List.of("cleanup", "report", "shutdown"), calls);
        assertEquals(1, primaryReporterInvocations.get());
        assertEquals(1, fallbackReporterInvocations.get());
    }

    @Test
    void shutdownSchedulingFailureIsSuppressedAndSurfacedWithoutRepeatingPrimaryReport() {
        final RuntimeException failure = new RuntimeException("game failed");
        final RuntimeException schedulingFailure = new RuntimeException("scheduling failed");
        final List<String> calls = new ArrayList<>();
        final AtomicInteger primaryReporterInvocations = new AtomicInteger();
        final AtomicInteger fallbackReporterInvocations = new AtomicInteger();
        final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                () -> calls.add("cleanup"),
                reported -> {
                    primaryReporterInvocations.incrementAndGet();
                    calls.add("report");
                },
                secondary -> {
                    assertSame(schedulingFailure, secondary);
                    calls.add("secondary");
                },
                () -> {
                    calls.add("shutdown");
                    throw schedulingFailure;
                });

        try {
            handler.handle(failure);
        } catch (final RuntimeException uncaughtFailure) {
            fallbackReporterInvocations.incrementAndGet();
        }

        assertEquals(List.of(schedulingFailure), List.of(failure.getSuppressed()));
        assertEquals(List.of("cleanup", "report", "shutdown", "secondary"), calls);
        assertEquals(1, primaryReporterInvocations.get());
        assertEquals(0, fallbackReporterInvocations.get());
    }

    @Test
    void fatalVmFailuresAreRethrownUntouchedWithoutCleanupOrReporting() {
        final List<Error> fatalFailures = List.of(
                new TestVirtualMachineError(),
                new ThreadDeath(),
                new LinkageError("linkage failed"));

        for (final Error failure : fatalFailures) {
            final List<String> calls = new ArrayList<>();
            final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                    () -> calls.add("cleanup"),
                    reported -> calls.add("report"),
                    secondary -> calls.add("secondary"),
                    () -> calls.add("shutdown"));

            final Throwable thrown = assertThrows(Throwable.class,
                    () -> handler.handle(failure));

            assertSame(failure, thrown);
            assertEquals(List.of(), calls);
            assertEquals(0, failure.getSuppressed().length);
        }
    }

    private static final class TestVirtualMachineError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }
}
