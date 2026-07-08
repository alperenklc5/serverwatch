package com.serverwatch.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.serverwatch.model.entity.Permission;
import com.serverwatch.model.entity.UserPermission;
import com.serverwatch.repository.UserPermissionRepository;
import com.serverwatch.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PermissionService {

    private static final Set<Permission> DEFAULT_PERMISSIONS = EnumSet.of(
            Permission.FILES_VIEW, Permission.DOCKER_VIEW,
            Permission.GIT_VIEW, Permission.ALERTS_VIEW);

    private final UserPermissionRepository permissionRepo;
    private final UserRepository userRepo;

    // Caffeine cache: userId → Set<Permission>, 60-second TTL
    private final Cache<Long, Set<Permission>> cache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();

    public PermissionService(UserPermissionRepository permissionRepo, UserRepository userRepo) {
        this.permissionRepo = permissionRepo;
        this.userRepo = userRepo;
    }

    public Set<Permission> getPermissions(Long userId) {
        return cache.get(userId, id -> {
            Set<Permission> perms = permissionRepo.findPermissionsByUserId(id);
            return perms.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(perms);
        });
    }

    public boolean hasPermission(Long userId, Permission permission) {
        return getPermissions(userId).contains(permission);
    }

    @Transactional
    public void grantPermission(Long userId, Permission permission, Long grantedBy) {
        if (!permissionRepo.existsByUserIdAndPermission(userId, permission)) {
            UserPermission up = new UserPermission();
            up.setUserId(userId);
            up.setPermission(permission);
            up.setGrantedBy(grantedBy);
            permissionRepo.save(up);
        }
        invalidateCache(userId);
    }

    @Transactional
    public void revokePermission(Long userId, Permission permission) {
        // Guard: never revoke from the seed admin account
        userRepo.findById(userId).ifPresent(user -> {
            if ("admin".equals(user.getUsername())) {
                throw new IllegalStateException("Cannot revoke permissions from the built-in admin account");
            }
        });

        // Guard: prevent revoking USER_MANAGEMENT from the last holder
        if (permission == Permission.USER_MANAGEMENT) {
            long holders = permissionRepo.countByPermission(Permission.USER_MANAGEMENT);
            if (holders <= 1) {
                throw new IllegalStateException("Cannot revoke USER_MANAGEMENT from the last user who holds it");
            }
        }

        permissionRepo.deleteByUserIdAndPermission(userId, permission);
        invalidateCache(userId);
    }

    @Transactional
    public void setPermissions(Long userId, Set<Permission> desired, Long grantedBy) {
        // Guard: built-in admin account is immutable
        userRepo.findById(userId).ifPresent(user -> {
            if ("admin".equals(user.getUsername())) {
                throw new IllegalStateException("Cannot change permissions for the built-in admin account");
            }
        });

        Set<Permission> current = getPermissions(userId);

        // Grant new ones
        for (Permission p : desired) {
            if (!current.contains(p)) {
                grantPermission(userId, p, grantedBy);
            }
        }

        // Revoke removed ones
        for (Permission p : current) {
            if (!desired.contains(p)) {
                // Check atomically for USER_MANAGEMENT last-holder guard
                if (p == Permission.USER_MANAGEMENT && !desired.contains(Permission.USER_MANAGEMENT)) {
                    long holders = permissionRepo.countByPermission(Permission.USER_MANAGEMENT);
                    if (holders <= 1) {
                        throw new IllegalStateException("Cannot revoke USER_MANAGEMENT from the last user who holds it");
                    }
                }
                permissionRepo.deleteByUserIdAndPermission(userId, p);
            }
        }

        invalidateCache(userId);
    }

    @Transactional
    public void grantDefaultPermissions(Long userId, Long grantedBy) {
        for (Permission p : DEFAULT_PERMISSIONS) {
            grantPermission(userId, p, grantedBy);
        }
    }

    @Transactional
    public void grantSpecificPermissions(Long userId, Set<Permission> permissions, Long grantedBy) {
        for (Permission p : permissions) {
            grantPermission(userId, p, grantedBy);
        }
    }

    public void invalidateCache(Long userId) {
        cache.invalidate(userId);
    }

    public boolean isBuiltinAdmin(Long userId) {
        return userRepo.findById(userId)
                .map(u -> "admin".equals(u.getUsername()))
                .orElse(false);
    }
}
