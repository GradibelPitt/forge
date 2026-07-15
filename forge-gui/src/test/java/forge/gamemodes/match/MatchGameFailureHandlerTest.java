package forge.gamemodes.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
        final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                () -> calls.add("cleanup"),
                reported -> {
                    calls.add("report");
                    throw reporterFailure;
                },
                () -> calls.add("shutdown"));

        final RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> handler.handle(failure));

        assertSame(failure, thrown);
        assertEquals(List.of(reporterFailure), List.of(failure.getSuppressed()));
        assertEquals(List.of("cleanup", "report", "shutdown"), calls);
    }

    @Test
    void shutdownSchedulingFailureIsSuppressedAndOriginalFailureIsRethrown() {
        final RuntimeException failure = new RuntimeException("game failed");
        final RuntimeException schedulingFailure = new RuntimeException("scheduling failed");
        final List<String> calls = new ArrayList<>();
        final MatchGameFailureHandler handler = new MatchGameFailureHandler(
                () -> calls.add("cleanup"),
                reported -> calls.add("report"),
                () -> {
                    calls.add("shutdown");
                    throw schedulingFailure;
                });

        final RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> handler.handle(failure));

        assertSame(failure, thrown);
        assertEquals(List.of(schedulingFailure), List.of(failure.getSuppressed()));
        assertEquals(List.of("cleanup", "report", "shutdown"), calls);
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
