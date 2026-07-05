package com.serverwatch.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Represents a single Git commit with optional diff stats.
 */
public record GitCommitDTO(
        String hash,
        String shortHash,
        String message,
        String author,
        String authorEmail,
        Instant date,
        List<String> parentHashes,
        int filesChanged,
        int insertions,
        int deletions
) {}
