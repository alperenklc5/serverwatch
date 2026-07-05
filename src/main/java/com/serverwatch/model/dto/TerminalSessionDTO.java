package com.serverwatch.model.dto;

import java.time.Instant;

/** Public snapshot of an active terminal session. */
public record TerminalSessionDTO(
        /** Unique session identifier (UUID). */
        String sessionId,
        /** Shell executable that was spawned, e.g. {@code /bin/bash}. */
        String shell,
        /** Working directory the shell was started in. */
        String cwd,
        Instant createdAt,
        Instant lastActivityAt,
        /** OS process ID of the PTY process. */
        long pid,
        /** Current terminal window dimensions. */
        TerminalDimensions dimensions
) {}
