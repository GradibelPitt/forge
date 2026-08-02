package forge.gamemodes.net.server;

import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.DeviceIdentity;
import org.jupnp.model.meta.Icon;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.LocalService;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceId;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.model.types.UDN;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FServerManagerUpnpTest {
    @Test
    void discoversWanIpConnectionOnIgdV2Router() throws Exception {
        final LocalService<?> wanIpConnection = new LocalService<>(
                new UDAServiceType("WANIPConnection", 2),
                new UDAServiceId("WANIPConn1"),
                null,
                null);
        final LocalDevice connectionDevice = device(
                "connection",
                "WANConnectionDevice",
                wanIpConnection,
                new LocalDevice[0]);
        final LocalDevice wanDevice = device(
                "wan",
                "WANDevice",
                null,
                new LocalDevice[] {connectionDevice});
        final LocalDevice gateway = device(
                "gateway",
                "InternetGatewayDevice",
                null,
                new LocalDevice[] {wanDevice});

        final Service<?, ?> discovered = FServerManager.discoverIgdV2ConnectionService(gateway);

        assertSame(wanIpConnection, discovered);
    }

    @Test
    void ignoresNonGatewayDevices() throws Exception {
        final LocalDevice unrelatedDevice = device(
                "media",
                "MediaRenderer",
                null,
                new LocalDevice[0]);

        assertNull(FServerManager.discoverIgdV2ConnectionService(unrelatedDevice));
    }

    private static LocalDevice device(final String id,
                                      final String type,
                                      final LocalService<?> service,
                                      final LocalDevice[] embeddedDevices) throws Exception {
        final LocalService<?>[] services = service == null
                ? new LocalService<?>[0]
                : new LocalService<?>[] {service};
        return new LocalDevice(
                new DeviceIdentity(new UDN(id)),
                new UDADeviceType(type, 2),
                new DeviceDetails(id),
                new Icon[0],
                services,
                embeddedDevices);
    }
}
