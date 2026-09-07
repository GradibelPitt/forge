package forge.gamemodes.net.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowsFirewallInspectorTest {
    @Test
    void reportsDisabledWhenNoActiveFirewallProfileIsEnabled() {
        final WindowsFirewallInspector.Result result = WindowsFirewallInspector.parse(
                "ACTIVE=6\r\nENABLED=0\r\nALLOW_RULE=0\r\n");

        assertEquals(WindowsFirewallInspector.State.DISABLED, result.state());
        assertEquals(6, result.activeProfiles());
    }

    @Test
    void distinguishesMatchingAllowRuleFromMissingRule() {
        assertEquals(WindowsFirewallInspector.State.ENABLED_WITH_ALLOW_RULE,
                WindowsFirewallInspector.parse("ACTIVE=4\nENABLED=4\nALLOW_RULE=1\n").state());
        assertEquals(WindowsFirewallInspector.State.ENABLED_WITHOUT_ALLOW_RULE,
                WindowsFirewallInspector.parse("ACTIVE=4\nENABLED=4\nALLOW_RULE=0\n").state());
    }

    @Test
    void malformedOutputIsUnknown() {
        assertEquals(WindowsFirewallInspector.State.UNKNOWN,
                WindowsFirewallInspector.parse("access denied").state());
    }
}
