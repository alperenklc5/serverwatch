package com.serverwatch.model.dto;

/** Content of a text file as returned by the read endpoint. */
public record FileContentDTO(
        String path,
        /** UTF-8 or ISO-8859-1 decoded text content; {@code null} when {@code binary=true}. */
        String content,
        /** Detected charset, e.g. {@code UTF-8}. */
        String encoding,
        /** {@code LF} or {@code CRLF}. */
        String lineEnding,
        long size,
        int lineCount,
        /** {@code true} when the file contains non-text bytes; {@code content} will be null. */
        boolean binary
) {}
