package com.serverwatch.model.dto;

/**
 * Represents a single host↔container port binding.
 */
public record PortMapping(
        int    privatePort,   // container-side port
        int    publicPort,    // host-side port (0 if not published)
        String type,          // "tcp" or "udp"
        String ip             // host binding IP, e.g. "0.0.0.0"
) {}
