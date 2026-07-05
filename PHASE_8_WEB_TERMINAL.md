# Phase 8 — Web Terminal (xterm.js + PTY)

## Objective
Implement a fully interactive web-based terminal that gives users shell access to the VPS through their browser. Uses PTY (pseudo-terminal) for real shell behavior — arrow keys, tab completion, colors, interactive commands (vim, htop, nano) all work. This is the "SSH replacement" feature.

## Prerequisites
- Phase 7 completed — File manager operational
- pty4j library added to pom.xml (see Step 1)
- Application running on Linux (VPS) or Windows (dev) — pty4j supports both

## Context — PTY vs Process
A plain `Runtime.exec()` or `ProcessBuilder` gives you a Process but not a proper terminal. Commands like `vim`, `htop`, `top`, arrow keys, tab completion, colors — none of these work without a PTY. `pty4j` (from JetBrains) provides a real pseudo-terminal that acts exactly like an SSH session.

## Step 1: Add Dependency

Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.jetbrains.pty4j</groupId>
    <artifactId>pty4j</artifactId>
    <version>0.13.10</version>
</dependency>
```

Note: pty4j pulls in native libraries automatically for Linux, macOS, and Windows. No additional setup needed.

## Step 2: Configuration

Add to `application.yml`:
```yaml
serverwatch:
  terminal:
    # Default shell command
    shell: /bin/bash        # Linux
    # shell: powershell.exe  # Windows dev
    # Additional shells users can pick from
    available-shells:
      - /bin/bash
      - /bin/sh
      - /bin/zsh
    # Working directory for new sessions
    default-cwd: ${user.home}
    # Environment variables to inject
    env:
      TERM: xterm-256color
      LANG: en_US.UTF-8
    # Session timeout in minutes (idle)
    session-timeout-minutes: 30
    # Maximum concurrent sessions
    max-sessions: 10
    # Maximum output buffer per session (bytes)
    max-buffer-bytes: 1048576   # 1MB
```

## Step 3: DTOs

### TerminalSessionDTO.java (record)
```java
// - sessionId (String) — UUID
// - shell (String) — the shell that was spawned
// - cwd (String) — current working directory
// - createdAt (Instant)
// - lastActivityAt (Instant)
// - pid (long) — the PTY process PID
// - dimensions (TerminalDimensions) — cols and rows
```

### TerminalDimensions.java (record)
```java
// - cols (int)
// - rows (int)
```

### TerminalInputMessage.java (record)
```java
// - sessionId (String)
// - type (String) — "INPUT", "RESIZE", "PING"
// - data (String) — for INPUT: user keystrokes; for RESIZE: null (dimensions in field)
// - dimensions (TerminalDimensions) — for RESIZE
```

### TerminalOutputMessage.java (record)
```java
// - sessionId (String)
// - type (String) — "OUTPUT", "CLOSED", "ERROR"
// - data (String) — for OUTPUT: raw terminal bytes as UTF-8
// - exitCode (Integer) — for CLOSED
// - message (String) — for ERROR
// - timestamp (Instant)
```

### CreateSessionRequest.java (record)
```java
// - shell (String, optional) — override default
// - cwd (String, optional) — override default
// - cols (int, default 80)
// - rows (int, default 24)
// - env (Map<String, String>, optional) — additional env vars
```

## Step 4: TerminalSession

### TerminalSession.java
Wraps a single PTY process with its I/O streams and metadata.

```java
public class TerminalSession implements Closeable {

    private final String sessionId;
    private final String wsSessionId;   // the WebSocket session that owns this terminal
    private final PtyProcess ptyProcess;
    private final Thread outputReaderThread;
    private final BufferedWriter writer;
    private final SimpMessagingTemplate messagingTemplate;
    private final String shell;
    private final String cwd;
    private final Instant createdAt;
    private volatile Instant lastActivityAt;
    private volatile boolean closed = false;
    private final CircularBuffer outputBuffer;  // stores recent output for reconnect

