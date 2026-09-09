package forge.ai;

import org.testng.Assert;
import org.testng.annotations.Test;

public class AIOptionCompatibilityTest {
    @Test
    public void legacyToggleSelectsTheOfficialFullSimulationMode() {
        Assert.assertSame(AIOption.USE_SIMULATION, AIOption.USE_FULL_SIMULATION);
    }
}
