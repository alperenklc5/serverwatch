package com.serverwatch.security;

import com.serverwatch.model.entity.Permission;
import com.serverwatch.model.entity.User;
import com.serverwatch.service.PermissionService;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Factory for per-permission {@link AuthorizationManager} instances used in
 * {@code SecurityConfig.authorizeHttpRequests}.
 *
 * <p>Each call to {@link #requiring(Permission)} returns a lightweight lambda
 * that looks up the caller's permissions from the cache-backed {@link PermissionService}.
 * This replaces the previous {@code hasRole("ADMIN")} checks which were not being
 * enforced due to Spring Security 6 MvcRequestMatcher resolution issues at startup.
 */
@Component
public class PermissionAuthorizationManager {

    private final PermissionService permissionService;

    public PermissionAuthorizationManager(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public AuthorizationManager<RequestAuthorizationContext> requiring(Permission permission) {
        return (authSupplier, context) -> {
            Authentication auth = authSupplier.get();
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(auth.getPrincipal())) {
                return new AuthorizationDecision(false);
            }
            if (!(auth.getPrincipal() instanceof User user)) {
                return new AuthorizationDecision(false);
            }
            boolean granted = permissionService.hasPermission(user.getId(), permission);
            return new AuthorizationDecision(granted);
        };
    }
}
