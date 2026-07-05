package com.serverwatch.alert.notifier;

import com.serverwatch.model.dto.AlertEventDTO;
import com.serverwatch.model.entity.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.InetAddress;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Sends HTML alert emails via Spring's {@link JavaMailSender}.
 *
 * <p>Email is only sent when:
 * <ul>
 *   <li>The rule has {@code notifyEmail=true}</li>
 *   <li>{@code emailRecipients} is non-blank</li>
 *   <li>The {@link JavaMailSender} bean is available (SMTP configured)</li>
 * </ul>
 *
 * <p>Any failure is logged but never re-thrown — the alert pipeline continues.
 */
@Component
public class EmailNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;

    public EmailNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(AlertEventDTO alert, AlertRule rule) {
        String recipients = rule.getEmailRecipients();
        if (recipients == null || recipients.isBlank()) {
            log.warn("Email notifier: rule '{}' has no recipients configured", rule.getName());
            return;
        }

        String[] addresses = recipients.split(",");
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setTo(addresses);
            helper.setSubject(buildSubject(alert));
            helper.setText(buildHtmlBody(alert), true);

            mailSender.send(msg);
            log.info("Email alert sent for rule '{}' to {} recipient(s)",
                    rule.getName(), addresses.length);
        } catch (MessagingException e) {
            log.error("Failed to send email for rule '{}': {}", rule.getName(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending email for rule '{}': {}", rule.getName(), e.getMessage());
        }
    }

    @Override
    public String getType() {
        return "email";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String buildSubject(AlertEventDTO alert) {
        return String.format("[ServerWatch Alert] %s — %s", alert.getRuleName(), alert.getSeverity());
    }

    private static String buildHtmlBody(AlertEventDTO alert) {
        String color     = "CRITICAL".equals(alert.getSeverity()) ? "#c0392b" : "#e67e22";
        String timestamp = alert.getTriggeredAt() != null
                ? FORMATTER.format(alert.getTriggeredAt()) : "—";
        String hostname  = resolveHostname();

        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"/></head>
                <body style="font-family:Arial,sans-serif;background:#f5f5f5;padding:20px;">
                  <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;
                              border-top:4px solid %s;padding:24px;">
                    <h2 style="color:%s;margin-top:0;">%s Alert: %s</h2>
                    <p style="font-size:16px;color:#333;">%s</p>
                    <table style="width:100%%;border-collapse:collapse;margin-top:16px;">
                      <tr style="background:#f8f8f8;">
                        <td style="padding:8px;font-weight:bold;width:160px;">Metric Type</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;font-weight:bold;">Current Value</td>
                        <td style="padding:8px;color:%s;font-weight:bold;">%.2f%%</td>
                      </tr>
                      <tr style="background:#f8f8f8;">
                        <td style="padding:8px;font-weight:bold;">Threshold</td>
                        <td style="padding:8px;">%s %.2f%%</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;font-weight:bold;">Severity</td>
                        <td style="padding:8px;color:%s;font-weight:bold;">%s</td>
                      </tr>
                      <tr style="background:#f8f8f8;">
                        <td style="padding:8px;font-weight:bold;">Time (UTC)</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;font-weight:bold;">Server</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                    </table>
                    <p style="margin-top:24px;font-size:12px;color:#999;">
                      Sent by ServerWatch Alert Engine. To manage alert rules, visit your dashboard.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(
                color, color,
                alert.getSeverity(), alert.getRuleName(),
                alert.getMessage(),
                alert.getMetricType().replace("_", " "),
                color, alert.getCurrentValue(),
                operatorSymbol(alert.getOperator()), alert.getThreshold(),
                color, alert.getSeverity(),
                timestamp,
                hostname
        );
    }

    private static String operatorSymbol(String op) {
        if (op == null) return "?";
        return switch (op) {
            case "GT"  -> ">";
            case "GTE" -> "≥";
            case "LT"  -> "<";
            case "LTE" -> "≤";
            case "EQ"  -> "=";
            default    -> op;
        };
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
