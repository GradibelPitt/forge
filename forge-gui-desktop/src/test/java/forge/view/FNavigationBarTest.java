package forge.view;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class FNavigationBarTest {
    @Test
    public void forcedCloseBypassesTheScreenCloseHandler() {
        final AtomicInteger closeHandlerCalls = new AtomicInteger();

        Assert.assertTrue(TabClosePolicy.allowClose(true, () -> {
            closeHandlerCalls.incrementAndGet();
            return false;
        }));
        Assert.assertEquals(closeHandlerCalls.get(), 0);

        Assert.assertFalse(TabClosePolicy.allowClose(false, () -> {
            closeHandlerCalls.incrementAndGet();
            return false;
        }));
        Assert.assertEquals(closeHandlerCalls.get(), 1);
    }
}
