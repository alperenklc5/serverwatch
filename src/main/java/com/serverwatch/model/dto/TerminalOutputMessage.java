package com.serverwatch.model.dto;

import java.time.Instant;

/**
 * Message pushed FROM the server TO the browser over STOMP at {@code /user/queue/terminal}.
 *
 * <ul>
 *   <li>{@code OUTPUT} — {@code data} contains raw PTY output (UTF-8, may include ANSI)</li>
 *   <li>{@code CLOSED} — shell exited; {@code exitCode} contains the process exit code</li>
 *   <li>{@code ERROR}  — {@code message} describes the problem</li>
 * </ul>
 */
public record TerminalOutputMessage(
        String sessionId,
        /** {@code OUTPUT}, {@code CLOSED}, or {@code ERROR}. */
        String type,
        /** Raw PTY output for OUTPUT messages; null otherwise. */
        String data,
        /** Shell exit code for CLOSED messages; null otherwise. */
        Integer exitCode,
        /** Human-readable description for CLOSED / ERROR messages. */
        String message,
        Instant timestamp
) {}
