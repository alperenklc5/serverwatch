package com.serverwatch.model.dto;

/** Result of a successful file upload. */
public record UploadResponseDTO(
        /** Absolute server path where the file was saved. */
        String path,
        /** Final filename (may differ from original if a conflict was resolved). */
        String filename,
        long size,
        String mimeType
) {}
