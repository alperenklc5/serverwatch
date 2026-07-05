# Phase 6 — Alert Engine

## Objective
Build a rule-based alert engine that evaluates system metrics against user-defined thresholds and sends notifications via email (SMTP) and webhooks (Discord, Slack, generic). Alerts are pushed to the frontend in real-time via WebSocket and stored in the database for history.

## Prerequisites
- Phase 3 completed — WebSocket streaming operational
- Phase 2 completed — MetricService collects and caches metrics
- Database schema includes `alert_rules` and `alert_history` tables (Phase 1 migration)
- Spring Mail configured in application.yml (Phase 1)

## Step 1: DTOs

### AlertRuleDTO.java
```java
// - id (Long) — null for creation
// - name (String, required) — e.g., "High RAM Warning"
// - metricType (String, required) — enum: CPU_USAGE, MEMORY_USAGE, DISK_USAGE,
//   SWAP_USAGE, CONTAINER_CPU, CONTAINER_MEMORY, NETWORK_RX_RATE, NETWORK_TX_RATE
// - operator (String, required) — "GT" (>), "GTE" (>=), "LT" (<), "LTE" (<=), "EQ" (==)
// - threshold (double, required) — the trigger value
// - containerName (String, optional) — for container-specific metrics
// - networkInterface (String, optional) — for network-specific metrics
// - cooldownMinutes (int, default 5) — minimum time between repeated alerts
// - notifyEmail (boolean, default false)
// - notifyWebhook (boolean, default false)
// - webhookUrl (String, optional) — Discord/Slack/generic webhook URL
// - emailRecipients (String, optional) — comma-separated emails
// - enabled (boolean, default true)
// - createdAt (Instant)
// - updatedAt (Instant)
```

### AlertEventDTO.java (sent via WebSocket and stored in history)
```java
// - id (Long)
// - ruleId (Long)
// - ruleName (String)
// - metricType (String)
// - currentValue (double) — the value that triggered the alert
// - threshold (double) — the rule's threshold
// - operator (String)
// - message (String) — human-readable, e.g., "CPU usage is 92.3% (threshold: 80%)"
// - severity (String) — "WARNING", "CRITICAL" (based on how far over threshold)
// - notified (boolean) — whether notification was successfully sent
// - notificationChannels (List<String>) — ["email", "webhook"]
// - triggeredAt (Instant)
```

## Step 2: Entity & Repository

### AlertRule.java (JPA Entity)
Map to the `alert_rules` table created in Phase 1. Use `@EntityListeners(AuditingEntityListener.class)` for auto-updating `updatedAt`.

### AlertHistory.java (JPA Entity)
Map to the `alert_history` table.

### AlertRuleRepository.java
```java
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabledTrue();
    List<AlertRule> findByMetricType(String metricType);
}
```

### AlertHistoryRepository.java
```java
@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {
    List<AlertHistory> findByRuleIdOrderByCreatedAtDesc(Long ruleId, Pageable pageable);

    List<AlertHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(
        Instant from, Instant to
    );

    Optional<AlertHistory> findTopByRuleIdOrderByCreatedAtDesc(Long ruleId);

    @Modifying
    @Query("DELETE FROM AlertHistory h WHERE h.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
```

## Step 3: Notifiers

### Notifier.java (interface)
```java
public interface Notifier {
    void send(AlertEventDTO alert, AlertRule rule);
    String getType(); // "email" or "webhook"
}
```

### EmailNotifier.java
```java
@Component
public class EmailNotifier implements Notifier {

    private final JavaMailSender mailSender;
    private final ServerWatchProperties properties;

    @Override
    public void send(AlertEventDTO alert, AlertRule rule) {
        // Build a MimeMessage:
        // Subject: "[ServerWatch Alert] {ruleName} - {severity}"
        // Body (HTML):
        //   - Alert name, metric type, current value vs threshold
        //   - Timestamp
        //   - Server hostname
        //   - Link to dashboard (if configured)
        //
        // Send to all recipients in rule.emailRecipients (comma-separated)
        //
        // Wrap in try-catch — log failure but don't crash the alert pipeline
    }

    @Override
    public String getType() { return "email"; }
}
```

