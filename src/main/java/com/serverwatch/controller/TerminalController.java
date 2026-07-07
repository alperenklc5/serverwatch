package com.serverwatch.controller;

import com.serverwatch.model.dto.ApiResponse;
import com.serverwatch.model.dto.TerminalSessionDTO;
import com.serverwatch.service.TerminalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for terminal session management.
 *
 * <p>These endpoints complement the STOMP terminal controller — they provide
 * observability and administrative control over active sessions without requiring
 * a live WebSocket connection.
 *
 * <p>All write operations ({@code DELETE}) are restricted to {@code ROLE_ADMIN}.
 * Read operations (list, get, buffer) are available to any authenticated user.
 */
@Slf4j
@RestController
@RequestMapping("/api/terminal")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    // ── Session listing ────────────────────────────────────────────────────────

    /**
     * Returns all currently active terminal sessions.
     *
     * @return list of session descriptors; empty list if none are active
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<TerminalSessionDTO>>> listSessions() {
        List<TerminalSessionDTO> sessions = terminalService.listSessions();
        return ResponseEntity.ok(ApiResponse.ok(sessions));
    }

    /**
     * Returns the descriptor for a specific session.
     *
     * @param id the terminal session UUID
     * @throws IllegalArgumentException (→ 400) if the session does not exist
     */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<TerminalSessionDTO>> getSession(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(terminalService.getSession(id)));
    }

    /**
     * Returns the circular output buffer for a session.
     * Used by reconnecting clients to redraw the terminal from the last known state.
     *
     * @param id the terminal session UUID
     * @return the buffered output; empty string if the session has no buffered output
     */
    @GetMapping("/sessions/{id}/buffer")
    public ResponseEntity<ApiResponse<String>> getBuffer(@PathVariable String id) {
        String buffer = terminalService.getBuffer(id);
        return ResponseEntity.ok(ApiResponse.ok(buffer));
    }

    // ── Session termination ────────────────────────────────────────────────────

    /**
     * Forcibly closes a terminal session.
     * Restricted to {@code ROLE_ADMIN} to prevent users from killing each other's sessions.
     *
     * @param id the terminal session UUID
     */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<Void>> closeSession(@PathVariable String id) {
        log.info("REST: force-closing terminal session {}", id);
        terminalService.closeSession(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Shell discovery ────────────────────────────────────────────────────────

    /**
     * Returns the list of shells available for new sessions.
     * The list is derived from the {@code serverwatch.terminal.available-shells} config property.
     *
     * @return the configured allow-list of shell paths
     */
    @GetMapping("/shells")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableShells() {
        return ResponseEntity.ok(ApiResponse.ok(terminalService.getAvailableShells()));
    }
}
