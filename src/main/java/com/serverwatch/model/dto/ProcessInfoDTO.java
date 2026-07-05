package com.serverwatch.model.dto;

import java.time.Instant;

/**
 * Information about a single OS process.
 */
public record ProcessInfoDTO(
        int pid,
        String name,
        double cpuPercent,
        long memoryBytes,
        double memoryPercent,
        String user,
        String state,
        Instant startTime,
        String commandLine
) {}
