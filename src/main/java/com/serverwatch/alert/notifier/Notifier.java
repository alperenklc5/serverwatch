package com.serverwatch.alert.notifier;

import com.serverwatch.model.dto.AlertEventDTO;
import com.serverwatch.model.entity.AlertRule;

/**
 * Strategy interface for alert notification channels.
 *
 * <p>Implementations must be Spring beans so they are discovered and collected
 * by {@link com.serverwatch.alert.AlertEngine}.
 * Implementations must not throw — log failures and return gracefully.
 */
public interface Notifier {

    /**
     * Sends a notification for the given alert event via this channel.
     *
     * @param alert the triggered alert event
     * @param rule  the rule that produced the event (provides channel config)
     */
    void send(AlertEventDTO alert, AlertRule rule);

    /**
     * Returns the channel identifier for this notifier.
     * Returned in {@link AlertEventDTO#getNotificationChannels()}.
     *
     * @return {@code "email"} or {@code "webhook"}
     */
    String getType();
}
