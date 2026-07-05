package com.serverwatch.model.dto;

import java.util.List;

/**
 * Full diff for a single commit, including per-file patch entries.
 */
public record GitDiffDTO(
        String commitHash,
        List<GitDiffEntryDTO> entries
) {}
