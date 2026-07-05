package com.serverwatch.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Summary information about a registered Git repository.
 */
public record GitRepoDTO(
        String repoId,
        String name,
        String localPath,
        String remoteUrl,
        String currentBranch,
        boolean isClean,
        String lastCommitHash,
        String lastCommitMessage,
        Instant lastCommitDate,
        List<String> branches,
        List<String> remoteBranches
) {}
