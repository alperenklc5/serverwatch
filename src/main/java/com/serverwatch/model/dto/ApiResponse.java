package com.serverwatch.model.dto;

import java.time.Instant;

/**
 * Standard wrapper for all REST API responses.
 *
 * @param <T> the payload type
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now());
    }
}
