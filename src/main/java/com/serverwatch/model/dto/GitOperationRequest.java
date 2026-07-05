package com.serverwatch.model.dto;

/**
 * Flexible request body for Git operations.
 *
 * <p>Fields used depend on the operation:
 * <ul>
 *   <li><b>clone</b>: {@code remoteUrl}, {@code name}, {@code branch} (optional)</li>
 *   <li><b>add existing</b>: {@code localPath}, {@code name}</li>
 *   <li><b>pull/push/fetch</b>: {@code repoId}, {@code remoteName} (optional), {@code branch} (optional)</li>
 *   <li><b>checkout</b>: {@code repoId}, {@code branch}, {@code createNew} (optional)</li>
 *   <li><b>create branch</b>: {@code repoId}, {@code name}, {@code startPoint} (optional)</li>
 * </ul>
 */
public record GitOperationRequest(
        String repoId,
        String remoteUrl,
        String name,
        String localPath,
        String branch,
        String startPoint,
        String remoteName,
        Boolean createNew
) {
    /** Returns {@code createNew} defaulting to {@code false} when null. */
    public boolean isCreateNew() {
        return Boolean.TRUE.equals(createNew);
    }

    /** Returns {@code remoteName} defaulting to {@code "origin"} when null. */
    public String remoteNameOrDefault() {
        return remoteName != null ? remoteName : "origin";
    }
}
