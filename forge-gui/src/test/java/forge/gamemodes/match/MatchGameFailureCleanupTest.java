package forge.gamemodes.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchGameFailureCleanupTest {
    @Test
    void nonfatalCleanupFailuresDoNotPreventLaterShutdownSteps() {
        final RuntimeException firstFailure = new RuntimeException("timer cleanup failed");
        final RuntimeException secondFailure = new RuntimeException("link cleanup failed");
        final List<String> calls = new ArrayList<>();

        final RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> MatchGameFailureCleanup.runAll(List.of(
                        () -> {
                            calls.add("timer");
                            throw firstFailure;
                        },
                        () -> calls.add("forced-close"),
                        () -> {
                            calls.add("links");
                            throw secondFailure;
                        },
                        () -> calls.add("error-popup"))));

        assertSame(firstFailure, thrown);
        assertEquals(List.of(secondFailure), List.of(thrown.getSuppressed()));
        assertEquals(List.of("timer", "forced-close", "links", "error-popup"), calls);
    }
}
