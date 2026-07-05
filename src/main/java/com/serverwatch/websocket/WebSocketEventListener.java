package com.serverwatch.websocket;

import com.serverwatch.service.DockerService;
import com.serverwatch.service.TerminalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks active STOMP WebSocket sessions and cleans up per-session resources
 * (container log streams, terminal PTY sessions) when a client disconnects.
 *
 * <p>Both {@link DockerService} and {@link TerminalService} are injected via
 * {@code @Autowired} setter (not constructor) to prevent a potential
 * circular-proxy issue during Spring WebSocket broker initialisation, since
 * those services depend on {@link com.serverwatch.service.WebSocketPublisher}
 * which is created by the same broker infrastructure.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final AtomicInteger connectedClients = new AtomicInteger(0);

    private DockerService dockerService;
    private TerminalService terminalService;

    @Autowired
    public void setDockerService(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    @Autowired
    public void setTerminalService(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        int count = connectedClients.incrementAndGet();
        log.info("WebSocket client connected. Total active sessions: {}", count);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        int count = connectedClients.decrementAndGet();
        log.info("WebSocket client disconnected (session={}). Total active sessions: {}",
                sessionId, count);

        // Clean up any active container log stream for this session
        if (dockerService != null) {
            dockerService.stopLogStream(sessionId);
        }

        // Close all PTY terminal sessions owned by this WebSocket connection
        if (terminalService != null) {
            terminalService.closeSessionsForWsClient(sessionId);
        }
    }

    /**
     * Returns the current number of connected WebSocket clients.
     *
     * @return session count (may briefly go negative during hot-restart due to
     *         disconnect events firing against a freshly reset counter)
     */
    public int getConnectedClientCount() {
        return connectedClients.get();
    }
}
