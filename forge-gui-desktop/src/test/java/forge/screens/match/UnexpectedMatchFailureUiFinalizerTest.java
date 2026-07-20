package forge.screens.match;

import forge.gamemodes.match.MatchGameFailureCleanup;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class UnexpectedMatchFailureUiFinalizerTest {
    @Test
    public void cleanupFailureCannotPreventForcedCloseAndExplicitPopup() {
        final RuntimeException cleanupFailure = new RuntimeException("overlay cleanup failed");
        final List<String> calls = new ArrayList<>();

        final RuntimeException thrown = Assert.expectThrows(RuntimeException.class,
                () -> MatchGameFailureCleanup.runAll(List.of(
                        () -> {
                            calls.add("overlay");
                            throw cleanupFailure;
                        },
                        () -> calls.add("forced-close"),
                        () -> calls.add("error-popup"))));

        Assert.assertSame(thrown, cleanupFailure);
        Assert.assertEquals(calls,
                List.of("overlay", "forced-close", "error-popup"));
    }
}
