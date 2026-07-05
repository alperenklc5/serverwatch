package com.serverwatch.scheduler;

import com.serverwatch.model.dto.ContainerStatsDTO;
import com.serverwatch.service.DockerService;
import com.serverwatch.service.WebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically collects Docker container stats and broadcasts them to all
 * connected WebSocket clients on the {@code /topic/containers} topic.
 *
 * <p>If Docker is unavailable (socket not accessible, daemon stopped) the
 * error is logged at DEBUG level and the cycle is skipped — no stack-trace
 * flooding in normal dev environments where Docker may not be present.
 */
@Component
public class ContainerMetricScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContainerMetricScheduler.class);

    private final DockerService      dockerService;
    private final WebSocketPublisher wsPublisher;

    public ContainerMetricScheduler(DockerService dockerService,
                                    WebSocketPublisher wsPublisher) {
        this.dockerService = dockerService;
        this.wsPublisher   = wsPublisher;
    }

    /**
     * Collects stats for all running containers and pushes them to
     * {@code /topic/containers} every 3 seconds.
     */
    @Scheduled(fixedDelay = 3000)
    public void broadcastContainerStats() {
        try {
            List<ContainerStatsDTO> stats = dockerService.getAllContainerStats();
            if (!stats.isEmpty()) {
                wsPublisher.publishContainerStats(stats);
                log.debug("Container stats broadcast: {} containers", stats.size());
            }
        } catch (Exception e) {
            // Docker not available on this host — log at debug to avoid noise
            log.debug("Container stats collection skipped: {}", e.getMessage());
        }
    }
}