### WebhookNotifier.java
```java
@Component
public class WebhookNotifier implements Notifier {

    private final RestTemplate restTemplate; // or WebClient

    @Override
    public void send(AlertEventDTO alert, AlertRule rule) {
        String url = rule.getWebhookUrl();

        if (isDiscordWebhook(url)) {
            sendDiscord(alert, url);
        } else if (isSlackWebhook(url)) {
            sendSlack(alert, url);
        } else {
            sendGeneric(alert, url);
        }
    }

    private void sendDiscord(AlertEventDTO alert, String url) {
        // Discord expects: {"content": "message", "embeds": [{...}]}
        // Build an embed with:
        //   - title: alert.ruleName
        //   - description: alert.message
        //   - color: red (0xFF0000) for CRITICAL, orange (0xFF8C00) for WARNING
        //   - fields: metric type, value, threshold, timestamp
        //   - footer: "ServerWatch Alert System"
        //
        // POST to the webhook URL
    }

    private void sendSlack(AlertEventDTO alert, String url) {
        // Slack expects: {"text": "fallback", "attachments": [{...}]}
        // Build an attachment with:
        //   - color: "#FF0000" for CRITICAL, "#FF8C00" for WARNING
        //   - title: alert.ruleName
        //   - text: alert.message
        //   - fields: [{title: "Metric", value: ...}, {title: "Value", value: ...}]
        //
        // POST to the webhook URL
    }

    private void sendGeneric(AlertEventDTO alert, String url) {
        // POST the raw AlertEventDTO as JSON body
        // Include headers: Content-Type: application/json
        // Include custom header: X-ServerWatch-Alert: true
    }

    private boolean isDiscordWebhook(String url) {
        return url != null && url.contains("discord.com/api/webhooks");
    }

    private boolean isSlackWebhook(String url) {
        return url != null && url.contains("hooks.slack.com");
    }

    @Override
    public String getType() { return "webhook"; }
}
```

## Step 4: Alert Engine

### AlertEvaluator.java
```java
@Component
public class AlertEvaluator {

    public boolean evaluate(double currentValue, String operator, double threshold) {
        return switch (operator) {
            case "GT"  -> currentValue > threshold;
            case "GTE" -> currentValue >= threshold;
            case "LT"  -> currentValue < threshold;
            case "LTE" -> currentValue <= threshold;
            case "EQ"  -> Math.abs(currentValue - threshold) < 0.01;
            default -> throw new IllegalArgumentException("Unknown operator: " + operator);
        };
    }

    public String determineSeverity(double currentValue, String operator, double threshold) {
        // CRITICAL if the value exceeds threshold by 20% or more
        // Example: threshold = 80%, value = 96% → CRITICAL
        //          threshold = 80%, value = 85% → WARNING
        double excess = Math.abs(currentValue - threshold) / threshold;
        return excess > 0.20 ? "CRITICAL" : "WARNING";
    }

    public String buildMessage(String metricType, double currentValue,
                                String operator, double threshold) {
        // "CPU usage is 92.3% (threshold: > 80%)"
        // "Memory usage is 87.1% (threshold: > 80%)"
        // "Container 'nginx' CPU is 95.2% (threshold: > 90%)"
        String opSymbol = switch (operator) {
            case "GT" -> ">"; case "GTE" -> ">=";
            case "LT" -> "<"; case "LTE" -> "<=";
            case "EQ" -> "="; default -> "?";
        };
        return String.format("%s is %.1f%% (threshold: %s %.1f%%)",
            metricType.replace("_", " ").toLowerCase(),
            currentValue, opSymbol, threshold);
    }
}
```

