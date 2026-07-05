package com.serverwatch.alert.notifier;

import com.serverwatch.model.dto.AlertEventDTO;
import com.serverwatch.model.entity.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Sends formatted alert notifications to Discord, Slack, or generic webhooks.
 *
 * <ul>
 *   <li><b>Discord</b> — URLs containing {@code discord.com/api/webhooks}:
 *       sends an embed with color-coded severity.</li>
 *   <li><b>Slack</b> — URLs containing {@code hooks.slack.com}:
 *       sends an attachment with color and fields.</li>
 *   <li><b>Generic</b> — all other URLs: POSTs the raw {@link AlertEventDTO}
 *       as JSON with the custom header {@code X-ServerWatch-Alert: true}.</li>
 * </ul>
 *
 * <p>Any failure is logged but never re-thrown.
 */
@Component
public class WebhookNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final RestTemplate restTemplate;

    public WebhookNotifier() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void send(AlertEventDTO alert, AlertRule rule) {
        String url = rule.getWebhookUrl();
        if (url == null || url.isBlank()) {
            log.warn("Webhook notifier: rule '{}' has no webhookUrl configured", rule.getName());
            return;
        }

        try {
            if (isDiscordWebhook(url)) {
                sendDiscord(alert, url);
            } else if (isSlackWebhook(url)) {
                sendSlack(alert, url);
            } else {
                sendGeneric(alert, url);
            }
            log.info("Webhook notification sent for rule '{}' to {}", rule.getName(), maskUrl(url));
        } catch (Exception e) {
            log.error("Failed to send webhook for rule '{}': {}", rule.getName(), e.getMessage());
        }
    }

    @Override
    public String getType() {
        return "webhook";
    }

    // ── Discord ───────────────────────────────────────────────────────────────

    private void sendDiscord(AlertEventDTO alert, String url) {
        int color = "CRITICAL".equals(alert.getSeverity()) ? 0xFF0000 : 0xFF8C00;
        String timestamp = alert.getTriggeredAt() != null
                ? ISO_FMT.format(alert.getTriggeredAt()) : null;

        Map<String, Object> field1 = Map.of(
                "name", "Metric Type", "value", alert.getMetricType().replace("_", " "), "inline", true);
        Map<String, Object> field2 = Map.of(
                "name", "Current Value", "value", String.format("%.2f%%", alert.getCurrentValue()), "inline", true);
        Map<String, Object> field3 = Map.of(
                "name", "Threshold", "value",
                String.format("%s %.2f%%", operatorSymbol(alert.getOperator()), alert.getThreshold()),
                "inline", true);

        Map<String, Object> embed = new java.util.LinkedHashMap<>();
        embed.put("title", alert.getRuleName());
        embed.put("description", alert.getMessage());
        embed.put("color", color);
        embed.put("fields", List.of(field1, field2, field3));
        embed.put("footer", Map.of("text", "ServerWatch Alert Engine"));
        if (timestamp != null) embed.put("timestamp", timestamp);

        Map<String, Object> payload = Map.of(
                "content", "**" + alert.getSeverity() + "** alert triggered",
                "embeds", List.of(embed)
        );

        post(url, payload, buildJsonHeaders());
    }

    // ── Slack ─────────────────────────────────────────────────────────────────

    private void sendSlack(AlertEventDTO alert, String url) {
        String color = "CRITICAL".equals(alert.getSeverity()) ? "#FF0000" : "#FF8C00";

        List<Map<String, Object>> fields = List.of(
                Map.of("title", "Metric Type",
                        "value", alert.getMetricType().replace("_", " "), "short", true),
                Map.of("title", "Current Value",
                        "value", String.format("%.2f%%", alert.getCurrentValue()), "short", true),
                Map.of("title", "Threshold",
                        "value", String.format("%s %.2f%%",
                                operatorSymbol(alert.getOperator()), alert.getThreshold()),
                        "short", true),
                Map.of("title", "Severity", "value", alert.getSeverity(), "short", true)
        );

        Map<String, Object> attachment = new java.util.LinkedHashMap<>();
        attachment.put("color", color);
        attachment.put("title", alert.getRuleName());
        attachment.put("text", alert.getMessage());
        attachment.put("fields", fields);
        attachment.put("footer", "ServerWatch Alert Engine");
        if (alert.getTriggeredAt() != null) {
            attachment.put("ts", alert.getTriggeredAt().getEpochSecond());
        }

        Map<String, Object> payload = Map.of(
                "text", "ServerWatch: " + alert.getRuleName() + " (" + alert.getSeverity() + ")",
                "attachments", List.of(attachment)
        );

        post(url, payload, buildJsonHeaders());
    }

    // ── Generic ───────────────────────────────────────────────────────────────

    private void sendGeneric(AlertEventDTO alert, String url) {
        HttpHeaders headers = buildJsonHeaders();
        headers.set("X-ServerWatch-Alert", "true");
        headers.set("X-Alert-Severity", alert.getSeverity());
        post(url, alert, headers);
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    private void post(String url, Object body, HttpHeaders headers) {
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, entity, String.class);
    }

    private static HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static boolean isDiscordWebhook(String url) {
        return url.contains("discord.com/api/webhooks");
    }

    private static boolean isSlackWebhook(String url) {
        return url.contains("hooks.slack.com");
    }

    private static String operatorSymbol(String op) {
        if (op == null) return "?";
        return switch (op) {
            case "GT"  -> ">";
            case "GTE" -> ">=";
            case "LT"  -> "<";
            case "LTE" -> "<=";
            case "EQ"  -> "=";
            default    -> op;
        };
    }

    /** Masks query parameters (e.g. tokens in URL) for safe logging. */
    private static String maskUrl(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) + "?[masked]" : url;
    }
}
