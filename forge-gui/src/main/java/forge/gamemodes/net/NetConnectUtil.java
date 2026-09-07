package forge.gamemodes.net;

import forge.gamemodes.match.AbstractGuiGame;
import forge.gamemodes.match.GameLobby.GameLobbyData;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.client.ClientGameLobby;
import forge.gamemodes.net.client.FGameClient;
import forge.gamemodes.net.event.IdentifiableNetEvent;
import forge.gamemodes.net.event.MessageEvent;
import forge.gamemodes.net.event.NetEvent;
import forge.gamemodes.net.server.FServerManager;
import forge.gamemodes.net.server.TunnelStatusInspector;
import forge.gamemodes.net.server.WindowsFirewallInspector;
import forge.gamemodes.net.server.ServerGameLobby;
import forge.localinstance.properties.ForgeNetPreferences;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.gui.interfaces.ILobbyView;
import forge.gui.util.SOptionPane;
import forge.interfaces.ILobbyListener;
import forge.interfaces.IUpdateable;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.util.Localizer;
import forge.util.URLValidator;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class NetConnectUtil {
    private NetConnectUtil() { }

    /**
     * Prompt for the server address to join. Returns null if cancelled, or the address string.
     */
    public static String getJoinServerUrl() {
        final String url = SOptionPane.showInputDialog(
                Localizer.getInstance().getMessage("lblEnterServerAddress"),
                Localizer.getInstance().getMessage("lblJoinGame"));
        if (url == null || url.isEmpty()) { return null; }

        ensurePlayerName();
        return url;
    }

    /**
     * Ensure the player name is set before connecting.
     */
    public static void ensurePlayerName() {
        if (StringUtils.isBlank(FModel.getPreferences().getPref(FPref.PLAYER_NAME))) {
            GamePlayerUtil.setPlayerName();
        }
    }

    public static ChatMessage host(final IOnlineLobby onlineLobby, final IOnlineChatInterface chatInterface) {
        final FServerManager server = FServerManager.getInstance();
        final ServerGameLobby lobby = new ServerGameLobby();
        final ILobbyView view = onlineLobby.setLobby(lobby);

        server.setLobby(lobby);
        lobby.setListener(new IUpdateable() {
            @Override
            public void update(final boolean fullUpdate) {
                view.update(fullUpdate);
                server.updateLobbyState();
            }
            @Override
            public void update(final int slot, final LobbySlotType type) {}
        });
        // updateSlot already routes through the IUpdateable listener above, which calls
        // updateLobbyState; calling it again here would broadcast a duplicate LobbyUpdateEvent.
        view.setPlayerChangeListener(server::updateSlot);

        server.setLobbyListener(new ILobbyListener() {
            @Override
            public void update(final GameLobbyData state, final int slot) {
                // NO-OP, lobby connected directly
            }
            @Override
            public void message(final String source, final String message, final ChatMessage.MessageType type) {
                chatInterface.addMessage(new ChatMessage(source, message, type));
            }
            @Override
            public void close() {
                // NO-OP, server can't receive close message
            }
            @Override
            public ClientGameLobby getLobby() {
                return null;
            }
        });
        server.setDraftHandler(view.getDraftHandler());
        chatInterface.setGameClient(new IRemote() {
            @Override
            public void send(final NetEvent event) {
                if (event instanceof MessageEvent message) {
                    if (server.handleCommand(message.getMessage())) {
                        return;
                    }
                    server.broadcast(event);
                }
            }
            @Override
            public Object sendAndWait(final IdentifiableNetEvent event) {
                send(event);
                return null;
            }
        });

        NetworkLogConfig.activateNetworkLogging();
        final int actualPort;
        try {
            actualPort = server.startServer();
        } catch (final RuntimeException e) {
            NetworkLogConfig.deactivateNetworkLogging();
            throw e;
        }

        view.update(true);

        server.broadcast(new MessageEvent(server.formatAfkTimeoutMessage()));

        final Localizer localizer = Localizer.getInstance();
        final String hostingMessage = localizer.getMessage("lblHostingPortOnN", String.valueOf(actualPort));
        final WindowsFirewallInspector.Result firewall = WindowsFirewallInspector.inspect(actualPort);
        final String firewallMessage = switch (firewall.state()) {
            case DISABLED -> localizer.getMessageorUseDefault("lblFirewallDisabledForHost",
                    "Windows Firewall: disabled for the active network profile(s).");
            case ENABLED_WITH_ALLOW_RULE -> localizer.getMessageorUseDefault("lblFirewallAllowRuleForHost",
                    "Windows Firewall: enabled; an inbound allow rule matches Forge/Java or TCP port {0}.", actualPort);
            case ENABLED_WITHOUT_ALLOW_RULE -> localizer.getMessageorUseDefault("lblFirewallNoAllowRuleForHost",
                    "Windows Firewall: enabled, but no inbound allow rule was found for Forge/Java or TCP port {0}. Incoming connections may be blocked.", actualPort);
            case NOT_WINDOWS -> localizer.getMessageorUseDefault("lblFirewallUnsupportedForHost",
                    "Firewall status: automatic inspection is available on Windows only.");
            case UNKNOWN -> localizer.getMessageorUseDefault("lblFirewallUnknownForHost",
                    "Windows Firewall: status could not be read ({0}).", firewall.detail());
        };
        final String tunnelMessage = formatTunnelStatus(localizer, TunnelStatusInspector.inspect());
        return new ChatMessage(null, hostingMessage + "\n" + firewallMessage
                + (tunnelMessage.isEmpty() ? "" : "\n" + tunnelMessage));
    }

    private static String formatTunnelStatus(final Localizer localizer, final TunnelStatusInspector.Result tunnel) {
        return switch (tunnel.state()) {
            case NOT_CONFIGURED -> "";
            case CLASH_TUN_ACTIVE -> {
                if ("CONFIG_MISSING".equals(tunnel.code())) {
                    yield localizer.getMessageorUseDefault("lblClashTunConfigMissingForHost",
                            "Proxy path: active Clash Verge / Meta Tunnel detected (DNS {0}, relay TCP {1}). "
                                    + "The long-lived relay still needs a registered account and fixed public port.",
                            tunnel.relayDns(), tunnel.relayTcp());
                }
                if ("SSH_CONNECTED".equals(tunnel.code())) {
                    yield localizer.getMessageorUseDefault("lblClashTunConnectedForHost",
                            "Proxy path: Clash TUN is active; the long-lived tunnel is connected at {0}:{1} "
                                    + "and forwards to local TCP {2}.",
                            tunnel.publicHost(), tunnel.publicPort(), tunnel.localPort());
                }
                if ("FAIL".equals(tunnel.relayDns()) || "FAIL".equals(tunnel.relayTcp())) {
                    yield localizer.getMessageorUseDefault("lblClashTunRelayBlockedForHost",
                            "Proxy path: Clash TUN is active, but the relay path stopped at {0} ({1}).",
                            tunnel.blockedAt(), tunnel.detail());
                }
                yield localizer.getMessageorUseDefault("lblClashTunReadyForHost",
                        "Proxy path: active Clash Verge / Meta Tunnel detected; reverse-tunnel state is {0}.",
                        tunnel.code());
            }
            case CLASH_TUN_BLOCKED -> localizer.getMessageorUseDefault("lblClashTunBlockedForHost",
                    "Proxy path: Clash/Mihomo is running, but no active Meta Tunnel default route was found. "
                            + "Stopped at {0}; the reverse tunnel was not started.", tunnel.blockedAt());
            case SYSTEM_ROUTE -> localizer.getMessageorUseDefault("lblSystemTunnelRouteForHost",
                    "Proxy path: no active Clash TUN route was found; the relay would use the current system route.");
            case UNKNOWN -> localizer.getMessageorUseDefault("lblTunnelStatusUnknownForHost",
                    "Proxy path: runtime route status is not available yet ({0}).", tunnel.detail());
        };
    }

    public static void copyHostedServerUrl() {
        final Localizer localizer = Localizer.getInstance();
        String internalAddress = FServerManager.getLocalAddress();
        String externalAddress = FServerManager.getExternalAddress();
        final int port = FServerManager.getInstance().getPort();
        if (port <= 0) {
            return;
        }
        String internalUrl = internalAddress + ":" + port;
        String externalUrl = null;
        if (externalAddress != null) {
            externalUrl = externalAddress + ":" + port;
            GuiBase.getInterface().copyToClipboard(externalUrl);
        } else {
            GuiBase.getInterface().copyToClipboard(internalUrl);
        }

        String message;
        String title = localizer.getMessage("lblServerURL");
        List<String> options;
        int closeIndex;
        int localCopyIndex;

        if (externalUrl != null) {
            message = localizer.getMessage("lblShareURLToMakePlayerJoinServer", externalUrl, internalUrl);
            options = List.of(
                    localizer.getMessage("lblCopyExternalURL"),
                    localizer.getMessage("lblCopyLocalURL"),
                    localizer.getMessage("lblClose"));
            closeIndex = 2;
            localCopyIndex = 1;
        } else {
            message = localizer.getMessage("lblForgeUnableDetermineYourExternalIP", internalUrl);
            options = List.of(
                    localizer.getMessage("lblCopyLocalURL"),
                    localizer.getMessage("lblClose"));
            closeIndex = 1;
            localCopyIndex = 0;
        }

        int result = SOptionPane.showOptionDialog(message, title, SOptionPane.INFORMATION_ICON, options, closeIndex);
        if (externalUrl != null && result == 0) {
            GuiBase.getInterface().copyToClipboard(externalUrl);
        } else if (result == localCopyIndex) {
            GuiBase.getInterface().copyToClipboard(internalUrl);
        }
    }

    public static ChatMessage join(final String url, final IOnlineLobby onlineLobby, final IOnlineChatInterface chatInterface) {
        final IGuiGame gui = GuiBase.getInterface().getNewGuiGame();
        String hostname;
        int port;

        URLValidator.HostPort hostPort = URLValidator.parseURL(url);
        if (hostPort == null) {
            return new ChatMessage(null, ForgeConstants.INVALID_HOST_COMMAND);
        }

        hostname = hostPort.host();
        port = hostPort.port();
        if (port == -1) port = Integer.valueOf(ForgeNetPreferences.FNetPref.NET_PORT.getDefault());

        final FGameClient client = new FGameClient(FModel.getPreferences().getPref(FPref.PLAYER_NAME), gui, hostname, port);
        onlineLobby.setClient(client);
        chatInterface.setGameClient(client);
        final ClientGameLobby lobby = new ClientGameLobby();
        final ILobbyView view =  onlineLobby.setLobby(lobby);
        lobby.setListener(view);
        if (gui instanceof AbstractGuiGame agg) {
            agg.setClientLobby(lobby);
        }
        client.addLobbyListener(new ILobbyListener() {
            @Override
            public void message(final String source, final String message, final ChatMessage.MessageType type) {
                chatInterface.addMessage(new ChatMessage(source, message, type));
            }
            @Override
            public void update(final GameLobbyData state, final int slot) {
                lobby.setLocalPlayer(slot);
                lobby.setData(state);
            }
            @Override
            public void close() {
                onlineLobby.closeConn(Localizer.getInstance().getMessage("lblYourConnectionToHostWasInterrupted", url));
            }
            @Override
            public ClientGameLobby getLobby() {
                return lobby;
            }
        });
        client.setDraftHandler(view.getDraftHandler());
        view.setPlayerChangeListener((index, event) -> client.send(event));

        NetworkLogConfig.activateNetworkLogging();
        try {
            client.connect();
        }
        catch (Exception ex) {
            // Return error with details for GUI display
            String errorDetail = getConnectionErrorMessage(ex, hostname, port);
            return new ChatMessage(null, ForgeConstants.CONN_ERROR_PREFIX + errorDetail);
        }

        return new ChatMessage(null, Localizer.getInstance().getMessage("lblConnectedIPPort", hostname, String.valueOf(port)));
    }

    /**
     * Generate a user-friendly error message for connection failures.
     */
    private static String getConnectionErrorMessage(Exception ex, String hostname, int port) {
        Localizer localizer = Localizer.getInstance();
        StringBuilder sb = new StringBuilder();

        // Get the root cause for better error messages
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        String causeName = cause.getClass().getSimpleName();

        sb.append(localizer.getMessage("lblConnectionFailedTo", hostname, port));
        sb.append("\n\n");

        // Provide specific messages for common error types
        if (causeName.contains("ConnectException") || causeName.contains("ConnectionRefused")) {
            sb.append(localizer.getMessage("lblConnectionRefused"));
        } else if (causeName.contains("UnknownHost")) {
            sb.append(localizer.getMessage("lblUnknownHost"));
        } else if (causeName.contains("Timeout") || causeName.contains("TimedOut")) {
            sb.append(localizer.getMessage("lblConnectionTimeout"));
        } else if (causeName.contains("NoRouteToHost")) {
            sb.append(localizer.getMessage("lblNoRouteToHost"));
        } else {
            // Generic error with the exception message
            String msg = cause.getMessage();
            if (msg != null && !msg.isEmpty()) {
                sb.append(msg);
            } else {
                sb.append(causeName);
            }
        }

        return sb.toString();
    }
}