### AlertEngine.java
```java
@Service
public class AlertEngine {

    private final AlertRuleRepository ruleRepo;
    private final AlertHistoryRepository historyRepo;
    private final AlertEvaluator evaluator;
    private final List<Notifier> notifiers; // Spring injects all Notifier implementations
    private final MetricService metricService;
    private final DockerService dockerService; // for container metrics
    private final WebSocketPublisher wsPublisher;

    // Cooldown tracking: ruleId → last alert time
    private final Map<Long, Instant> lastAlertTimes = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${serverwatch.alert.evaluation-interval-ms}")
    public void evaluateRules() {
        List<AlertRule> enabledRules = ruleRepo.findByEnabledTrue();

        for (AlertRule rule : enabledRules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.error("Error evaluating alert rule '{}': {}", rule.getName(), e.getMessage());
            }
        }
    }

    private void evaluateRule(AlertRule rule) {
        // 1. Check cooldown — skip if last alert was within cooldownMinutes
        Instant lastAlert = lastAlertTimes.get(rule.getId());
        if (lastAlert != null && lastAlert.plus(rule.getCooldownMinutes(), ChronoUnit.MINUTES).isAfter(Instant.now())) {
            return; // still in cooldown
        }

        // 2. Get current metric value based on metricType
        double currentValue = getCurrentMetricValue(rule);

        // 3. Evaluate the rule
        if (!evaluator.evaluate(currentValue, rule.getOperator(), rule.getThreshold())) {
            return; // rule not triggered
        }

        // 4. Build alert event
        AlertEventDTO event = AlertEventDTO.builder()
            .ruleId(rule.getId())
            .ruleName(rule.getName())
            .metricType(rule.getMetricType())
            .currentValue(currentValue)
            .threshold(rule.getThreshold())
            .operator(rule.getOperator())
            .severity(evaluator.determineSeverity(currentValue, rule.getOperator(), rule.getThreshold()))
            .message(evaluator.buildMessage(rule.getMetricType(), currentValue, rule.getOperator(), rule.getThreshold()))
            .triggeredAt(Instant.now())
            .build();

        // 5. Save to history
        AlertHistory history = mapToEntity(event, rule);
        historyRepo.save(history);

        // 6. Send notifications (async to not block the evaluation loop)
        sendNotifications(event, rule);

        // 7. Push to WebSocket for real-time dashboard alerts
        wsPublisher.publishAlert(event);

        // 8. Update cooldown tracker
        lastAlertTimes.put(rule.getId(), Instant.now());

        log.warn("Alert triggered: {} — {}", rule.getName(), event.getMessage());
    }

    private double getCurrentMetricValue(AlertRule rule) {
        SystemMetricDTO metrics = metricService.getLatestSystemMetric();
        if (metrics == null) return 0.0;

        return switch (rule.getMetricType()) {
            case "CPU_USAGE" -> metrics.getCpuUsagePercent();
            case "MEMORY_USAGE" -> metrics.getMemoryUsagePercent();
            case "SWAP_USAGE" -> {
                if (metrics.getSwapTotalBytes() == 0) yield 0.0;
                yield (metrics.getSwapUsedBytes() * 100.0) / metrics.getSwapTotalBytes();
            }
            case "DISK_USAGE" -> metrics.getDiskInfos().stream()
                .mapToDouble(d -> d.getUsagePercent())
                .max().orElse(0.0);
            case "CONTAINER_CPU" -> getContainerMetric(rule.getContainerName(), "cpu");
            case "CONTAINER_MEMORY" -> getContainerMetric(rule.getContainerName(), "memory");
            default -> throw new IllegalArgumentException("Unknown metric type: " + rule.getMetricType());
        };
    }

    private double getContainerMetric(String containerName, String metric) {
        // Get stats for the specific container from DockerService
        // Return cpuPercent or memoryPercent
        // Return 0.0 if container not found or not running
    }

    @Async
    protected void sendNotifications(AlertEventDTO event, AlertRule rule) {
        List<String> channels = new ArrayList<>();

        for (Notifier notifier : notifiers) {
            boolean shouldNotify =
                (notifier.getType().equals("email") && rule.isNotifyEmail()) ||
                (notifier.getType().equals("webhook") && rule.isNotifyWebhook());

            if (shouldNotify) {
                try {
                    notifier.send(event, rule);
                    channels.add(notifier.getType());
                } catch (Exception e) {
                    log.error("Failed to send {} notification for rule '{}': {}",
                        notifier.getType(), rule.getName(), e.getMessage());
                }
            }
        }

        event.setNotificationChannels(channels);
        event.setNotified(!channels.isEmpty());
    }

    // Cleanup old history
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    public void cleanupHistory() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = historyRepo.deleteOlderThan(cutoff);
        log.info("Cleaned up {} old alert history records", deleted);
    }
}
```

## Step 5: AlertService (CRUD for rules)

