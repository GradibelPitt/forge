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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reads the effective Windows Firewall state without changing firewall rules.
 */
public final class WindowsFirewallInspector {
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);
    private static final String FIREWALL_PROGRAM_ENV = "FORGE_FIREWALL_PROGRAM";
    private static final String FIREWALL_PORT_ENV = "FORGE_FIREWALL_PORT";
    private static final String POWERSHELL_QUERY = """
            $ErrorActionPreference = 'Stop'
            [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
            $firewall = New-Object -ComObject HNetCfg.FwPolicy2
            $activeProfiles = [int]$firewall.CurrentProfileTypes
            $enabledProfiles = 0
            foreach ($profile in @(1, 2, 4)) {
                if (($activeProfiles -band $profile) -ne 0 -and $firewall.FirewallEnabled($profile)) {
                    $enabledProfiles = $enabledProfiles -bor $profile
                }
            }
            $matchingRule = 0
            if ($enabledProfiles -ne 0) {
                $program = $env:FORGE_FIREWALL_PROGRAM
                $port = [int]$env:FORGE_FIREWALL_PORT
                foreach ($rule in $firewall.Rules) {
                    try {
                        if (-not $rule.Enabled -or [int]$rule.Direction -ne 1 -or [int]$rule.Action -ne 1) { continue }
                        $ruleProfiles = [int]$rule.Profiles
                        if ($ruleProfiles -ne 2147483647 -and ($ruleProfiles -band $activeProfiles) -eq 0) { continue }
                        $application = [Environment]::ExpandEnvironmentVariables([string]$rule.ApplicationName)
                        $programMatch = -not [string]::IsNullOrWhiteSpace($program) -and
                            -not [string]::IsNullOrWhiteSpace($application) -and
                            [string]::Equals($application, $program, [StringComparison]::OrdinalIgnoreCase)
                        $portMatch = $false
                        if ([int]$rule.Protocol -eq 6 -or [int]$rule.Protocol -eq 256) {
                            foreach ($part in ([string]$rule.LocalPorts).Split(',')) {
                                $part = $part.Trim()
                                if ($part -eq '*' -or $part -eq [string]$port) { $portMatch = $true; break }
                                if ($part -match '^(\\d+)-(\\d+)$' -and $port -ge [int]$Matches[1] -and $port -le [int]$Matches[2]) {
                                    $portMatch = $true
                                    break
                                }
                            }
                        }
                        if ($programMatch -or $portMatch) { $matchingRule = 1; break }
                    } catch { }
                }
            }
            Write-Output "ACTIVE=$activeProfiles"
            Write-Output "ENABLED=$enabledProfiles"
            Write-Output "ALLOW_RULE=$matchingRule"
            """;

    public enum State {
        NOT_WINDOWS,
        DISABLED,
        ENABLED_WITH_ALLOW_RULE,
        ENABLED_WITHOUT_ALLOW_RULE,
        UNKNOWN
    }

    public record Result(State state, int activeProfiles, int enabledProfiles, String detail) {
    }

    private WindowsFirewallInspector() {
    }

    public static Result inspect(final int port) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return new Result(State.NOT_WINDOWS, 0, 0, "not-windows");
        }
        if (port <= 0 || port > 65535) {
            return new Result(State.UNKNOWN, 0, 0, "invalid-port");
        }

        final String encodedQuery = Base64.getEncoder().encodeToString(
                POWERSHELL_QUERY.getBytes(StandardCharsets.UTF_16LE));
        final ProcessBuilder processBuilder = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encodedQuery);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put(FIREWALL_PORT_ENV, Integer.toString(port));
        ProcessHandle.current().info().command().ifPresent(command ->
                processBuilder.environment().put(FIREWALL_PROGRAM_ENV, command));

        Process process = null;
        try {
            process = processBuilder.start();
            if (!process.waitFor(QUERY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new Result(State.UNKNOWN, 0, 0, "timeout");
            }
            final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new Result(State.UNKNOWN, 0, 0, compactDetail(output));
            }
            return parse(output);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(State.UNKNOWN, 0, 0, "interrupted");
        } catch (final IOException | RuntimeException e) {
            return new Result(State.UNKNOWN, 0, 0, compactDetail(e.getMessage()));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    static Result parse(final String output) {
        final Map<String, Integer> values = new HashMap<>();
        for (final String line : output.split("\\R")) {
            final int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            try {
                values.put(line.substring(0, equals).trim(), Integer.parseInt(line.substring(equals + 1).trim()));
            } catch (final NumberFormatException ignored) {
            }
        }
        if (!values.containsKey("ACTIVE") || !values.containsKey("ENABLED") || !values.containsKey("ALLOW_RULE")) {
            return new Result(State.UNKNOWN, 0, 0, compactDetail(output));
        }
        final int activeProfiles = values.get("ACTIVE");
        final int enabledProfiles = values.get("ENABLED");
        if (enabledProfiles == 0) {
            return new Result(State.DISABLED, activeProfiles, enabledProfiles, "");
        }
        final State state = values.get("ALLOW_RULE") == 1
                ? State.ENABLED_WITH_ALLOW_RULE
                : State.ENABLED_WITHOUT_ALLOW_RULE;
        return new Result(state, activeProfiles, enabledProfiles, "");
    }

    private static String compactDetail(final String detail) {
        if (detail == null || detail.isBlank()) {
            return "no-details";
        }
        final String compact = detail.replaceAll("\\s+", " ").trim();
        return compact.length() <= 160 ? compact : compact.substring(0, 160);
    }
}
