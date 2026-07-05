package com.serverwatch.model.dto;

import java.time.Instant;

/**
 * System uptime and OS identification information.
 */
public record UptimeDTO(
        long uptimeSeconds,
        Instant bootTime,
        String formattedUptime,
        String osName,
        String osVersion,
        String hostname
) {}
