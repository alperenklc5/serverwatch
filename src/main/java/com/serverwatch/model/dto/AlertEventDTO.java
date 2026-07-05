package com.serverwatch.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Represents a single fired alert event.
 * Mutable so that notification results can be written back before persisting
 * or pushing to the WebSocket topic.
 *
 * <p>Sent to the {@code /topic/alerts} WebSocket topic and stored in
 * {@code alert_history}.
 */
public class AlertEventDTO {

    private Long         id;
    private Long         ruleId;
    private String       ruleName;
    private String       metricType;
    private double       currentValue;
    private double       threshold;
    private String       operator;
    private String       message;
    private String       severity;
    private boolean      notified;
    private List<String> notificationChannels;
    private Instant      triggeredAt;

    public AlertEventDTO() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long         getId()                   { return id; }
    public Long         getRuleId()               { return ruleId; }
    public String       getRuleName()             { return ruleName; }
    public String       getMetricType()           { return metricType; }
    public double       getCurrentValue()         { return currentValue; }
    public double       getThreshold()            { return threshold; }
    public String       getOperator()             { return operator; }
    public String       getMessage()              { return message; }
    public String       getSeverity()             { return severity; }
    public boolean      isNotified()              { return notified; }
    public List<String> getNotificationChannels() { return notificationChannels; }
    public Instant      getTriggeredAt()          { return triggeredAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(Long id)                                       { this.id = id; }
    public void setRuleId(Long ruleId)                               { this.ruleId = ruleId; }
    public void setRuleName(String ruleName)                         { this.ruleName = ruleName; }
    public void setMetricType(String metricType)                     { this.metricType = metricType; }
    public void setCurrentValue(double currentValue)                 { this.currentValue = currentValue; }
    public void setThreshold(double threshold)                       { this.threshold = threshold; }
    public void setOperator(String operator)                         { this.operator = operator; }
    public void setMessage(String message)                           { this.message = message; }
    public void setSeverity(String severity)                         { this.severity = severity; }
    public void setNotified(boolean notified)                        { this.notified = notified; }
    public void setNotificationChannels(List<String> channels)       { this.notificationChannels = channels; }
    public void setTriggeredAt(Instant triggeredAt)                  { this.triggeredAt = triggeredAt; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final AlertEventDTO dto = new AlertEventDTO();

        public Builder id(Long v)                     { dto.id = v;                    return this; }
        public Builder ruleId(Long v)                 { dto.ruleId = v;                return this; }
        public Builder ruleName(String v)             { dto.ruleName = v;              return this; }
        public Builder metricType(String v)           { dto.metricType = v;            return this; }
        public Builder currentValue(double v)         { dto.currentValue = v;          return this; }
        public Builder threshold(double v)            { dto.threshold = v;             return this; }
        public Builder operator(String v)             { dto.operator = v;              return this; }
        public Builder message(String v)              { dto.message = v;               return this; }
        public Builder severity(String v)             { dto.severity = v;              return this; }
        public Builder notified(boolean v)            { dto.notified = v;              return this; }
        public Builder notificationChannels(List<String> v) { dto.notificationChannels = v; return this; }
        public Builder triggeredAt(Instant v)         { dto.triggeredAt = v;           return this; }

        public AlertEventDTO build() { return dto; }
    }
}
