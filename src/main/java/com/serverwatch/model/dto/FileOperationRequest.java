package com.serverwatch.model.dto;

/**
 * Multi-purpose request body for file mutation operations.
 * Not every field is required for every operation — see the individual service methods.
 *
 * <ul>
 *   <li><b>write</b>: {@code path}, {@code content}, {@code encoding} (default UTF-8)</li>
 *   <li><b>create</b>: {@code path} (parent dir), {@code name}, {@code type} (FILE|DIRECTORY), {@code content} (optional)</li>
 *   <li><b>move/copy</b>: {@code sourcePath}, {@code targetPath}</li>
 *   <li><b>chmod</b>: {@code path}, {@code permissions} (octal, e.g. "755")</li>
 * </ul>
 */
public record FileOperationRequest(
        String path,
        String name,
        /** {@code FILE} or {@code DIRECTORY} — used by create. */
        String type,
        String content,
        String encoding,
        /** Octal permission string, e.g. {@code 755} — used by chmod. */
        String permissions,
        String sourcePath,
        String targetPath
) {}
