package com.serverwatch.model.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,   // seconds until access token expires
        UserDTO user
) {}
