package com.serverwatch.service;

import com.serverwatch.config.ServerWatchProperties;
import com.serverwatch.model.dto.CreateSessionRequest;
import com.serverwatch.model.dto.TerminalSessionDTO;
import com.serverwatch.terminal.TerminalSession;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of all active {@link TerminalSession}s.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create and register sessions (enforces max-sessions and shell allow-list)</li>
 *   <li>Route input and resize events to the correct session</li>
 *   <li>Close sessions on client request, WebSocket disconnect, or idle timeout</li>
 *   <li>Shut down all PTY processes cleanly when the application stops</li>
 * </ul>
 */
@Slf4j
@Service
public class TerminalService {

    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final ServerWatchProperties properties;

    public TerminalService(SimpMessagingTemplate messagingTemplate,
                           ServerWatchProperties properties) {
        this.messagingTemplate = messagingTemplate;
        this.properties        = properties;
    }

    // ── Session creation ───────────────────────────────────────────────────────

    /**
     * Spawns a new PTY shell and registers the session.
     *
     * @param username    principal name of the authenticated user (for STOMP routing)
     * @param wsSessionId WebSocket session ID of the owning connection
     * @param request     shell preferences and initial dimensions
     * @return the newly created session descriptor
     * @throws IllegalStateException if the concurrent session limit is reached
     * @throws SecurityException     if the requested shell is not in the allow-list
     * @throws IOException           if the PTY process fails to start
     */
    public TerminalSessionDTO createSession(String username,
                                            String wsSessionId,
                                            CreateSessionRequest request) throws IOException {

        ServerWatchProperties.Terminal cfg = properties.getTerminal();

        if (sessions.size() >= cfg.getMaxSessions()) {
            throw new IllegalStateException(
                    "Maximum concurrent terminal sessions (%d) reached".formatted(cfg.getMaxSessions()));
        }

        // Validate shell against the allow-list
        String shell = (request.shell() != null && !request.shell().isBlank())
                ? request.shell()
                : cfg.getShell();

        if (!cfg.getAvailableShells().contains(shell)) {
            throw new SecurityException("Shell not in allow-list: " + shell);
        }

        String sessionId = UUID.randomUUID().toString();
        TerminalSession session = new TerminalSession(
                sessionId, username, wsSessionId, request, messagingTemplate, properties);
        sessions.put(sessionId, session);

        log.info("Terminal session created: id={} user={} shell={}", sessionId, username, shell);
        return session.toDTO();
    }

    // ── Input routing ──────────────────────────────────────────────────────────

    /**
     * Forwards raw keystrokes to the PTY stdin.
     *
     * @throws IllegalArgumentException if {@code sessionId} is not recognised
     */
    public void writeInput(String sessionId, String input) {
        TerminalSession session = requireSession(sessionId);
        try {
            session.write(input);
        } catch (IOException e) {
            log.error("Write failed for session {} — closing: {}", sessionId, e.getMessage());
            closeSession(sessionId);
        }
    }

    /**
     * Resizes the PTY window to the given dimensions.
     * Silently ignored if the session does not exist or is already closed.
     */
    public void resize(String sessionId, int cols, int rows) {
        TerminalSession session = sessions.get(sessionId);
        if (session != null && !session.isClosed()) {
            session.resize(cols, rows);
        }
    }

    // ── Output replay ──────────────────────────────────────────────────────────

    /**
     * Returns the circular output buffer for a session.
     * Used by reconnecting clients to redraw the terminal from the last known state.
     *
     * @return buffered output string, or an empty string if the session is not found
     */
    public String getBuffer(String sessionId) {
        TerminalSession session = sessions.get(sessionId);
        return session != null ? session.getBufferedOutput() : "";
    }

    // ── Session termination ────────────────────────────────────────────────────

    /**
     * Closes a single session by its terminal session ID.
     *
     * @throws IllegalArgumentException if {@code sessionId} is not found
     */
    public void closeSession(String sessionId) {
        TerminalSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
            log.info("Terminal session closed: {}", sessionId);
        }
    }

    /**
     * Closes all terminal sessions associated with a WebSocket connection.
     * Called automatically when a WebSocket client disconnects.
     *
     * @param wsSessionId the disconnected WebSocket session ID
     */
    public void closeSessionsForWsClient(String wsSessionId) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, TerminalSession> entry : sessions.entrySet()) {
            if (wsSessionId.equals(entry.getValue().getWsSessionId())) {
                toRemove.add(entry.getKey());
            }
        }
        for (String id : toRemove) {
            TerminalSession session = sessions.remove(id);
            if (session != null) {
                session.close();
                log.info("Terminal session {} closed on WebSocket disconnect (wsSession={})",
                        id, wsSessionId);
            }
        }
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    /** Returns a snapshot of all currently active sessions. */
    public List<TerminalSessionDTO> listSessions() {
        return sessions.values().stream()
                .filter(s -> !s.isClosed())
                .map(TerminalSession::toDTO)
                .toList();
    }

    /**
     * Returns the descriptor for a specific session.
     *
     * @throws IllegalArgumentException if not found
     */
    public TerminalSessionDTO getSession(String sessionId) {
        return requireSession(sessionId).toDTO();
    }

    /** Returns the shells configured in the allow-list. */
    public List<String> getAvailableShells() {
        return Collections.unmodifiableList(properties.getTerminal().getAvailableShells());
    }

    // ── Scheduled cleanup ──────────────────────────────────────────────────────

    /**
     * Checks for idle sessions every 60 seconds and closes any that have not
     * had activity within the configured {@code session-timeout-minutes}.
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanupIdleSessions() {
        int timeoutMinutes = properties.getTerminal().getSessionTimeoutMinutes();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, TerminalSession> entry : sessions.entrySet()) {
            TerminalSession s = entry.getValue();
            if (s.isClosed() || s.isIdle(timeoutMinutes)) {
                toRemove.add(entry.getKey());
            }
        }

        for (String id : toRemove) {
            TerminalSession session = sessions.remove(id);
            if (session != null) {
                log.info("Closing idle terminal session: {}", id);
                session.close();
            }
        }

        if (!toRemove.isEmpty()) {
            log.info("Idle cleanup: closed {} terminal session(s), {} remain active",
                    toRemove.size(), sessions.size());
        }
    }

    // ── Shutdown ───────────────────────────────────────────────────────────────

    /** Closes all PTY processes when the Spring application context shuts down. */
    @PreDestroy
    public void closeAll() {
        log.info("Shutting down {} terminal session(s)", sessions.size());
        sessions.values().forEach(TerminalSession::close);
        sessions.clear();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private TerminalSession requireSession(String sessionId) {
        TerminalSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Terminal session not found: " + sessionId);
        }
        return session;
    }
}
