package com.serverwatch.model.dto;

/**
 * Represents a single file-system mount point's usage.
 */
public record DiskInfo(
        String name,
        String mountPoint,
        long totalBytes,
        long usableBytes,
        double usagePercent,
        String type
) {}
