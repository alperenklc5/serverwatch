package com.serverwatch.alert;

import org.springframework.stereotype.Component;

/**
 * Pure-function component for alert rule evaluation logic.
 * No I/O, no state — everything is deterministic and easily unit-testable.
 */
@Component
public class AlertEvaluator {

    /**
     * Returns {@code true} if {@code currentValue} satisfies the given
     * operator/threshold predicate.
     *
     * @param currentValue the live metric reading
     * @param operator     one of: GT, GTE, LT, LTE, EQ
     * @param threshold    the configured trigger level
     * @throws IllegalArgumentException if the operator is unknown
     */
    public boolean evaluate(double currentValue, String operator, double threshold) {
        return switch (operator) {
            case "GT"  -> currentValue >  threshold;
            case "GTE" -> currentValue >= threshold;
            case "LT"  -> currentValue <  threshold;
            case "LTE" -> currentValue <= threshold;
            case "EQ"  -> Math.abs(currentValue - threshold) < 0.01;
            default    -> throw new IllegalArgumentException("Unknown operator: " + operator);
        };
    }

    /**
     * Classifies alert severity based on how far the value exceeds the threshold.
     * Returns {@code "CRITICAL"} when the excess is ≥20% of the threshold value,
     * {@code "WARNING"} otherwise.
     *
     * <p>Examples (operator GT, threshold 80):
     * <ul>
     *   <li>value 96 → excess = (96-80)/80 = 20% → CRITICAL</li>
     *   <li>value 85 → excess = (85-80)/80 = 6%  → WARNING</li>
     * </ul>
     *
     * @param currentValue the live metric reading
     * @param threshold    the rule's threshold
     * @return {@code "CRITICAL"} or {@code "WARNING"}
     */
    public String determineSeverity(double currentValue, double threshold) {
        if (threshold == 0) return "CRITICAL";
        double excess = Math.abs(currentValue - threshold) / Math.abs(threshold);
        return excess >= 0.20 ? "CRITICAL" : "WARNING";
    }

    /**
     * Builds a human-readable alert message.
     *
     * <p>Example: {@code "cpu usage is 92.3% (threshold: > 80.0%)"}
     *
     * @param metricType   e.g. "CPU_USAGE"
     * @param currentValue the live reading
     * @param operator     GT, GTE, etc.
     * @param threshold    the configured level
     * @return formatted message string
     */
    public String buildMessage(String metricType, double currentValue,
                               String operator, double threshold) {
        String opSymbol = switch (operator) {
            case "GT"  -> ">";
            case "GTE" -> ">=";
            case "LT"  -> "<";
            case "LTE" -> "<=";
            case "EQ"  -> "=";
            default    -> "?";
        };
        String label = metricType.replace("_", " ").toLowerCase();
        return String.format("%s is %.1f%% (threshold: %s %.1f%%)",
                label, currentValue, opSymbol, threshold);
    }
}
