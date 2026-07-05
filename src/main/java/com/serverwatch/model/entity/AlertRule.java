package com.serverwatch.model.entity;

import com.serverwatch.model.dto.AlertRuleDTO;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for the {@code alert_rules} table.
 *
 * <p>{@link #updatedAt} is refreshed automatically on every write via
 * {@link #onUpdate()}.
 */
@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType;

    @Column(nullable = false, length = 10)
    private String operator;

    @Column(nullable = false)
    private double threshold;

    @Column(name = "container_name")
    private String containerName;

    @Column(name = "network_interface")
    private String networkInterface;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes = 5;

    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail = false;

    @Column(name = "notify_webhook", nullable = false)
    private boolean notifyWebhook = false;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "email_recipients", length = 1000)
    private String emailRecipients;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Creates a new {@link AlertRule} from a DTO (used for creation requests).
     */
    public static AlertRule fromDto(AlertRuleDTO dto) {
        AlertRule rule = new AlertRule();
        rule.name             = dto.name();
        rule.metricType       = dto.metricType();
        rule.operator         = dto.operator();
        rule.threshold        = dto.threshold();
        rule.containerName    = dto.containerName();
        rule.networkInterface = dto.networkInterface();
        rule.cooldownMinutes  = dto.cooldownMinutes() > 0 ? dto.cooldownMinutes() : 5;
        rule.notifyEmail      = dto.notifyEmail();
        rule.notifyWebhook    = dto.notifyWebhook();
        rule.webhookUrl       = dto.webhookUrl();
        rule.emailRecipients  = dto.emailRecipients();
        rule.enabled          = dto.enabled();
        return rule;
    }

    /**
     * Applies all mutable fields from a DTO onto this existing entity (used for updates).
     */
    public void applyDto(AlertRuleDTO dto) {
        this.name             = dto.name();
        this.metricType       = dto.metricType();
        this.operator         = dto.operator();
        this.threshold        = dto.threshold();
        this.containerName    = dto.containerName();
        this.networkInterface = dto.networkInterface();
        this.cooldownMinutes  = dto.cooldownMinutes() > 0 ? dto.cooldownMinutes() : 5;
        this.notifyEmail      = dto.notifyEmail();
        this.notifyWebhook    = dto.notifyWebhook();
        this.webhookUrl       = dto.webhookUrl();
        this.emailRecipients  = dto.emailRecipients();
        this.enabled          = dto.enabled();
    }

    /** Converts this entity to a DTO for API responses. */
    public AlertRuleDTO toDto() {
        return new AlertRuleDTO(
                id, name, metricType, operator, threshold,
                containerName, networkInterface, cooldownMinutes,
                notifyEmail, notifyWebhook, webhookUrl, emailRecipients,
                enabled, createdAt, updatedAt
        );
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long    getId()               { return id; }
    public String  getName()             { return name; }
    public String  getMetricType()       { return metricType; }
    public String  getOperator()         { return operator; }
    public double  getThreshold()        { return threshold; }
    public String  getContainerName()    { return containerName; }
    public String  getNetworkInterface() { return networkInterface; }
    public int     getCooldownMinutes()  { return cooldownMinutes; }
    public boolean isNotifyEmail()       { return notifyEmail; }
    public boolean isNotifyWebhook()     { return notifyWebhook; }
    public String  getWebhookUrl()       { return webhookUrl; }
    public String  getEmailRecipients()  { return emailRecipients; }
    public boolean isEnabled()           { return enabled; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    public void setEnabled(boolean enabled)   { this.enabled = enabled; }
    public void setName(String name)          { this.name = name; }
}
