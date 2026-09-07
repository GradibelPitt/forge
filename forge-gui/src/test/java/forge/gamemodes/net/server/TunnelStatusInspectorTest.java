package forge.gamemodes.net.server;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TunnelStatusInspectorTest {
    @Test
    void recognizesActiveClashTunRoute() {
        final Properties properties = properties(
                "proxyProvider", "CLASH_VERGE",
                "proxyMode", "TUN",
                "proxyRoute", "ACTIVE",
                "code", "CONFIG_MISSING",
                "relayDns", "OK",
                "resolvedAddress", "198.18.0.18",
                "relayTcp", "OK");

        final TunnelStatusInspector.Result result = TunnelStatusInspector.parse(properties, 1234);

        assertEquals(TunnelStatusInspector.State.CLASH_TUN_ACTIVE, result.state());
        assertEquals("CONFIG_MISSING", result.code());
        assertEquals("198.18.0.18", result.resolvedAddress());
    }

    @Test
    void distinguishesRunningClashWithoutTunRoute() {
        final Properties properties = properties(
                "proxyProvider", "CLASH_VERGE",
                "proxyMode", "NONE",
                "proxyRoute", "MISSING",
                "code", "CLASH_TUN_NOT_READY",
                "blockedAt", "TUN_ROUTE");

        final TunnelStatusInspector.Result result = TunnelStatusInspector.parse(properties, 1234);

        assertEquals(TunnelStatusInspector.State.CLASH_TUN_BLOCKED, result.state());
        assertEquals("TUN_ROUTE", result.blockedAt());
    }

    @Test
    void rejectsStatusFromAnotherForgeProcess() {
        final Properties properties = properties(
                "ownerProcessId", "9999",
                "proxyProvider", "CLASH_VERGE",
                "proxyMode", "TUN",
                "proxyRoute", "ACTIVE");

        final TunnelStatusInspector.Result result = TunnelStatusInspector.parse(properties, 1234);

        assertEquals(TunnelStatusInspector.State.UNKNOWN, result.state());
        assertEquals("status-owner-mismatch", result.detail());
    }

    @Test
    void recognizesSystemRouteWithoutProxy() {
        final Properties properties = properties(
                "proxyProvider", "NONE",
                "proxyMode", "DIRECT",
                "proxyRoute", "DIRECT",
                "code", "WAITING_FOR_HOST");

        assertEquals(TunnelStatusInspector.State.SYSTEM_ROUTE,
                TunnelStatusInspector.parse(properties, 1234).state());
    }

    private static Properties properties(final String... values) {
        final Properties properties = new Properties();
        for (int i = 0; i < values.length; i += 2) {
            properties.setProperty(values[i], values[i + 1]);
        }
        return properties;
    }
}
