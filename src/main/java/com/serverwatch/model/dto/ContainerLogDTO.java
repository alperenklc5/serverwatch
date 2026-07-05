package com.serverwatch.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of container log output from a single fetch (not streaming).
 */
public record ContainerLogDTO(
        String       containerId,
        List<String> lines,
        Instant      since,
        boolean      stdout,
        boolean      stderr
) {}
