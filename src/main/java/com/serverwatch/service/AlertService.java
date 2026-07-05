package com.serverwatch.service;

import com.serverwatch.alert.notifier.Notifier;
import com.serverwatch.model.dto.AlertEventDTO;
import com.serverwatch.model.dto.AlertRuleDTO;
import com.serverwatch.model.entity.AlertHistory;
import com.serverwatch.model.entity.AlertRule;
import com.serverwatch.repository.AlertHistoryRepository;
import com.serverwatch.repository.AlertRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * CRUD service for alert rules and retrieval of alert history.
 *
 * <p>Also exposes a test-notification method that sends a synthetic alert
 * through all configured channels for a rule, allowing users to verify
 * their email/webhook setup without waiting for a real threshold breach.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private static final Set<String> VALID_METRIC_TYPES = Set.of(
            "CPU_USAGE", "MEMORY_USAGE", "SWAP_USAGE", "DISK_USAGE",
            "CONTAINER_CPU", "CONTAINER_MEMORY", "NETWORK_RX_RATE", "NETWORK_TX_RATE"
    );
    private static final Set<String> VALID_OPERATORS = Set.of("GT", "GTE", "LT", "LTE", "EQ");

    private final AlertRuleRepository    ruleRepo;
    private final AlertHistoryRepository historyRepo;
    private final List<Notifier>         notifiers;

    public AlertService(AlertRuleRepository ruleRepo,
                        AlertHistoryRepository historyRepo,
                        List<Notifier> notifiers) {
        this.ruleRepo    = ruleRepo;
        this.historyRepo = historyRepo;
        this.notifiers   = notifiers;
    }

    // ── Rule CRUD ─────────────────────────────────────────────────────────────

    /**
     * Returns all alert rules.
     */
    public List<AlertRuleDTO> getAllRules() {
        return ruleRepo.findAll().stream()
                .map(AlertRule::toDto)
                .toList();
    }

    /**
     * Returns a single rule by ID.
     *
     * @throws EntityNotFoundException if no rule with that ID exists
     */
    public AlertRuleDTO getRule(Long id) {
        return requireRule(id).toDto();
    }

    /**
     * Creates and persists a new alert rule.
     *
     * @param dto creation request
     * @return the persisted rule as a DTO
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public AlertRuleDTO createRule(AlertRuleDTO dto) {
        validate(dto);
        AlertRule rule = AlertRule.fromDto(dto);
        return ruleRepo.save(rule).toDto();
    }

    /**
     * Updates an existing alert rule.
     *
     * @param id  rule ID to update
     * @param dto replacement values
     * @return the updated rule as a DTO
     * @throws EntityNotFoundException  if no rule with that ID exists
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public AlertRuleDTO updateRule(Long id, AlertRuleDTO dto) {
        validate(dto);
        AlertRule rule = requireRule(id);
        rule.applyDto(dto);
        return ruleRepo.save(rule).toDto();
    }

    /**
     * Deletes an alert rule and all its history (cascaded by DB constraint).
     *
     * @throws EntityNotFoundException if no rule with that ID exists
     */
    @Transactional
    public void deleteRule(Long id) {
        AlertRule rule = requireRule(id);
        ruleRepo.delete(rule);
        log.info("Deleted alert rule '{}' (id={})", rule.getName(), id);
    }

    /**
     * Enables or disables an alert rule.
     *
     * @param id      rule ID
     * @param enabled desired state
     */
    @Transactional
    public AlertRuleDTO toggleRule(Long id, boolean enabled) {
        AlertRule rule = requireRule(id);
        rule.setEnabled(enabled);
        return ruleRepo.save(rule).toDto();
    }

    // ── History queries ───────────────────────────────────────────────────────

    /**
     * Returns the most recent {@code limit} alerts in the last {@code hours} hours.
     *
     * @param hours  look-back window (1–168)
     * @param limit  max results (1–500)
     */
    public List<AlertEventDTO> getRecentAlerts(int hours, int limit) {
        int boundedHours = Math.clamp(hours, 1, 168);
        Instant from = Instant.now().minus(boundedHours, ChronoUnit.HOURS);
        return historyRepo.findByCreatedAtBetweenOrderByCreatedAtDesc(from, Instant.now())
                .stream()
                .limit(Math.clamp(limit, 1, 500))
                .map(this::toEventDTO)
                .toList();
    }

    /**
     * Returns the most recent {@code limit} history entries for a specific rule.
     *
     * @param ruleId rule ID
     * @param limit  max results (1–200)
     */
    public List<AlertEventDTO> getAlertHistory(Long ruleId, int limit) {
        int bounded = Math.clamp(limit, 1, 200);
        return historyRepo.findByRuleIdOrderByCreatedAtDesc(
                        ruleId, PageRequest.of(0, bounded))
                .stream()
                .map(this::toEventDTO)
                .toList();
    }

    // ── Test notification ─────────────────────────────────────────────────────

    /**
     * Sends a synthetic test notification through all channels configured for a rule.
     * Does NOT save to history and does NOT affect the cooldown timer.
     *
     * @param ruleId rule ID
     * @throws EntityNotFoundException if no rule with that ID exists
     */
    public void testNotification(Long ruleId) {
        AlertRule rule = requireRule(ruleId);

        AlertEventDTO testEvent = AlertEventDTO.builder()
                .ruleId(rule.getId())
                .ruleName("[TEST] " + rule.getName())
                .metricType(rule.getMetricType())
                .currentValue(rule.getThreshold() * 1.1)   // 10% above threshold
                .threshold(rule.getThreshold())
                .operator(rule.getOperator())
                .severity("WARNING")
                .message("[TEST] This is a test notification from ServerWatch. "
                        + "Your alert channel is configured correctly.")
                .triggeredAt(Instant.now())
                .notified(false)
                .notificationChannels(List.of())
                .build();

        int sent = 0;
        for (Notifier notifier : notifiers) {
            boolean shouldNotify =
                    ("email".equals(notifier.getType())   && rule.isNotifyEmail())   ||
                    ("webhook".equals(notifier.getType()) && rule.isNotifyWebhook());
            if (shouldNotify) {
                try {
                    notifier.send(testEvent, rule);
                    sent++;
                } catch (Exception e) {
                    log.error("Test notification via '{}' failed for rule '{}': {}",
                            notifier.getType(), rule.getName(), e.getMessage());
                }
            }
        }
        log.info("Test notification sent via {} channel(s) for rule '{}'", sent, rule.getName());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validate(AlertRuleDTO dto) {
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (!VALID_METRIC_TYPES.contains(dto.metricType())) {
            throw new IllegalArgumentException(
                    "Invalid metricType '" + dto.metricType() + "'. Allowed: " + VALID_METRIC_TYPES);
        }
        if (!VALID_OPERATORS.contains(dto.operator())) {
            throw new IllegalArgumentException(
                    "Invalid operator '" + dto.operator() + "'. Allowed: " + VALID_OPERATORS);
        }
        if (dto.threshold() < 0) {
            throw new IllegalArgumentException("threshold must be non-negative");
        }
        if (dto.cooldownMinutes() < 1) {
            throw new IllegalArgumentException("cooldownMinutes must be >= 1");
        }
        if (dto.notifyEmail() && (dto.emailRecipients() == null || dto.emailRecipients().isBlank())) {
            throw new IllegalArgumentException("emailRecipients is required when notifyEmail is true");
        }
        if (dto.notifyWebhook() && (dto.webhookUrl() == null || dto.webhookUrl().isBlank())) {
            throw new IllegalArgumentException("webhookUrl is required when notifyWebhook is true");
        }
        if (dto.notifyEmail() && dto.emailRecipients() != null) {
            for (String addr : dto.emailRecipients().split(",")) {
                if (!addr.trim().contains("@")) {
                    throw new IllegalArgumentException(
                            "Invalid email address in emailRecipients: " + addr.trim());
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AlertRule requireRule(Long id) {
        return ruleRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found: " + id));
    }

    private AlertEventDTO toEventDTO(AlertHistory h) {
        List<String> channels = h.getNotificationChannels() != null
                && !h.getNotificationChannels().isBlank()
                ? Arrays.asList(h.getNotificationChannels().split(","))
                : List.of();

        return AlertEventDTO.builder()
                .id(h.getId())
                .ruleId(h.getRuleId())
                .metricType(h.getMetricType())
                .currentValue(h.getMetricValue())
                .threshold(h.getThreshold())
                .operator(h.getOperator())
                .message(h.getMessage())
                .severity(h.getSeverity())
                .notified(h.isNotified())
                .notificationChannels(channels)
                .triggeredAt(h.getCreatedAt())
                .build();
    }
}
