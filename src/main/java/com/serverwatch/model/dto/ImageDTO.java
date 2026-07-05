package com.serverwatch.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Simplified Docker image descriptor.
 */
public record ImageDTO(
        String       imageId,    // short 12-char ID
        List<String> repoTags,
        double       sizeMb,
        Instant      created
) {}