    public TerminalSession(String sessionId, String wsSessionId,
                            CreateSessionRequest request,
                            SimpMessagingTemplate template,
                            ServerWatchProperties properties) throws IOException {
        this.sessionId = sessionId;
        this.wsSessionId = wsSessionId;
        this.shell = Optional.ofNullable(request.shell()).orElse(properties.getTerminal().getShell());
        this.cwd = Optional.ofNullable(request.cwd()).orElse(properties.getTerminal().getDefaultCwd());

        // Build environment
        Map<String, String> env = new HashMap<>(System.getenv());
        env.putAll(properties.getTerminal().getEnv());
        if (request.env() != null) env.putAll(request.env());

        // Spawn PTY
        PtyProcessBuilder builder = new PtyProcessBuilder()
            .setCommand(new String[]{shell, "-i"})  // -i = interactive shell
            .setDirectory(cwd)
            .setEnvironment(env)
            .setInitialColumns(request.cols())
            .setInitialRows(request.rows());

        this.ptyProcess = builder.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(
            ptyProcess.getOutputStream(), StandardCharsets.UTF_8
        ));
        this.messagingTemplate = template;
        this.createdAt = Instant.now();
        this.lastActivityAt = createdAt;
        this.outputBuffer = new CircularBuffer(properties.getTerminal().getMaxBufferBytes());

        // Start reader thread to pump PTY output to WebSocket
        this.outputReaderThread = Thread.ofVirtual()
            .name("terminal-reader-" + sessionId)
            .start(this::readOutputLoop);
    }

    private void readOutputLoop() {
        try (InputStreamReader reader = new InputStreamReader(
                ptyProcess.getInputStream(), StandardCharsets.UTF_8)) {

            char[] buf = new char[4096];
            int n;
            while (!closed && (n = reader.read(buf)) > 0) {
                String output = new String(buf, 0, n);
                outputBuffer.append(output);
                lastActivityAt = Instant.now();

                // Send to the WebSocket session that owns this terminal
                TerminalOutputMessage msg = new TerminalOutputMessage(
                    sessionId, "OUTPUT", output, null, null, Instant.now()
                );
                messagingTemplate.convertAndSendToUser(
                    wsSessionId, "/queue/terminal", msg,
                    createHeaders(wsSessionId)
                );
            }

            // Process ended
            int exitCode = ptyProcess.waitFor();
            TerminalOutputMessage closeMsg = new TerminalOutputMessage(
                sessionId, "CLOSED", null, exitCode, "Shell exited", Instant.now()
            );
            messagingTemplate.convertAndSendToUser(
                wsSessionId, "/queue/terminal", closeMsg,
                createHeaders(wsSessionId)
            );
        } catch (Exception e) {
            log.error("Terminal reader error for session {}: {}", sessionId, e.getMessage());
        } finally {
            close();
        }
    }

    public void write(String input) throws IOException {
        if (closed) throw new IllegalStateException("Session closed");
        writer.write(input);
        writer.flush();
        lastActivityAt = Instant.now();
    }

    public void resize(int cols, int rows) {
        if (ptyProcess instanceof PtyProcess p) {
            p.setWinSize(new WinSize(cols, rows));
        }
    }

    public String getBufferedOutput() {
        return outputBuffer.getAll();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        try { writer.close(); } catch (Exception ignored) {}
        if (ptyProcess.isAlive()) {
            ptyProcess.destroy();
            // Force kill after 5 seconds if still alive
            new Thread(() -> {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                if (ptyProcess.isAlive()) ptyProcess.destroyForcibly();
            }).start();
        }
    }

    // Getters, isIdle(timeoutMinutes), toDTO(), etc.
}
```

### CircularBuffer.java (helper)
A ring buffer that keeps the last N bytes of output. When a client reconnects, they can request the buffer to redraw the terminal.

```java
public class CircularBuffer {
    private final StringBuilder buffer;
    private final int maxSize;

