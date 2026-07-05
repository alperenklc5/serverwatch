package com.serverwatch.controller;

import com.serverwatch.model.dto.ApiResponse;
import com.serverwatch.model.dto.NetworkMetricDTO;
import com.serverwatch.model.dto.ProcessInfoDTO;
import com.serverwatch.model.dto.SystemMetricDTO;
import com.serverwatch.model.dto.UptimeDTO;
import com.serverwatch.model.entity.MetricSnapshot;
import com.serverwatch.service.MetricService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * REST endpoints for system metrics, network stats, process info, uptime,
 * and historical snapshot queries.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricController {

    private final MetricService metricService;

    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    /**
     * Returns the latest cached CPU, memory, swap, and disk snapshot.
     *
     * @return 200 with {@link SystemMetricDTO}, or 503 if no data yet
     */
    @GetMapping("/system")
    public ResponseEntity<ApiResponse<SystemMetricDTO>> getSystemMetrics() {
        SystemMetricDTO metric = metricService.getLatestSystemMetric();
        if (metric == null) {
            return ResponseEntity.status(503)
                    .body(ApiResponse.error("Metrics not yet available — first collection cycle pending"));
        }
        return ResponseEntity.ok(ApiResponse.ok(metric));
    }

    /**
     * Returns the latest cached network interface statistics.
     *
     * @return 200 with list of {@link NetworkMetricDTO}
     */
    @GetMapping("/network")
    public ResponseEntity<ApiResponse<List<NetworkMetricDTO>>> getNetworkMetrics() {
        return ResponseEntity.ok(ApiResponse.ok(metricService.getLatestNetworkMetrics()));
    }

    /**
     * Returns the top processes by CPU usage.
     *
     * @param limit max number of processes (default 20, max 200)
     * @return 200 with list of {@link ProcessInfoDTO}
     */
    @GetMapping("/processes")
    public ResponseEntity<ApiResponse<List<ProcessInfoDTO>>> getProcesses(
            @RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > 200) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("limit must be between 1 and 200"));
        }
        return ResponseEntity.ok(ApiResponse.ok(metricService.getTopProcesses(limit)));
    }

    /**
     * Returns system uptime and OS identification.
     *
     * @return 200 with {@link UptimeDTO}
     */
    @GetMapping("/uptime")
    public ResponseEntity<ApiResponse<UptimeDTO>> getUptime() {
        return ResponseEntity.ok(ApiResponse.ok(metricService.getUptime()));
    }

    /**
     * Queries persisted metric snapshots over a trailing time window.
     *
     * @param type  metric type key, e.g. {@code CPU_USAGE}, {@code MEMORY_USAGE}
     * @param hours number of trailing hours to include (default 1, max 168)
     * @return 200 with list of {@link MetricSnapshot}
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<MetricSnapshot>>> getHistory(
            @RequestParam String type,
            @RequestParam(defaultValue = "1") int hours) {
        if (hours < 1 || hours > 168) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("hours must be between 1 and 168"));
        }
        Instant to   = Instant.now();
        Instant from = to.minus(hours, ChronoUnit.HOURS);
        return ResponseEntity.ok(ApiResponse.ok(metricService.getHistory(type, from, to)));
    }
}
