package com.serverwatch.alert;

import com.serverwatch.alert.notifier.Notifier;
import com.serverwatch.model.dto.AlertEventDTO;
import com.serverwatch.model.dto.ContainerStatsDTO;
import com.serverwatch.model.dto.NetworkMetricDTO;
import com.serverwatch.model.dto.SystemMetricDTO;
import com.serverwatch.model.entity.AlertHistory;
import com.serverwatch.model.entity.AlertRule;
import com.serverwatch.repository.AlertHistoryRepository;
import com.serverwatch.repository.AlertRuleRepository;
import com.serverwatch.service.DockerService;
import com.serverwatch.service.MetricService;
import com.serverwatch.service.WebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Core alert evaluation loop.
 *
 * <p>Runs every {@code serverwatch.alert.evaluation-interval-ms} milliseconds
 * (default 5 s). For each enabled rule it:
 * <ol>
 *   <li>Checks cooldown — skips if last alert was within {@code cooldownMinutes}</li>
 *   <li>Reads the current metric value from {@link MetricService} or
 *       {@link DockerService}</li>
 *   <li>Evaluates the rule via {@link AlertEvaluator}</li>
 *   <li>If triggered: saves history, dispatches notifications on a separate
 *       thread pool, and pushes the event to {@code /topic/alerts}</li>
 * </ol>
 *
 * <p>Notification dispatch runs on the {@code alertNotificationExecutor} pool
 * and is entirely decoupled from the evaluation loop — notification failures
 * never interrupt rule evaluation.
 */
