package com.serverwatch.websocket;

import com.serverwatch.model.dto.SystemMetricDTO;
import com.serverwatch.model.entity.MetricSnapshot;
import com.serverwatch.service.MetricService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Handles inbound STOMP messages from clients (interactive WebSocket commands).
 *
 * <p>All {@code @MessageMapping} paths are relative to the application destination
 * prefix configured in {@link com.serverwatch.config.WebSocketConfig} ({@code /app}).
 * So a client sends to {@code /app/metrics/request} to trigger a response.
 */
@Controller
public class MetricWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(MetricWebSocketController.class);

    private final MetricService metricService;
    private final SimpMessagingTemplate messagingTemplate;

    public MetricWebSocketController(MetricService metricService,
                                     SimpMessagingTemplate messagingTemplate) {
        this.metricService     = metricService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Client sends to {@code /app/metrics/request} to get an immediate
     * system metric snapshot without waiting for the next scheduled broadcast.
     * The response is broadcast to {@code /topic/metrics/system}.
     *
     * @return the latest cached {@link SystemMetricDTO}
     */
    @MessageMapping("/metrics/request")
    @SendTo("/topic/metrics/system")
    public SystemMetricDTO requestImmediate() {
        log.debug("Immediate metric snapshot requested via WebSocket");
        return metricService.getLatestSystemMetric();
    }

    /**
     * Client sends to {@code /app/metrics/history} with a JSON body:
     * <pre>{"type":"CPU_USAGE","hours":"6"}</pre>
     * The historical data is sent back only to the requesting session via
     * {@code /user/queue/history}.
     *
     * @param request         map with keys {@code type} (required) and {@code hours} (optional, default 1)
     * @param headerAccessor  used to extract the session ID for user-targeted reply
     */
    @MessageMapping("/metrics/history")
    @SendToUser("/queue/history")
    public List<MetricSnapshot> requestHistory(
            @Payload Map<String, String> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String type  = request.getOrDefault("type", "CPU_USAGE");
        int    hours = parseHours(request.getOrDefault("hours", "1"));

        log.debug("History request via WebSocket: type={}, hours={}, session={}",
                type, hours, headerAccessor.getSessionId());

        return metricService.getHistory(
                type,
                Instant.now().minus(hours, ChronoUnit.HOURS),
                Instant.now()
        );
    }

    // -------------------------------------------------------------------------

    private static int parseHours(String raw) {
        try {
            int h = Integer.parseInt(raw);
            return Math.min(Math.max(h, 1), 168); // clamp 1–168
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
