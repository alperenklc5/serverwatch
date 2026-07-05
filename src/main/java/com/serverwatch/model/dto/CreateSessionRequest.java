package com.serverwatch.model.dto;

import java.util.Map;

/**
 * Request payload for creating a new terminal session via
 * {@code /app/terminal/create} STOMP message.
 * All fields are optional — omitted values fall back to the server configuration defaults.
 */
public record CreateSessionRequest(
        /** Shell override, e.g. {@code /bin/zsh}. Must be in the configured allow-list. */
        String shell,
        /** Working directory override. Defaults to {@code serverwatch.terminal.default-cwd}. */
        String cwd,
        /** Initial terminal width in columns. Defaults to 80. */
        int cols,
        /** Initial terminal height in rows. Defaults to 24. */
        int rows,
        /** Additional environment variables merged on top of the server defaults. */
        Map<String, String> env
) {
    /** Compact constructor that substitutes sensible defaults for zero-value dimensions. */
    public CreateSessionRequest {
        if (cols <= 0) cols = 80;
        if (rows <= 0) rows = 24;
    }
}
