package com.serverwatch.model.dto;

/**
 * Generic request body for container lifecycle actions
 * (start / stop / restart / pause / unpause / remove).
 */
public record ContainerActionDTO(
        String  containerId,
        String  action,
        boolean force,
        int     timeout      // seconds before forced kill (stop/restart)
) {}
