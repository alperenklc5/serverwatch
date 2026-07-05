package com.serverwatch.model.dto;

/**
 * Docker daemon system information.
 */
public record DockerInfoDTO(
        String  dockerVersion,
        String  apiVersion,
        int     runningContainers,
        int     pausedContainers,
        int     stoppedContainers,
        int     totalImages,
        String  storageDriver,
        String  operatingSystem,
        String  architecture,
        long    totalMemory,
        String  hostname
) {}
