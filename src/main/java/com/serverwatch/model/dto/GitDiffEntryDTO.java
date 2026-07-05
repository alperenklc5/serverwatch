package com.serverwatch.model.dto;

/**
 * Represents the diff for a single file within a commit.
 */
public record GitDiffEntryDTO(
        String changeType,   // ADD, MODIFY, DELETE, RENAME, COPY
        String oldPath,
        String newPath,
        String patch         // unified diff text, may be truncated for large files
) {}
