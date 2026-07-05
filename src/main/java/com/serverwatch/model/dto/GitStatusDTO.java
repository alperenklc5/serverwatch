package com.serverwatch.model.dto;

import java.util.List;

/**
 * Working-tree and index status for a repository.
 */
public record GitStatusDTO(
        String branch,
        boolean isClean,
        List<String> added,         // staged new files
        List<String> changed,       // staged modified files
        List<String> removed,       // staged deleted files
        List<String> untracked,     // untracked files
        List<String> modified,      // unstaged modified files
        List<String> missing,       // deleted but not staged
        List<String> conflicting    // merge conflicts
) {}
