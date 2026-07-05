package com.serverwatch.model.dto;

/** One segment in a filesystem breadcrumb trail. */
public record PathBreadcrumb(
        /** Display name (directory / drive label). */
        String name,
        /** Absolute path up to and including this segment. */
        String path
) {}
