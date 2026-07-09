package com.serverwatch.controller;

import com.serverwatch.model.dto.ApiResponse;
import com.serverwatch.model.dto.PermissionDTO;
import com.serverwatch.model.entity.User;
import com.serverwatch.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Permission management endpoints.
 *
 * <pre>
 * GET  /api/users/{id}/permissions — ADMIN only
 * PUT  /api/users/{id}/permissions — ADMIN only
 * </pre>
 */
@RestController
@RequestMapping("/api/users")
public class PermissionController {

    private final AuthService authService;

    public PermissionController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<List<PermissionDTO>>> getUserPermissions(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(authService.getUserPermissions(id)));
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<Void>> setUserPermissions(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser,
            @RequestBody Map<String, List<String>> body) {
        authService.setUserPermissions(id, body.getOrDefault("permissions", List.of()), currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
