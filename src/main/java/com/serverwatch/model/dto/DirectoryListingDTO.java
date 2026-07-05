package com.serverwatch.model.dto;

import java.util.List;

/** The result of browsing a directory: metadata + sorted entry list. */
public record DirectoryListingDTO(
        /** Absolute path of the directory being listed. */
        String path,
        /** Parent directory path; {@code null} when at an allowed-root boundary. */
        String parentPath,
        /** Navigation trail from the allowed root to this directory. */
        List<PathBreadcrumb> breadcrumbs,
        /** Sorted entries (directories first, then files, both case-insensitive). */
        List<FileEntryDTO> entries,
        int totalCount,
        int directoryCount,
        int fileCount,
        /** Sum of sizes of all immediate file children (not recursive). */
        long totalSize,
        /** {@code true} when this directory is under a configured read-only root. */
        boolean readOnly
) {}
