package com.serverwatch.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Metrics for a single network interface, including delta-based traffic rates.
 */
public record NetworkMetricDTO(
        String interfaceName,
        String displayName,
        String macAddress,
        List<String> ipv4Addresses,
        long bytesReceived,
        long bytesSent,
        long receivedPerSecond,
        long sentPerSecond,
        long packetsReceived,
        long packetsSent,
        long speed,
        Instant timestamp
) {}