    public CircularBuffer(int maxSize) {
        this.maxSize = maxSize;
        this.buffer = new StringBuilder(maxSize);
    }

    public synchronized void append(String data) {
        buffer.append(data);
        if (buffer.length() > maxSize) {
            buffer.delete(0, buffer.length() - maxSize);
        }
    }

    public synchronized String getAll() {
        return buffer.toString();
    }
}
```

## Step 5: TerminalService

### TerminalService.java

```java
@Service
public class TerminalService {

    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final ServerWatchProperties properties;

    public TerminalSessionDTO createSession(String wsSessionId, CreateSessionRequest request) {
        if (sessions.size() >= properties.getTerminal().getMaxSessions()) {
            throw new IllegalStateException("Maximum session count reached");
        }

        // Validate shell is in the allowed list
        String shell = Optional.ofNullable(request.shell()).orElse(properties.getTerminal().getShell());
        if (!properties.getTerminal().getAvailableShells().contains(shell)) {
            throw new SecurityException("Shell not allowed: " + shell);
        }

        String sessionId = UUID.randomUUID().toString();
        TerminalSession session = new TerminalSession(sessionId, wsSessionId, request,
                                                       messagingTemplate, properties);
        sessions.put(sessionId, session);

        log.info("Terminal session created: {} (shell={}, cwd={})", sessionId, shell, request.cwd());
        return session.toDTO();
    }

    public void writeInput(String sessionId, String input) {
        TerminalSession session = sessions.get(sessionId);
        if (session == null) throw new NotFoundException("Session not found: " + sessionId);
        try {
            session.write(input);
        } catch (IOException e) {
            log.error("Failed to write to terminal {}: {}", sessionId, e.getMessage());
            closeSession(sessionId);
        }
    }

    public void resize(String sessionId, int cols, int rows) {
        TerminalSession session = sessions.get(sessionId);
        if (session == null) return;
        session.resize(cols, rows);
    }

    public String getBuffer(String sessionId) {
        TerminalSession session = sessions.get(sessionId);
        return session != null ? session.getBufferedOutput() : "";
    }

    public void closeSession(String sessionId) {
        TerminalSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
            log.info("Terminal session closed: {}", sessionId);
        }
    }

    public void closeSessionsForWsClient(String wsSessionId) {
        // Called when a WebSocket disconnects — close all its terminal sessions
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getWsSessionId().equals(wsSessionId)) {
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    public List<TerminalSessionDTO> listSessions() {
        return sessions.values().stream().map(TerminalSession::toDTO).toList();
    }

    // Idle timeout cleanup
    @Scheduled(fixedDelay = 60_000) // every minute
    public void cleanupIdleSessions() {
        int timeoutMinutes = properties.getTerminal().getSessionTimeoutMinutes();
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);

        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getLastActivityAt().isBefore(cutoff)) {
                log.info("Closing idle terminal session: {}", entry.getKey());
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    @PreDestroy
    public void closeAll() {
        sessions.values().forEach(TerminalSession::close);
        sessions.clear();
    }
}
```

## Step 6: WebSocket Controller

### TerminalWebSocketController.java

```java
@Controller
public class TerminalWebSocketController {

    private final TerminalService terminalService;

    @MessageMapping("/terminal/create")
    @SendToUser("/queue/terminal-created")
    public TerminalSessionDTO create(@Payload CreateSessionRequest request,
                                      SimpMessageHeaderAccessor accessor) {
        return terminalService.createSession(accessor.getSessionId(), request);
    }

    @MessageMapping("/terminal/input")
    public void input(@Payload TerminalInputMessage message) {
        switch (message.type()) {
            case "INPUT" -> terminalService.writeInput(message.sessionId(), message.data());
            case "RESIZE" -> terminalService.resize(
                message.sessionId(),
                message.dimensions().cols(),
                message.dimensions().rows()
            );
            case "PING" -> { /* keep-alive, no action needed */ }
        }
    }

