package com.serverwatch.model.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for the {@code alert_history} table.
 * Stores a denormalized snapshot of each fired alert event so that history
 * remains queryable even if the originating rule is later deleted.
 */
@Entity
@Table(name = "alert_history")
public class AlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to the triggering rule; nullable because the rule may be deleted. */
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType;

    @Column(name = "metric_value", nullable = false)
    private double metricValue;

    @Column(nullable = false)
    private double threshold;

    @Column(name = "operator", length = 10)
    private String operator;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(length = 20)
    private String severity;

    @Column(nullable = false)
    private boolean notified = false;

    /** Comma-separated channel names, e.g. {@code "email,webhook"}. */
    @Column(name = "notification_channels", length = 500)
    private String notificationChannels;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long    getId()                     { return id; }
    public Long    getRuleId()                 { return ruleId; }
    public String  getMetricType()             { return metricType; }
    public double  getMetricValue()            { return metricValue; }
    public double  getThreshold()              { return threshold; }
    public String  getOperator()               { return operator; }
    public String  getMessage()                { return message; }
    public String  getSeverity()               { return severity; }
    public boolean isNotified()                { return notified; }
    public String  getNotificationChannels()   { return notificationChannels; }
    public Instant getCreatedAt()              { return createdAt; }

    public void setRuleId(Long ruleId)                             { this.ruleId = ruleId; }
    public void setMetricType(String metricType)                   { this.metricType = metricType; }
    public void setMetricValue(double metricValue)                 { this.metricValue = metricValue; }
    public void setThreshold(double threshold)                     { this.threshold = threshold; }
    public void setOperator(String operator)                       { this.operator = operator; }
    public void setMessage(String message)                         { this.message = message; }
    public void setSeverity(String severity)                       { this.severity = severity; }
    public void setNotified(boolean notified)                      { this.notified = notified; }
    public void setNotificationChannels(String channels)           { this.notificationChannels = channels; }
}
