package com.serverwatch.model.dto;

import java.time.Instant;

/**
 * Metadata for a single filesystem entry (file, directory, or symlink).
 * POSIX-specific fields (permissions, owner, group) are empty strings on non-POSIX systems.
 */
public record FileEntryDTO(
        /** Bare filename or directory name. */
        String name,
        /** Absolute path on the server. */
        String path,
        /** Path relative to the closest allowed root. */
        String relativePath,
        /** {@code FILE}, {@code DIRECTORY}, or {@code SYMLINK}. */
        String type,
        /** Size in bytes; 0 for directories. */
        long size,
        /** POSIX permission string, e.g. {@code rwxr-xr-x}. Empty on Windows. */
        String permissions,
        /** Octal permission string, e.g. {@code 755}. Empty on Windows. */
        String permissionsNumeric,
        /** Owning user name. Empty on Windows. */
        String owner,
        /** Owning group name. Empty on Windows. */
        String group,
        Instant modifiedAt,
        Instant createdAt,
        /** {@code true} if the name begins with a dot. */
        boolean hidden,
        boolean readable,
        boolean writable,
        boolean executable,
        /** MIME type for files; {@code null} for directories. */
        String mimeType,
        /** {@code true} for text files whose size is within the editable limit. */
        boolean editable,
        /** Symlink target path; {@code null} for non-symlinks. */
        String symlinkTarget
) {}