### AlertService.java
```java
@Service
public class AlertService {

    private final AlertRuleRepository ruleRepo;
    private final AlertHistoryRepository historyRepo;

    public List<AlertRuleDTO> getAllRules() { ... }
    public AlertRuleDTO getRule(Long id) { ... }

    public AlertRuleDTO createRule(AlertRuleDTO dto) {
        // Validate:
        // - metricType is valid enum value
        // - operator is valid
        // - threshold is positive
        // - if notifyEmail, emailRecipients must not be blank
        // - if notifyWebhook, webhookUrl must be a valid URL
        // - cooldownMinutes >= 1
        //
        // Map to entity, save, return DTO
    }

    public AlertRuleDTO updateRule(Long id, AlertRuleDTO dto) { ... }

    public void deleteRule(Long id) {
        // Also deletes history via CASCADE
    }

    public void toggleRule(Long id, boolean enabled) {
        AlertRule rule = ruleRepo.findById(id).orElseThrow();
        rule.setEnabled(enabled);
        ruleRepo.save(rule);
    }

    public List<AlertEventDTO> getAlertHistory(Long ruleId, int limit) {
        return historyRepo.findByRuleIdOrderByCreatedAtDesc(
            ruleId, PageRequest.of(0, limit)
        ).stream().map(this::toDTO).toList();
    }

    public List<AlertEventDTO> getRecentAlerts(int hours) {
        Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
        return historyRepo.findByCreatedAtBetweenOrderByCreatedAtDesc(from, Instant.now())
            .stream().map(this::toDTO).toList();
    }

    // Test a notification channel
    public void testNotification(Long ruleId) {
        // Create a fake AlertEventDTO with test values
        // Send via all configured channels for this rule
        // Useful for verifying email/webhook setup
    }
}
```

## Step 6: REST Controller

### AlertController.java
```
# Rule CRUD
GET    /api/alerts/rules                   → List<AlertRuleDTO>
GET    /api/alerts/rules/{id}              → AlertRuleDTO
POST   /api/alerts/rules                   → AlertRuleDTO (body: AlertRuleDTO)
PUT    /api/alerts/rules/{id}              → AlertRuleDTO (body: AlertRuleDTO)
DELETE /api/alerts/rules/{id}              → 204 No Content
PATCH  /api/alerts/rules/{id}/toggle       → 200 OK (body: {"enabled": true/false})

# Alert History
GET    /api/alerts/history                 → List<AlertEventDTO>
       Query: hours=24 (default), limit=100
GET    /api/alerts/history/rule/{ruleId}   → List<AlertEventDTO>
       Query: limit=50

# Testing
POST   /api/alerts/rules/{id}/test         → 200 OK (sends test notification)
```

## Step 7: Enable Async

Add `@EnableAsync` to the main application class or create an `AsyncConfig.java`:
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor alertNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("alert-notify-");
        executor.initialize();
        return executor;
    }
}
```

## Acceptance Criteria
- [ ] `POST /api/alerts/rules` creates a new alert rule and persists to DB
- [ ] `GET /api/alerts/rules` lists all rules with current enabled/disabled status
- [ ] Alert engine evaluates rules every 5 seconds (configurable)
- [ ] When CPU > 80% (example rule), an alert is triggered and stored in alert_history
- [ ] Triggered alert appears on WebSocket `/topic/alerts` in real-time
- [ ] Email notification is sent when `notifyEmail=true` and threshold is exceeded
- [ ] Discord webhook receives a formatted embed when `notifyWebhook=true`
- [ ] Slack webhook receives a formatted message when URL contains `hooks.slack.com`
- [ ] Cooldown works: same rule does not trigger again within cooldownMinutes
- [ ] `POST /api/alerts/rules/{id}/test` sends a test notification to configured channels
- [ ] `GET /api/alerts/history?hours=24` returns all alerts from the last 24 hours
- [ ] `PATCH /api/alerts/rules/{id}/toggle` enables/disables a rule
- [ ] Notification failures are logged but don't crash the alert engine
- [ ] Old history records (>30 days) are auto-cleaned

## Files to Create/Modify
```
CREATE:
src/main/java/com/serverwatch/
├── model/dto/
│   ├── AlertRuleDTO.java
│   └── AlertEventDTO.java
├── model/entity/
│   ├── AlertRule.java
│   └── AlertHistory.java
├── repository/
│   ├── AlertRuleRepository.java
│   └── AlertHistoryRepository.java
├── service/
│   └── AlertService.java
├── alert/
│   ├── AlertEngine.java
│   ├── AlertEvaluator.java
│   └── notifier/
│       ├── Notifier.java
│       ├── EmailNotifier.java
│       └── WebhookNotifier.java
├── config/
│   └── AsyncConfig.java
└── controller/
    └── AlertController.java

MODIFY:
├── ServerWatchApplication.java — add @EnableAsync
└── service/WebSocketPublisher.java — ensure publishAlert method exists
```
