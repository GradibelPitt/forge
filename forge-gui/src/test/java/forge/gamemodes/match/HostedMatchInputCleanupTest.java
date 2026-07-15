package forge.gamemodes.match;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostedMatchInputCleanupTest {
    @Test
    void fatalControllerCleanupEscapesUntouchedAfterEarlierNonfatalFailure() {
        final RuntimeException nonfatalFailure = new RuntimeException("first cleanup failed");
        final ThreadDeath fatalFailure = new ThreadDeath();
        final AtomicInteger cleanupAttempts = new AtomicInteger();

        final Throwable thrown = assertThrows(Throwable.class,
                () -> HostedMatch.runControllerInputCleanups(List.of(1, 2, 3), controller -> {
                    cleanupAttempts.incrementAndGet();
                    if (controller == 1) {
                        throw nonfatalFailure;
                    }
                    if (controller == 2) {
                        throw fatalFailure;
                    }
                }));

        assertSame(fatalFailure, thrown);
        assertEquals(0, fatalFailure.getSuppressed().length);
        assertEquals(2, cleanupAttempts.get());
    }
}
