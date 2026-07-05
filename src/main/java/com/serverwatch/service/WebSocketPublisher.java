package com.serverwatch.service;

import com.serverwatch.model.dto.AlertEventDTO;
import com.serverwatch.model.dto.ContainerStatsDTO;
import com.serverwatch.model.dto.NetworkMetricDTO;
import com.serverwatch.model.dto.ProcessInfoDTO;
import com.serverwatch.model.dto.SystemMetricDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Pushes data to STOMP broadcast topics and user-specific queues.
 *
 * <p>Each {@code publish*} method wraps
 * {@link SimpMessagingTemplate#convertAndSend}, serialising the payload to JSON
 * via Jackson and delivering it to all destination subscribers.
 * If no clients are subscribed the call is a cheap no-op.
 *
 * <h3>Topics</h3>
 * <ul>
 *   <li>{@code /topic/metrics/system}    — {@link SystemMetricDTO} every 2 s</li>
 *   <li>{@code /topic/metrics/network}   — {@code List<NetworkMetricDTO>} every 2 s</li>
 *   <li>{@code /topic/metrics/processes} — {@code List<ProcessInfoDTO>} every 5 s</li>
 *   <li>{@code /topic/containers}        — {@code List<ContainerStatsDTO>} every 3 s</li>
 * </ul>
 * <h3>User queues (session-targeted)</h3>
 * <ul>
 *   <li>{@code /user/{sid}/queue/container-logs} — live log lines</li>
 *   <li>{@code /user/{sid}/queue/errors}         — error messages</li>
 *   <li>{@code /user/{sid}/queue/history}         — metric history replies</li>
 * </ul>
 */
@Service
public class WebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebSocketPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // ── Broadcast topics ─────────────────────────────────────────────────────

    /**
     * Broadcasts the latest system metric snapshot to all subscribers.
     *
     * @param metrics current {@link SystemMetricDTO}
     */
    public void publishSystemMetrics(SystemMetricDTO metrics) {
        messagingTemplate.convertAndSend("/topic/metrics/system", metrics);
        log.debug("Published system metrics to /topic/metrics/system");
    }

    /**
     * Broadcasts per-interface network metrics to all subscribers.
     *
     * @param metrics list of {@link NetworkMetricDTO}, one per interface
     */
    public void publishNetworkMetrics(List<NetworkMetricDTO> metrics) {
        messagingTemplate.convertAndSend("/topic/metrics/network", metrics);
        log.debug("Published network metrics ({} interfaces) to /topic/metrics/network",
                metrics.size());
    }

    /**
     * Broadcasts the current top-process list to all subscribers.
     *
     * @param processes list of {@link ProcessInfoDTO} sorted by CPU desc
     */
    public void publishProcesses(List<ProcessInfoDTO> processes) {
        messagingTemplate.convertAndSend("/topic/metrics/processes", processes);
        log.debug("Published {} processes to /topic/metrics/processes", processes.size());
    }

    /**
     * Broadcasts container resource-usage snapshots to all subscribers.
     *
     * @param stats list of {@link ContainerStatsDTO}, one per running container
     */
    public void publishContainerStats(List<ContainerStatsDTO> stats) {
        messagingTemplate.convertAndSend("/topic/containers", stats);
        log.debug("Published container stats ({} containers) to /topic/containers", stats.size());
    }

    // ── User-targeted queues ──────────────────────────────────────────────────

    /**
     * Sends a single container log line to a specific WebSocket session.
     *
     * @param sessionId   STOMP session ID of the subscribing client
     * @param containerId short container ID (for client-side correlation)
     * @param line        log text (already stripped of trailing whitespace)
     * @param stderr      {@code true} if the line originated from stderr
     */
    public void sendContainerLogLine(String sessionId, String containerId,
                                     String line, boolean stderr) {
        Map<String, Object> payload = Map.of(
                "containerId", containerId,
                "line",        line,
                "stderr",      stderr,
                "timestamp",   Instant.now().toString()
        );
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/container-logs", payload);
    }

    /**
     * Broadcasts a triggered alert event to all subscribers.
     *
     * <p>The payload is serialised as JSON and delivered to
     * {@code /topic/alerts}. Clients can subscribe to this topic to
     * display real-time alert notifications in the dashboard.
     *
     * @param alert the fired {@link AlertEventDTO}
     */
    public void publishAlert(AlertEventDTO alert) {
        messagingTemplate.convertAndSend("/topic/alerts", alert);
        log.debug("Published alert '{}' [{}] to /topic/alerts",
                alert.getRuleName(), alert.getSeverity());
    }

    /**
     * Sends an error message directly to a specific client session.
     *
     * @param sessionId the STOMP session ID
     * @param message   human-readable error text
     */
    public void sendErrorToUser(String sessionId, String message) {
        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/errors",
                Map.of("error", message, "timestamp", Instant.now().toString())
        );
    }
}
