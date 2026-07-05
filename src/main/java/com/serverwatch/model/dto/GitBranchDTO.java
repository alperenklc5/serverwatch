package com.serverwatch.model.dto;

import java.time.Instant;

/**
 * Represents a local or remote Git branch with tracking information.
 */
public record GitBranchDTO(
        String name,
        boolean isRemote,
        boolean isCurrent,
        String lastCommitHash,
        String lastCommitMessage,
        Instant lastCommitDate,
        String trackingBranch,
        int ahead,
        int behind
) {}
