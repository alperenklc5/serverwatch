package com.serverwatch.model.dto;

/**
 * Message sent FROM the browser TO the server over STOMP at {@code /app/terminal/input}.
 *
 * <ul>
 *   <li>{@code INPUT} — {@code data} contains raw keystrokes / paste content</li>
 *   <li>{@code RESIZE} — {@code dimensions} contains the new terminal size; {@code data} is null</li>
 *   <li>{@code PING} — keep-alive heartbeat; no action taken</li>
 * </ul>
 */
public record TerminalInputMessage(
        String sessionId,
        /** {@code INPUT}, {@code RESIZE}, or {@code PING}. */
        String type,
        /** Raw keystrokes for INPUT messages; null otherwise. */
        String data,
        /** New window dimensions for RESIZE messages; null otherwise. */
        TerminalDimensions dimensions
) {}
