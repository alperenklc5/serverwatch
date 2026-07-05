package com.serverwatch.model.dto;

import java.time.Instant;

/**
 * Data-transfer object for an alert rule.
 * Used for both request bodies (id=null for creation) and response payloads.
 */
public record AlertRuleDTO(
        Long    id,
        String  name,
        String  metricType,
        String  operator,
        double  threshold,
        String  containerName,
        String  networkInterface,
        int     cooldownMinutes,
        boolean notifyEmail,
        boolean notifyWebhook,
        String  webhookUrl,
        String  emailRecipients,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}