@Service
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

    private final AlertRuleRepository    ruleRepo;
    private final AlertHistoryRepository historyRepo;
    private final AlertEvaluator         evaluator;
    private final List<Notifier>         notifiers;
    private final MetricService          metricService;
    private final DockerService          dockerService;
    private final WebSocketPublisher     wsPublisher;
    private final Executor               notificationExecutor;

    /** In-memory cooldown tracker: ruleId → last alert time. */
    private final Map<Long, Instant> lastAlertTimes = new ConcurrentHashMap<>();

    public AlertEngine(AlertRuleRepository ruleRepo,
                       AlertHistoryRepository historyRepo,
                       AlertEvaluator evaluator,
                       List<Notifier> notifiers,
                       MetricService metricService,
                       DockerService dockerService,
                       WebSocketPublisher wsPublisher,
                       @Qualifier("alertNotificationExecutor") Executor notificationExecutor) {
        this.ruleRepo             = ruleRepo;
        this.historyRepo          = historyRepo;
        this.evaluator            = evaluator;
        this.notifiers            = notifiers;
        this.metricService        = metricService;
        this.dockerService        = dockerService;
        this.wsPublisher          = wsPublisher;
        this.notificationExecutor = notificationExecutor;
    }

    // ── Evaluation loop ───────────────────────────────────────────────────────

    /**
     * Evaluates all enabled rules. Runs every
     * {@code serverwatch.alert.evaluation-interval-ms} milliseconds.
     */
    @Scheduled(fixedDelayString = "${serverwatch.alert.evaluation-interval-ms:5000}")
    public void evaluateRules() {
        List<AlertRule> rules = ruleRepo.findByEnabledTrue();
        if (rules.isEmpty()) return;

        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.error("Error evaluating alert rule '{}' (id={}): {}",
                        rule.getName(), rule.getId(), e.getMessage());
            }
        }
    }

    /**
     * Deletes alert history entries older than 30 days. Runs at 02:00 daily.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupHistory() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = historyRepo.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} alert history records older than 30 days", deleted);
        }
    }

    // ── Internal evaluation ───────────────────────────────────────────────────

    private void evaluateRule(AlertRule rule) {
        // 1. Cooldown check
        Instant lastAlert = lastAlertTimes.get(rule.getId());
        if (lastAlert != null) {
            Instant nextAllowed = lastAlert.plus(rule.getCooldownMinutes(), ChronoUnit.MINUTES);
            if (nextAllowed.isAfter(Instant.now())) {
                log.debug("Rule '{}' is in cooldown until {}", rule.getName(), nextAllowed);
                return;
            }
        }

        // 2. Read current metric value
        double currentValue = getCurrentMetricValue(rule);

        // 3. Evaluate threshold
        if (!evaluator.evaluate(currentValue, rule.getOperator(), rule.getThreshold())) {
            return;
        }

        // 4. Build event
        AlertEventDTO event = AlertEventDTO.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .metricType(rule.getMetricType())
                .currentValue(currentValue)
                .threshold(rule.getThreshold())
                .operator(rule.getOperator())
                .severity(evaluator.determineSeverity(currentValue, rule.getThreshold()))
                .message(evaluator.buildMessage(
                        rule.getMetricType(), currentValue, rule.getOperator(), rule.getThreshold()))
                .triggeredAt(Instant.now())
                .notified(false)
                .notificationChannels(List.of())
                .build();

        // 5. Persist to history (synchronous — history must exist before notifications fire)
        AlertHistory saved = persistHistory(event, rule);
        event.setId(saved.getId());

        // 6. Dispatch notifications on the dedicated thread pool (async, non-blocking)
        final AlertHistory historyRef = saved;
        notificationExecutor.execute(() -> dispatchNotifications(event, rule, historyRef));

        // 7. Push to WebSocket subscribers
        wsPublisher.publishAlert(event);

        // 8. Update cooldown
        lastAlertTimes.put(rule.getId(), Instant.now());

        log.warn("Alert TRIGGERED — rule='{}' metric={} value={:.1f}% severity={}",
                rule.getName(), rule.getMetricType(), currentValue, event.getSeverity());
    }

    // ── Metric resolution ─────────────────────────────────────────────────────

    /**
     * Reads the current value for the metric type specified in the rule.
     * Returns {@code 0.0} if data is unavailable (e.g. no collection cycle yet,
     * or Docker not running for container metrics).
     */
    private double getCurrentMetricValue(AlertRule rule) {
        return switch (rule.getMetricType()) {
            case "CPU_USAGE"       -> getSystemMetricValue(rule, "cpu");
            case "MEMORY_USAGE"    -> getSystemMetricValue(rule, "memory");
            case "SWAP_USAGE"      -> getSystemMetricValue(rule, "swap");
            case "DISK_USAGE"      -> getSystemMetricValue(rule, "disk");
            case "CONTAINER_CPU"   -> getContainerMetric(rule.getContainerName(), "cpu");
            case "CONTAINER_MEMORY"-> getContainerMetric(rule.getContainerName(), "memory");
            case "NETWORK_RX_RATE" -> getNetworkMetric(rule.getNetworkInterface(), "rx");
            case "NETWORK_TX_RATE" -> getNetworkMetric(rule.getNetworkInterface(), "tx");
            default -> throw new IllegalArgumentException(
                    "Unknown metric type: " + rule.getMetricType());
        };
    }

    private double getSystemMetricValue(AlertRule rule, String aspect) {
        SystemMetricDTO metrics = metricService.getLatestSystemMetric();
        if (metrics == null) return 0.0;
        return switch (aspect) {
            case "cpu"    -> metrics.cpuUsagePercent();
            case "memory" -> metrics.memoryUsagePercent();
            case "swap"   -> metrics.swapTotalBytes() == 0 ? 0.0
                    : (metrics.swapUsedBytes() * 100.0) / metrics.swapTotalBytes();
            case "disk"   -> metrics.diskInfos().stream()
                    .mapToDouble(d -> d.usagePercent())
                    .max().orElse(0.0);
            default       -> 0.0;
        };
    }

    private double getContainerMetric(String containerName, String aspect) {
        if (containerName == null || containerName.isBlank()) return 0.0;
        try {
            List<ContainerStatsDTO> stats = dockerService.getAllContainerStats();
            return stats.stream()
                    .filter(s -> containerName.equals(s.containerName())
                              || containerName.equals(s.containerId()))
                    .findFirst()
                    .map(s -> "cpu".equals(aspect) ? s.cpuPercent() : s.memoryPercent())
                    .orElse(0.0);
        } catch (Exception e) {
            log.debug("Cannot read container metric for '{}': {}", containerName, e.getMessage());
            return 0.0;
        }
    }

    private double getNetworkMetric(String interfaceName, String direction) {
        List<NetworkMetricDTO> nets = metricService.getLatestNetworkMetrics();
        return nets.stream()
                .filter(n -> interfaceName == null
                          || interfaceName.isBlank()
                          || interfaceName.equals(n.interfaceName()))
                .findFirst()
                .map(n -> "rx".equals(direction)
                        ? (double) n.receivedPerSecond() : (double) n.sentPerSecond())
                .orElse(0.0);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Transactional
    AlertHistory persistHistory(AlertEventDTO event, AlertRule rule) {
        AlertHistory history = new AlertHistory();
        history.setRuleId(rule.getId());
        history.setMetricType(event.getMetricType());
        history.setMetricValue(event.getCurrentValue());
        history.setThreshold(event.getThreshold());
        history.setOperator(event.getOperator());
        history.setMessage(event.getMessage());
        history.setSeverity(event.getSeverity());
        history.setNotified(false);
        return historyRepo.save(history);
    }

    // ── Notification dispatch (runs on alertNotificationExecutor) ─────────────

    private void dispatchNotifications(AlertEventDTO event, AlertRule rule, AlertHistory history) {
        List<String> channels = new ArrayList<>();

        for (Notifier notifier : notifiers) {
            boolean shouldNotify =
                    ("email".equals(notifier.getType())   && rule.isNotifyEmail())   ||
                    ("webhook".equals(notifier.getType()) && rule.isNotifyWebhook());

            if (shouldNotify) {
                try {
                    notifier.send(event, rule);
                    channels.add(notifier.getType());
                } catch (Exception e) {
                    log.error("Notifier '{}' failed for rule '{}': {}",
                            notifier.getType(), rule.getName(), e.getMessage());
                }
            }
        }

        // Update the history record with notification outcome
        if (!channels.isEmpty()) {
            try {
                updateHistoryNotification(history.getId(), channels);
            } catch (Exception e) {
                log.warn("Could not update notification status in history: {}", e.getMessage());
            }
        }

        event.setNotificationChannels(channels);
        event.setNotified(!channels.isEmpty());
    }

    @Transactional
    void updateHistoryNotification(Long historyId, List<String> channels) {
        historyRepo.findById(historyId).ifPresent(h -> {
            h.setNotified(true);
            h.setNotificationChannels(String.join(",", channels));
            historyRepo.save(h);
        });
    }
}
