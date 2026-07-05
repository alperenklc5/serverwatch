package com.serverwatch.service;

import com.serverwatch.collector.NetworkMetricCollector;
import com.serverwatch.collector.ProcessCollector;
import com.serverwatch.collector.SystemMetricCollector;
import com.serverwatch.config.ServerWatchProperties;
import com.serverwatch.model.dto.NetworkMetricDTO;
import com.serverwatch.model.dto.ProcessInfoDTO;
import com.serverwatch.model.dto.SystemMetricDTO;
import com.serverwatch.model.dto.UptimeDTO;
import com.serverwatch.model.entity.MetricSnapshot;
import com.serverwatch.repository.MetricSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates all metric collectors on a fixed schedule.
 *
 * <p>The latest system and network metrics are cached in volatile fields for
 * lock-free reads by the REST controllers. After each collection cycle the
 * results are also broadcast to all connected WebSocket clients via
 * {@link WebSocketPublisher}.
 *
 * <p>Snapshot persistence to PostgreSQL runs every 60 seconds (every 30th
 * two-second cycle). Stale snapshots are cleaned up once per hour.
 */
@Service
public class MetricService {

    private static final Logger log = LoggerFactory.getLogger(MetricService.class);
    private static final int SNAPSHOT_EVERY_N_CYCLES = 30; // 30 × 2 s = 60 s

    private final SystemMetricCollector     systemCollector;
    private final NetworkMetricCollector    networkCollector;
    private final ProcessCollector          processCollector;
    private final MetricSnapshotRepository  snapshotRepo;
    private final ServerWatchProperties     properties;
    private final WebSocketPublisher        webSocketPublisher;

    private volatile SystemMetricDTO        latestSystemMetric;
    private volatile List<NetworkMetricDTO> latestNetworkMetrics = List.of();
    private final AtomicInteger             cycleCounter = new AtomicInteger(0);

    public MetricService(SystemMetricCollector systemCollector,
                         NetworkMetricCollector networkCollector,
                         ProcessCollector processCollector,
                         MetricSnapshotRepository snapshotRepo,
                         ServerWatchProperties properties,
                         WebSocketPublisher webSocketPublisher) {
        this.systemCollector    = systemCollector;
        this.networkCollector   = networkCollector;
        this.processCollector   = processCollector;
        this.snapshotRepo       = snapshotRepo;
        this.properties         = properties;
        this.webSocketPublisher = webSocketPublisher;
    }

    // -------------------------------------------------------------------------
    // Scheduled collection
    // -------------------------------------------------------------------------

    /**
     * Runs every {@code serverwatch.collector.interval-ms} milliseconds (default 2 s).
     * Collects system and network metrics, refreshes the in-memory cache,
     * pushes updates to WebSocket subscribers, and persists a DB snapshot
     * every 60 seconds.
     */
    @Scheduled(fixedDelayString = "${serverwatch.collector.interval-ms:2000}")
    public void collectAll() {
        collectSystem();
        collectNetwork();

        int cycle = cycleCounter.incrementAndGet();
        if (cycle % SNAPSHOT_EVERY_N_CYCLES == 0) {
            persistSnapshots();
        }
    }

    /**
     * Publishes process data to WebSocket subscribers every 5 seconds.
     * Kept separate from {@link #collectAll()} because process enumeration
     * is more expensive and doesn't need 2-second resolution.
     */
    @Scheduled(fixedDelay = 5000)
    public void collectProcesses() {
        try {
            List<ProcessInfoDTO> processes = processCollector.collectTopProcesses(20);
            webSocketPublisher.publishProcesses(processes);
            log.debug("Published {} processes to WebSocket", processes.size());
        } catch (Exception e) {
            log.error("Failed to collect/publish process info", e);
        }
    }

    /**
     * Deletes snapshots older than the configured retention window.
     * Runs at the top of every hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOldSnapshots() {
        Instant cutoff = Instant.now().minus(
                properties.getCollector().getHistoryRetentionHours(), ChronoUnit.HOURS
        );
        int deleted = snapshotRepo.deleteOlderThan(cutoff);
        log.info("Cleaned up {} old metric snapshots", deleted);
    }

    // -------------------------------------------------------------------------
    // Public accessors (used by REST controllers)
    // -------------------------------------------------------------------------

    /**
     * Returns the most recently collected system metrics, or {@code null} if
     * no collection cycle has completed yet.
     */
    public SystemMetricDTO getLatestSystemMetric() {
        return latestSystemMetric;
    }

    /** Returns the most recently collected network interface metrics. */
    public List<NetworkMetricDTO> getLatestNetworkMetrics() {
        return latestNetworkMetrics;
    }

    /**
     * Returns the current top-N processes by CPU usage (live query, not cached).
     *
     * @param limit maximum processes to return
     */
    public List<ProcessInfoDTO> getTopProcesses(int limit) {
        return processCollector.collectTopProcesses(limit);
    }

    /** Returns uptime and OS identification (live query via system collector). */
    public UptimeDTO getUptime() {
        return systemCollector.collectUptime();
    }

    /**
     * Queries the database for metric snapshots of a given type within a time window.
     *
     * @param metricType the logical metric type (e.g. "CPU_USAGE")
     * @param from       start of the window (inclusive)
     * @param to         end of the window (inclusive)
     */
    public List<MetricSnapshot> getHistory(String metricType, Instant from, Instant to) {
        return snapshotRepo.findByMetricTypeAndRecordedAtBetween(metricType, from, to);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void collectSystem() {
        try {
            SystemMetricDTO metric = systemCollector.collect();
            latestSystemMetric = metric;
            webSocketPublisher.publishSystemMetrics(metric);
            log.debug("System metrics collected and published: cpu={}%", metric.cpuUsagePercent());
        } catch (Exception e) {
            log.error("Failed to collect system metrics", e);
        }
    }

    private void collectNetwork() {
        try {
            List<NetworkMetricDTO> metrics = networkCollector.collect();
            latestNetworkMetrics = metrics;
            webSocketPublisher.publishNetworkMetrics(metrics);
            log.debug("Network metrics collected and published: {} interfaces", metrics.size());
        } catch (Exception e) {
            log.error("Failed to collect network metrics", e);
        }
    }

    @Transactional
    void persistSnapshots() {
        SystemMetricDTO sys = latestSystemMetric;
        if (sys == null) return;

        Instant now = Instant.now();
        List<MetricSnapshot> batch = new ArrayList<>();

        batch.add(new MetricSnapshot("CPU_USAGE",    null, sys.cpuUsagePercent(),    "%",   now));
        batch.add(new MetricSnapshot("MEMORY_USAGE", null, sys.memoryUsagePercent(), "%",   now));
        batch.add(new MetricSnapshot("MEMORY_USED",  null, sys.memoryUsedBytes(),    "B",   now));
        batch.add(new MetricSnapshot("SWAP_USED",    null, sys.swapUsedBytes(),      "B",   now));

        for (var disk : sys.diskInfos()) {
            batch.add(new MetricSnapshot("DISK_USAGE", disk.mountPoint(),
                    disk.usagePercent(), "%", now));
        }

        for (NetworkMetricDTO net : latestNetworkMetrics) {
            batch.add(new MetricSnapshot("NET_RECV", net.interfaceName(),
                    net.receivedPerSecond(), "B/s", now));
            batch.add(new MetricSnapshot("NET_SENT", net.interfaceName(),
                    net.sentPerSecond(), "B/s", now));
        }

        snapshotRepo.saveAll(batch);
        log.debug("Persisted {} metric snapshots", batch.size());
    }
}
