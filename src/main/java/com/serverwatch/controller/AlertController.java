package com.serverwatch.controller;

import com.serverwatch.model.dto.AlertEventDTO;
import com.serverwatch.model.dto.AlertRuleDTO;
import com.serverwatch.model.dto.ApiResponse;
import com.serverwatch.service.AlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for alert rule management and history retrieval.
 *
 * <pre>
 * Rule CRUD:
 *   GET    /api/alerts/rules                    → List&lt;AlertRuleDTO&gt;
 *   GET    /api/alerts/rules/{id}               → AlertRuleDTO
 *   POST   /api/alerts/rules                    → AlertRuleDTO (201 Created)
 *   PUT    /api/alerts/rules/{id}               → AlertRuleDTO
 *   DELETE /api/alerts/rules/{id}               → 204 No Content
 *   PATCH  /api/alerts/rules/{id}/toggle        → AlertRuleDTO
 *
 * Alert History:
 *   GET    /api/alerts/history                  → List&lt;AlertEventDTO&gt;
 *          Query: hours=24 (default), limit=100
 *   GET    /api/alerts/history/rule/{ruleId}    → List&lt;AlertEventDTO&gt;
 *          Query: limit=50
 *
 * Testing:
 *   POST   /api/alerts/rules/{id}/test          → 200 OK
 * </pre>
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // ── Rule CRUD ─────────────────────────────────────────────────────────────

    /**
     * Returns all alert rules.
     */
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<AlertRuleDTO>>> getAllRules() {
        return ResponseEntity.ok(ApiResponse.ok(alertService.getAllRules()));
    }

    /**
     * Returns a single alert rule by ID.
     */
    @GetMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<AlertRuleDTO>> getRule(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.getRule(id)));
    }

    /**
     * Creates a new alert rule.
     * Returns {@code 201 Created} with the persisted rule.
     */
    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<AlertRuleDTO>> createRule(
            @RequestBody AlertRuleDTO dto) {
        AlertRuleDTO created = alertService.createRule(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /**
     * Replaces all mutable fields of an existing rule.
     */
    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<AlertRuleDTO>> updateRule(
            @PathVariable Long id,
            @RequestBody AlertRuleDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.updateRule(id, dto)));
    }

    /**
     * Deletes an alert rule and all its history.
     * Returns {@code 204 No Content}.
     */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Enables or disables an alert rule.
     *
     * <p>Request body: {@code {"enabled": true}} or {@code {"enabled": false}}.
     */
    @PatchMapping("/rules/{id}/toggle")
    public ResponseEntity<ApiResponse<AlertRuleDTO>> toggleRule(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {

        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Request body must contain 'enabled' boolean"));
        }
        return ResponseEntity.ok(ApiResponse.ok(alertService.toggleRule(id, enabled)));
    }

    // ── Alert History ─────────────────────────────────────────────────────────

    /**
     * Returns recent alerts across all rules.
     *
     * @param hours look-back window in hours (default 24, max 168)
     * @param limit max results to return (default 100, max 500)
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<AlertEventDTO>>> getHistory(
            @RequestParam(defaultValue = "24")  int hours,
            @RequestParam(defaultValue = "100") int limit) {

        return ResponseEntity.ok(ApiResponse.ok(alertService.getRecentAlerts(hours, limit)));
    }

    /**
     * Returns alert history for a specific rule.
     *
     * @param ruleId the rule ID
     * @param limit  max results to return (default 50, max 200)
     */
    @GetMapping("/history/rule/{ruleId}")
    public ResponseEntity<ApiResponse<List<AlertEventDTO>>> getRuleHistory(
            @PathVariable Long ruleId,
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(ApiResponse.ok(alertService.getAlertHistory(ruleId, limit)));
    }

    // ── Test Notifications ────────────────────────────────────────────────────

    /**
     * Sends a test notification to all channels configured for the given rule.
     * Does not store history and does not affect cooldown.
     */
    @PostMapping("/rules/{id}/test")
    public ResponseEntity<ApiResponse<String>> testNotification(@PathVariable Long id) {
        alertService.testNotification(id);
        return ResponseEntity.ok(ApiResponse.ok("Test notification dispatched"));
    }
}
