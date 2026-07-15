package forge.gamemodes.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchGameFailureFinalizerTest {
    @Test
    void shutdownFailureStillMarksMatchOverThenReportsOriginalExactlyOnce() {
        final RuntimeException original = new RuntimeException("game failed");
        final RuntimeException shutdownFailure = new RuntimeException("shutdown failed");
        final List<String> calls = new ArrayList<>();
        final AtomicInteger reports = new AtomicInteger();
        final MatchGameFailureFinalizer finalizer = new MatchGameFailureFinalizer(
                () -> true,
                () -> {
                    calls.add("shutdown");
                    throw shutdownFailure;
                },
                () -> calls.add("mark-over"),
                reported -> {
                    reports.incrementAndGet();
                    assertSame(original, reported);
                    assertEquals(List.of(shutdownFailure), List.of(reported.getSuppressed()));
                    calls.add("report");
                },
                secondary -> calls.add("secondary"));

        finalizer.finish(original);

        assertEquals(List.of("shutdown", "mark-over", "report"), calls);
        assertEquals(1, reports.get());
    }

    @Test
    void reporterFailureUsesSecondarySinkWithoutEscapingOriginalAgain() {
        final RuntimeException original = new RuntimeException("game failed");
        final RuntimeException reporterFailure = new RuntimeException("report failed");
        final List<String> calls = new ArrayList<>();
        final MatchGameFailureFinalizer finalizer = new MatchGameFailureFinalizer(
                () -> true,
                () -> calls.add("shutdown"),
                () -> calls.add("mark-over"),
                reported -> {
                    calls.add("report");
                    throw reporterFailure;
                },
                secondary -> {
                    assertSame(reporterFailure, secondary);
                    calls.add("secondary");
                });

        finalizer.finish(original);

        assertEquals(List.of("shutdown", "mark-over", "report", "secondary"), calls);
        assertEquals(List.of(reporterFailure), List.of(original.getSuppressed()));
    }

    @Test
    void fatalShutdownOrMarkFailureEscapesUntouchedWithoutFurtherCallbacks() {
        final TestVirtualMachineError fatalShutdown = new TestVirtualMachineError();
        final List<String> shutdownCalls = new ArrayList<>();
        final MatchGameFailureFinalizer shutdownFinalizer = new MatchGameFailureFinalizer(
                () -> true,
                () -> {
                    shutdownCalls.add("shutdown");
                    throw fatalShutdown;
                },
                () -> shutdownCalls.add("mark-over"),
                reported -> shutdownCalls.add("report"),
                secondary -> shutdownCalls.add("secondary"));

        assertSame(fatalShutdown, assertThrows(Throwable.class,
                () -> shutdownFinalizer.finish(new RuntimeException("game failed"))));
        assertEquals(List.of("shutdown"), shutdownCalls);

        final LinkageError fatalMark = new LinkageError("mark failed");
        final List<String> markCalls = new ArrayList<>();
        final MatchGameFailureFinalizer markFinalizer = new MatchGameFailureFinalizer(
                () -> true,
                () -> markCalls.add("shutdown"),
                () -> {
                    markCalls.add("mark-over");
                    throw fatalMark;
                },
                reported -> markCalls.add("report"),
                secondary -> markCalls.add("secondary"));

        assertSame(fatalMark, assertThrows(Throwable.class,
                () -> markFinalizer.finish(new RuntimeException("game failed"))));
        assertEquals(List.of("shutdown", "mark-over"), markCalls);
    }

    @Test
    void fatalReporterOrSecondarySinkFailureEscapesUntouched() {
        final ThreadDeath fatalReporter = new ThreadDeath();
        final List<String> reporterCalls = new ArrayList<>();
        final MatchGameFailureFinalizer reporterFinalizer = new MatchGameFailureFinalizer(
                () -> false,
                () -> reporterCalls.add("shutdown"),
                () -> reporterCalls.add("mark-over"),
                reported -> {
                    reporterCalls.add("report");
                    throw fatalReporter;
                },
                secondary -> reporterCalls.add("secondary"));

        assertSame(fatalReporter, assertThrows(Throwable.class,
                () -> reporterFinalizer.finish(new RuntimeException("game failed"))));
        assertEquals(List.of("report"), reporterCalls);

        final RuntimeException reporterFailure = new RuntimeException("report failed");
        final LinkageError fatalSink = new LinkageError("sink failed");
        final List<String> sinkCalls = new ArrayList<>();
        final MatchGameFailureFinalizer sinkFinalizer = new MatchGameFailureFinalizer(
                () -> false,
                () -> sinkCalls.add("shutdown"),
                () -> sinkCalls.add("mark-over"),
                reported -> {
                    sinkCalls.add("report");
                    throw reporterFailure;
                },
                secondary -> {
                    sinkCalls.add("secondary");
                    throw fatalSink;
                });

        assertSame(fatalSink, assertThrows(Throwable.class,
                () -> sinkFinalizer.finish(new RuntimeException("game failed"))));
        assertEquals(List.of("report", "secondary"), sinkCalls);
    }

    private static final class TestVirtualMachineError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }
}
