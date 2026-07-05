package com.serverwatch.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Summary view of a Docker container — returned by list and inspect endpoints.
 *
 * <p>{@code envVars} is populated only for the full-inspect endpoint;
 * the container-list endpoint returns an empty list to avoid per-container
 * inspect round-trips.
 */
public record ContainerInfoDTO(
        String              containerId,      // short 12-char ID
        String              containerIdFull,  // full 64-char SHA
        String              name,             // without leading "/"
        String              image,
        String              state,            // running / exited / paused / restarting
        String              status,           // human-readable, e.g. "Up 3 hours"
        Instant             created,
        List<PortMapping>   ports,
        List<String>        networks,
        List<String>        volumes,
        Map<String, String> labels,
        List<String>        envVars
) {}
