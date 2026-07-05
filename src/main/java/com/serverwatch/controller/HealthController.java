package com.serverwatch.controller;

import com.github.dockerjava.api.DockerClient;
import com.serverwatch.service.DockerService;
import com.serverwatch.websocket.WebSocketEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple liveness/readiness endpoint.
 * Checks Docker socket reachability and database connectivity without throwing.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DockerClient           dockerClient;
    private final DataSource             dataSource;
    private final WebSocketEventListener wsEventListener;
    private final DockerService          dockerService;

    public HealthController(DockerClient dockerClient,
                            DataSource dataSource,
                            WebSocketEventListener wsEventListener,
                            DockerService dockerService) {
        this.dockerClient    = dockerClient;
        this.dataSource      = dataSource;
        this.wsEventListener = wsEventListener;
        this.dockerService   = dockerService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        // Use LinkedHashMap for deterministic key order in the response
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",             "UP");
        body.put("version",            "1.0.0");
        body.put("timestamp",          Instant.now().toString());
        body.put("docker",             checkDocker());
        body.put("database",           checkDatabase());
        body.put("websocketClients",   wsEventListener.getConnectedClientCount());
        body.put("runningContainers",  dockerService.getRunningContainerCount());
        return ResponseEntity.ok(body);
    }

    private boolean checkDocker() {
        try {
            dockerClient.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.debug("Docker health check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            log.debug("Database health check failed: {}", e.getMessage());
            return false;
        }
    }
}
