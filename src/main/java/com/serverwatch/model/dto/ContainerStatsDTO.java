package com.serverwatch.model.dto;

import java.time.Instant;

/**
 * Live resource-usage snapshot for a single running container.
 * CPU percentage is computed via the delta method from Docker stats.
 */
public record ContainerStatsDTO(
        String  containerId,
        String  containerName,
        double  cpuPercent,
        long    memoryUsageBytes,
        long    memoryLimitBytes,
        double  memoryPercent,
        long    networkRxBytes,
        long    networkTxBytes,
        long    blockReadBytes,
        long    blockWriteBytes,
        int     pidCount,
        Instant timestamp
) {}
