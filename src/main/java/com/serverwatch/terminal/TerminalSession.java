package com.serverwatch.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import com.serverwatch.config.ServerWatchProperties;
import com.serverwatch.model.dto.CreateSessionRequest;
import com.serverwatch.model.dto.TerminalDimensions;
import com.serverwatch.model.dto.TerminalOutputMessage;
import com.serverwatch.model.dto.TerminalSessionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a single PTY shell process with its bidirectional I/O streams.
 *
 * <p>A virtual thread ({@link Thread#ofVirtual()}) continuously reads PTY output
 * and forwards it to the owning WebSocket session via STOMP.
 *
 * <p>Thread-safety: {@code write()} and {@code resize()} may be called from any
 * thread. {@code close()} is idempotent — the first call destroys the PTY process;
 * subsequent calls are no-ops.
 */
@Slf4j
public class TerminalSession implements Closeable {

    private final String sessionId;
    /** WebSocket session that created this terminal (routes output back to it). */
    private final String wsSessionId;
    /** STOMP principal name (username) of the owning user. */
    private final String username;

    private final PtyProcess ptyProcess;
    private final BufferedWriter writer;
    private final SimpMessagingTemplate messagingTemplate;
    private final CircularBuffer outputBuffer;

    private final String shell;
    private final String cwd;
    private final Instant createdAt;
    private volatile Instant lastActivityAt;
    private volatile int currentCols;
    private volatile int currentRows;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Spawns a PTY shell and starts the output-reader virtual thread.
     *
     * @throws IOException if the PTY process cannot be started
     */
    public TerminalSession(String sessionId,
                           String username,
                           String wsSessionId,
                           CreateSessionRequest request,
                           SimpMessagingTemplate messagingTemplate,
                           ServerWatchProperties properties) throws IOException {

        this.sessionId         = sessionId;
        this.username          = username;
        this.wsSessionId       = wsSessionId;
        this.messagingTemplate = messagingTemplate;
        this.createdAt         = Instant.now();
        this.lastActivityAt    = createdAt;

        ServerWatchProperties.Terminal cfg = properties.getTerminal();

        this.shell = Optional.ofNullable(request.shell()).filter(s -> !s.isBlank())
                .orElse(cfg.getShell());
        this.cwd = Optional.ofNullable(request.cwd()).filter(s -> !s.isBlank())
                .orElse(cfg.getDefaultCwd());
        this.currentCols = request.cols();
        this.currentRows = request.rows();
        this.outputBuffer = new CircularBuffer(cfg.getMaxBufferBytes());

        // Build environment: system defaults → configured extras → per-request extras
        Map<String, String> env = new HashMap<>(System.getenv());
        env.putAll(cfg.getEnv());
        if (request.env() != null) env.putAll(request.env());

        // Spawn PTY
        this.ptyProcess = new PtyProcessBuilder()
                .setCommand(new String[]{shell, "-i"})
                .setDirectory(cwd)
                .setEnvironment(env)
                .setInitialColumns(currentCols)
                .setInitialRows(currentRows)
                .start();

        this.writer = new BufferedWriter(
                new OutputStreamWriter(ptyProcess.getOutputStream(), StandardCharsets.UTF_8));

        // Pump PTY output → WebSocket on a virtual thread (Java 21)
        Thread.ofVirtual()
                .name("pty-reader-" + sessionId)
                .start(this::readOutputLoop);

        log.info("Terminal session started: id={} shell={} cwd={} pid={} dims={}x{}",
                sessionId, shell, cwd, ptyProcess.pid(), currentCols, currentRows);
    }

    // ── PTY output reader ──────────────────────────────────────────────────────

    private void readOutputLoop() {
        try (InputStreamReader reader =
                     new InputStreamReader(ptyProcess.getInputStream(), StandardCharsets.UTF_8)) {

            char[] buf = new char[4096];
            int n;
            while (!closed.get() && (n = reader.read(buf)) > 0) {
                String chunk = new String(buf, 0, n);
                outputBuffer.append(chunk);
                lastActivityAt = Instant.now();
                sendToOwner(new TerminalOutputMessage(
                        sessionId, "OUTPUT", chunk, null, null, Instant.now()));
            }

        } catch (IOException e) {
            if (!closed.get()) {
                log.warn("PTY read error for session {}: {}", sessionId, e.getMessage());
            }
        }

        // Shell exited (or connection dropped) — notify client and clean up
        int exitCode = 0;
        try {
            exitCode = ptyProcess.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Shell exited for session {} with code {}", sessionId, exitCode);
        sendToOwner(new TerminalOutputMessage(
                sessionId, "CLOSED", null, exitCode, "Shell exited", Instant.now()));
        close();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Sends raw input (keystrokes, paste) to the PTY stdin.
     *
     * @throws IllegalStateException if the session has already been closed
     * @throws IOException           on write failure
     */
    public void write(String input) throws IOException {
        if (closed.get()) throw new IllegalStateException("Terminal session is closed");
        writer.write(input);
        writer.flush();
        lastActivityAt = Instant.now();
    }

    /**
     * Resizes the PTY window. Called when the browser terminal is resized.
     *
     * @param cols new column count
     * @param rows new row count
     */
    public void resize(int cols, int rows) {
        if (closed.get() || cols <= 0 || rows <= 0) return;
        this.currentCols = cols;
        this.currentRows = rows;
        ptyProcess.setWinSize(new WinSize(cols, rows));
        log.debug("Resized session {} to {}x{}", sessionId, cols, rows);
    }

    /** Returns the buffered output captured since session start (for reconnect replay). */
    public String getBufferedOutput() {
        return outputBuffer.getAll();
    }

    /** Returns {@code true} if no activity has been seen within {@code timeoutMinutes}. */
    public boolean isIdle(int timeoutMinutes) {
        return lastActivityAt.isBefore(Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES));
    }

    /** Converts session metadata to a public-facing DTO. */
    public TerminalSessionDTO toDTO() {
        return new TerminalSessionDTO(
                sessionId, shell, cwd, createdAt, lastActivityAt,
                ptyProcess.pid(),
                new TerminalDimensions(currentCols, currentRows));
    }

    /**
     * Closes the PTY process. Idempotent — safe to call multiple times from any thread.
     * Forces destruction after 5 seconds if the process does not terminate on its own.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return; // already closed

        try { writer.close(); } catch (IOException ignored) { }

        if (ptyProcess.isAlive()) {
            ptyProcess.destroy();

            // Force-kill on a daemon virtual thread if the process lingers
            Thread.ofVirtual().name("pty-killer-" + sessionId).start(() -> {
                try { Thread.sleep(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (ptyProcess.isAlive()) {
                    log.warn("Force-killing unresponsive PTY for session {}", sessionId);
                    ptyProcess.destroyForcibly();
                }
            });
        }
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getSessionId()       { return sessionId; }
    public String getWsSessionId()     { return wsSessionId; }
    public String getUsername()        { return username; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public boolean isClosed()          { return closed.get(); }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Sends a STOMP message to the specific WebSocket session that owns this terminal.
     * Using both the username (for user-destination resolution) and the session ID
     * (in the headers) ensures only the correct browser tab receives the message,
     * even if the same user has multiple connections open.
     */
    private void sendToOwner(TerminalOutputMessage msg) {
        try {
            SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            accessor.setSessionId(wsSessionId);
            accessor.setLeaveMutable(true);
            messagingTemplate.convertAndSendToUser(
                    username, "/queue/terminal", msg, accessor.getMessageHeaders());
        } catch (Exception e) {
            log.debug("Failed to send terminal output to session {}: {}", wsSessionId, e.getMessage());
        }
    }
}