    @MessageMapping("/terminal/close")
    public void close(@Payload Map<String, String> payload) {
        terminalService.closeSession(payload.get("sessionId"));
    }
}
```

## Step 7: REST Controller (session management)

### TerminalController.java

```
GET    /api/terminal/sessions              → List<TerminalSessionDTO>
GET    /api/terminal/sessions/{id}         → TerminalSessionDTO
GET    /api/terminal/sessions/{id}/buffer  → {content: "..."} (for reconnect)
DELETE /api/terminal/sessions/{id}         → 204
GET    /api/terminal/shells                → List<String> (available shells)
```

## Step 8: WebSocket Cleanup Integration

Modify `WebSocketEventListener.java` to close terminal sessions when a client disconnects:

```java
@EventListener
public void handleDisconnect(SessionDisconnectEvent event) {
    String sessionId = event.getSessionId();
    dockerService.stopLogStream(sessionId);
    terminalService.closeSessionsForWsClient(sessionId);
    connectedClients.decrementAndGet();
}
```

## Step 9: Frontend Protocol (documented for the React phase later)

The React frontend will use `xterm.js` and connect like this:

```
1. Connect WebSocket to /ws
2. Send STOMP frame to /app/terminal/create with:
   { shell: "/bin/bash", cwd: "/home/user", cols: 80, rows: 24 }
3. Receive TerminalSessionDTO on /user/queue/terminal-created
4. Subscribe to /user/queue/terminal to receive OUTPUT messages
5. On xterm.js onData(data): send to /app/terminal/input as INPUT
6. On xterm.js onResize({cols, rows}): send to /app/terminal/input as RESIZE
7. On close: send to /app/terminal/close
```

## Security Considerations

**⚠️ CRITICAL:** This phase gives full shell access. Once Phase 9 adds authentication, terminal access should be restricted to admin role. Never expose this without auth.

Additional hardening options for later:
- Session recording — save all input/output to disk for audit
- Command allowlist mode — restrict to specific commands
- Runtime restriction — spawn shell inside a chroot or Docker container
- Multi-factor confirmation for destructive commands (rm -rf, dd, etc.)

## Acceptance Criteria
- [ ] `POST` message to `/app/terminal/create` spawns a shell and returns session info
- [ ] Terminal output streams to `/user/queue/terminal` in real time
- [ ] Sending `INPUT` messages sends keystrokes to the shell
- [ ] Interactive commands work: `vim`, `htop`, `top`, `nano`, `less`
- [ ] Arrow keys navigate command history
- [ ] Tab completion works
- [ ] Colored output renders correctly (ls --color, git log --color)
- [ ] `RESIZE` messages update PTY dimensions
- [ ] Sessions auto-close after idle timeout
- [ ] WebSocket disconnect closes all associated sessions
- [ ] `GET /api/terminal/sessions/{id}/buffer` returns recent output for reconnect
- [ ] Cannot spawn shells not in the `available-shells` list
- [ ] Max concurrent session limit is enforced

## Files to Create
```
src/main/java/com/serverwatch/
├── model/dto/
│   ├── TerminalSessionDTO.java
│   ├── TerminalDimensions.java
│   ├── TerminalInputMessage.java
│   ├── TerminalOutputMessage.java
│   └── CreateSessionRequest.java
├── terminal/
│   ├── TerminalSession.java
│   └── CircularBuffer.java
├── service/
│   └── TerminalService.java
├── websocket/
│   └── TerminalWebSocketController.java
└── controller/
    └── TerminalController.java

MODIFY:
├── pom.xml — add pty4j dependency
├── config/ServerWatchProperties.java — add Terminal nested config
├── websocket/WebSocketEventListener.java — close terminals on disconnect
└── application.yml — add terminal config section
```
