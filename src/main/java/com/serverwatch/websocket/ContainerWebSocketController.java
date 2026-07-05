package com.serverwatch.websocket;

import com.serverwatch.service.DockerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * Handles inbound WebSocket commands for container log streaming.
 *
 * <pre>
 * Client sends to /app/container/logs/start  — payload: {"containerId":"abc123"}
 * Client sends to /app/container/logs/stop   — no payload needed
 * </pre>
 *
 * <p>Log lines are delivered back to the client via
 * {@code /user/{sessionId}/queue/container-logs}. The stream is automatically
 * closed when the WebSocket session disconnects (handled by
 * {@link WebSocketEventListener}).
 */
@Controller
public class ContainerWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ContainerWebSocketController.class);

    private final DockerService dockerService;

    public ContainerWebSocketController(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    /**
     * Starts streaming logs for the specified container to the requesting session.
     *
     * @param request  JSON payload with key {@code containerId}
     * @param accessor used to extract the session ID for routing replies
     */
    @MessageMapping("/container/logs/start")
    public void startLogStream(@Payload Map<String, String> request,
                               SimpMessageHeaderAccessor accessor) {
        String containerId = request.get("containerId");
        String sessionId   = accessor.getSessionId();

        if (containerId == null || containerId.isBlank()) {
            log.warn("startLogStream called without containerId from session {}", sessionId);
            return;
        }

        log.info("Log stream requested: container={} session={}", containerId, sessionId);
        dockerService.streamContainerLogs(containerId, sessionId);
    }

    /**
     * Stops any active log stream for the requesting session.
     *
     * @param accessor used to extract the session ID
     */
    @MessageMapping("/container/logs/stop")
    public void stopLogStream(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        log.info("Log stream stop requested for session {}", sessionId);
        dockerService.stopLogStream(sessionId);
    }
}
