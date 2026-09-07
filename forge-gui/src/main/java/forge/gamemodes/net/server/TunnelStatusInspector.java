/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package forge.gamemodes.net.server;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads the route and reverse-tunnel status published by the desktop runtime.
 */
public final class TunnelStatusInspector {
    private static final String STATUS_FILE_PROPERTY = "forge.net.tunnelStatusFile";

    public enum State {
        NOT_CONFIGURED,
        CLASH_TUN_ACTIVE,
        CLASH_TUN_BLOCKED,
        SYSTEM_ROUTE,
        UNKNOWN
    }

    public record Result(
            State state,
            String code,
            String stage,
            String blockedAt,
            int localPort,
            String publicHost,
            int publicPort,
            String proxyProvider,
            String proxyMode,
            String proxyRoute,
            String relayHost,
            String relayDns,
            String resolvedAddress,
            String relayTcp,
            String connectionPath,
            String detail) {
    }

    private TunnelStatusInspector() {
    }

    public static Result inspect() {
        final String configuredPath = System.getProperty(STATUS_FILE_PROPERTY, "").trim();
        if (configuredPath.isEmpty()) {
            return empty(State.NOT_CONFIGURED, "runtime-status-not-configured");
        }

        final Path statusPath;
        try {
            statusPath = Path.of(configuredPath);
        } catch (final RuntimeException e) {
            return empty(State.UNKNOWN, compactDetail(e.getMessage()));
        }
        if (!Files.isRegularFile(statusPath)) {
            return empty(State.UNKNOWN, "status-file-not-ready");
        }

        final Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(statusPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return parse(properties, ProcessHandle.current().pid());
        } catch (final IOException | RuntimeException e) {
            return empty(State.UNKNOWN, compactDetail(e.getMessage()));
        }
    }

    static Result parse(final Properties properties, final long currentProcessId) {
        final long ownerProcessId = parseLong(properties.getProperty("ownerProcessId"));
        if (ownerProcessId > 0 && currentProcessId > 0 && ownerProcessId != currentProcessId) {
            return empty(State.UNKNOWN, "status-owner-mismatch");
        }

        final String proxyProvider = value(properties, "proxyProvider");
        final String proxyMode = value(properties, "proxyMode");
        final String proxyRoute = value(properties, "proxyRoute");
        final State state;
        if (!"NONE".equals(proxyProvider) && "TUN".equals(proxyMode) && "ACTIVE".equals(proxyRoute)) {
            state = State.CLASH_TUN_ACTIVE;
        } else if (!"NONE".equals(proxyProvider) && "MISSING".equals(proxyRoute)) {
            state = State.CLASH_TUN_BLOCKED;
        } else if ("NONE".equals(proxyProvider) && "DIRECT".equals(proxyRoute)) {
            state = State.SYSTEM_ROUTE;
        } else {
            state = State.UNKNOWN;
        }

        return new Result(
                state,
                value(properties, "code"),
                value(properties, "stage"),
                value(properties, "blockedAt"),
                parseInt(properties.getProperty("localPort")),
                value(properties, "publicHost"),
                parseInt(properties.getProperty("publicPort")),
                proxyProvider,
                proxyMode,
                proxyRoute,
                value(properties, "relayHost"),
                value(properties, "relayDns"),
                value(properties, "resolvedAddress"),
                value(properties, "relayTcp"),
                value(properties, "connectionPath"),
                compactDetail(value(properties, "detail")));
    }

    private static Result empty(final State state, final String detail) {
        return new Result(state, "", "", "", 0, "", 0,
                "", "", "", "", "", "", "", "", detail);
    }

    private static String value(final Properties properties, final String key) {
        return properties.getProperty(key, "").trim();
    }

    private static int parseInt(final String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private static long parseLong(final String value) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private static String compactDetail(final String detail) {
        if (detail == null || detail.isBlank()) {
            return "no-details";
        }
        final String compact = detail.replaceAll("\\s+", " ").trim();
        return compact.length() <= 200 ? compact : compact.substring(0, 200);
    }
}
