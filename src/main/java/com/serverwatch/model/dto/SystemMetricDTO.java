package com.serverwatch.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of the host system's CPU, memory, swap, and disk metrics.
 */
public record SystemMetricDTO(
        double cpuUsagePercent,
        int cpuCoreCount,
        String cpuModelName,
        long memoryTotalBytes,
        long memoryUsedBytes,
        long memoryFreeBytes,
        double memoryUsagePercent,
        long swapTotalBytes,
        long swapUsedBytes,
        List<DiskInfo> diskInfos,
        Instant timestamp
) {}
