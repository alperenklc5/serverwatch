package com.serverwatch.websocket;

import com.serverwatch.model.dto.CreateSessionRequest;
import com.serverwatch.model.dto.TerminalInputMessage;
import com.serverwatch.model.dto.TerminalOutputMessage;
import com.serverwatch.model.dto.TerminalSessionDTO;
import com.serverwatch.service.TerminalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;

/**
 * STOMP controller that handles all terminal lifecycle messages sent by the browser.
 *
 * <p>Message flow:
 * <ul>
 *   <li>Client → {@code /app/terminal/create} → spawns PTY, replies to
 *       {@code /user/queue/terminal-created}</li>
 *   <li>Client → {@code /app/terminal/input} → routes keystrokes or resize to the PTY</li>
 *   <li>Client → {@code /app/terminal/close} → tears down the PTY session</li>
 * </ul>
 *
 * <p>PTY output is pushed asynchronously from {@link com.serverwatch.terminal.TerminalSession}
 * directly to {@code /user/queue/terminal} without passing through this controller.
 */
@Slf4j
@Controller
public class TerminalWebSocketController {

    private final TerminalService terminalService;

    public TerminalWebSocketController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    // ── Session creation ───────────────────────────────────────────────────────

    /**
     * Creates a new PTY session and notifies the requesting client.
     *
     * @param request   shell, cwd, and initial dimensions (all optional)
     * @param accessor  STOMP headers — used to extract the WebSocket session ID
     * @param principal authenticated STOMP user (set by {@link com.serverwatch.security.WebSocketAuthInterceptor})
     */
    @MessageMapping("/terminal/create")
    @SendToUser(value = "/queue/terminal-created", broadcast = false)
    public TerminalOutputMessage createSession(@Payload CreateSessionRequest request,
                                               SimpMessageHeaderAccessor accessor,
                                               Principal principal) {
        String username    = principal.getName();
        String wsSessionId = accessor.getSessionId();

        try {
            TerminalSessionDTO dto = terminalService.createSession(username, wsSessionId, request);
            log.info("STOMP: terminal session created id={} for user={}", dto.sessionId(), username);

            // The OUTPUT message carries the session ID as a handshake token so the client
            // knows which sessionId to use for subsequent INPUT/RESIZE messages.
            return new TerminalOutputMessage(
                    dto.sessionId(), "CREATED", null, null,
                    "Session created", Instant.now());

        } catch (IllegalStateException e) {
            log.warn("Terminal create rejected for user={}: {}", username, e.getMessage());
            return errorMessage(null, e.getMessage());

        } catch (SecurityException e) {
            log.warn("Terminal create forbidden for user={}: {}", username, e.getMessage());
            return errorMessage(null, e.getMessage());

        } catch (IOException e) {
            log.error("PTY spawn failed for user={}: {}", username, e.getMessage(), e);
            return errorMessage(null, "Failed to start terminal: " + e.getMessage());
        }
    }

    // ── Input / resize / ping ──────────────────────────────────────────────────

    /**
     * Routes a terminal message to the correct PTY session.
     *
     * <p>Supported types:
     * <ul>
     *   <li>{@code INPUT}  — forwards raw data to PTY stdin</li>
     *   <li>{@code RESIZE} — updates PTY window dimensions</li>
     *   <li>{@code PING}   — no-op keep-alive (acknowledges silently)</li>
     * </ul>
     */
    @MessageMapping("/terminal/input")
    public void handleInput(@Payload TerminalInputMessage message, Principal principal) {
        if (message == null || message.sessionId() == null) return;

        String sessionId = message.sessionId();

        switch (message.type() == null ? "" : message.type()) {
            case "INPUT" -> {
                if (message.data() != null) {
                    try {
                        terminalService.writeInput(sessionId, message.data());
                    } catch (IllegalArgumentException e) {
                        log.debug("INPUT to unknown session {}: {}", sessionId, e.getMessage());
                    }
                }
            }
            case "RESIZE" -> {
                if (message.dimensions() != null) {
                    terminalService.resize(sessionId,
                            message.dimensions().cols(),
                            message.dimensions().rows());
                }
            }
            case "PING" -> log.trace("PING for session {}", sessionId);
            default     -> log.debug("Unknown terminal message type '{}' for session {}",
                    message.type(), sessionId);
        }
    }

    // ── Session close ──────────────────────────────────────────────────────────

    /**
     * Closes a terminal session at the client's explicit request.
     *
     * @param message must contain at least a non-null {@code sessionId}
     */
    @MessageMapping("/terminal/close")
    public void closeSession(@Payload TerminalInputMessage message, Principal principal) {
        if (message == null || message.sessionId() == null) return;

        log.info("STOMP: close request for session {} from user={}",
                message.sessionId(), principal.getName());
        try {
            terminalService.closeSession(message.sessionId());
        } catch (IllegalArgumentException e) {
            // Session already gone — ignore
            log.debug("Close ignored: {}", e.getMessage());
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private TerminalOutputMessage errorMessage(String sessionId, String text) {
        return new TerminalOutputMessage(sessionId, "ERROR", null, null, text, Instant.now());
    }
}
