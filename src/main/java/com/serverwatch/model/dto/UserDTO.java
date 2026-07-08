package com.serverwatch.model.dto;

import java.time.Instant;
import java.util.Set;

public record UserDTO(
        Long id,
        String username,
        String email,
        String displayName,
        String role,
        boolean enabled,
        Instant lastLoginAt,
        Instant createdAt,
        Set<String> permissions
) {}
