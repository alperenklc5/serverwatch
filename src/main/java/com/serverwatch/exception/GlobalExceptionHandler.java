package com.serverwatch.exception;

import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.serverwatch.model.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.stream.Collectors;

/**
 * Centralised exception-to-HTTP-response mapper.
 * Every handler returns an {@link ApiResponse} so clients always see the same envelope.
 *
 * <p>Docker-specific exceptions are also handled here as a global fallback;
 * {@link com.serverwatch.controller.DockerController} has controller-scoped handlers
 * that fire first for Docker endpoints, providing more contextual messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Security / access exceptions ─────────────────────────────────────────

    /**
     * Handles path-traversal and allow-list violations thrown by PathValidator.
     * Note: Spring Security's own AccessDeniedException (from @PreAuthorize) is
     * handled by the SecurityConfig accessDeniedHandler before reaching here.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurity(SecurityException ex) {
        log.warn("Security violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied"));
    }

    // ── File-system exceptions ────────────────────────────────────────────────

    /**
     * Unwraps {@link UncheckedIOException} and maps the cause to an appropriate status.
     * {@link NoSuchFileException} → 404, {@link FileAlreadyExistsException} → 409,
     * all others → 500.
     */
    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ApiResponse<Void>> handleUncheckedIO(UncheckedIOException ex) {
        IOException cause = ex.getCause();
        if (cause instanceof NoSuchFileException nsfe) {
            log.debug("File not found: {}", nsfe.getFile());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("File not found: " + nsfe.getFile()));
        }
        if (cause instanceof FileAlreadyExistsException fae) {
            log.debug("File already exists: {}", fae.getFile());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("File already exists: " + fae.getFile()));
        }
        log.error("Filesystem error: {}", cause != null ? cause.getMessage() : ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Filesystem error: " +
                        (cause != null ? cause.getMessage() : ex.getMessage())));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupported(UnsupportedOperationException ex) {
        log.debug("Unsupported operation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── Application exceptions ────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        log.debug("Entity not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.debug("Validation failed: {}", message);
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    // ── Docker exceptions ─────────────────────────────────────────────────────

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDockerNotFound(NotFoundException ex) {
        log.debug("Docker resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Container or image not found: " + cleanDockerMessage(ex)));
    }

    @ExceptionHandler(NotModifiedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDockerNotModified(NotModifiedException ex) {
        // HTTP 304 cannot carry a body; use 409 with an explanatory message
        log.debug("Docker not-modified: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Container state unchanged (already started/stopped)"));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleDockerConflict(ConflictException ex) {
        log.warn("Docker conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Docker conflict: " + cleanDockerMessage(ex)));
    }

    @ExceptionHandler(DockerException.class)
    public ResponseEntity<ApiResponse<Void>> handleDockerException(DockerException ex) {
        log.error("Docker daemon error (HTTP {}): {}", ex.getHttpStatus(), ex.getMessage());
        HttpStatus status = ex.getHttpStatus() == 500
                ? HttpStatus.BAD_GATEWAY   // daemon error proxied through us
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(ApiResponse.error("Docker daemon error: " + cleanDockerMessage(ex)));
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    /**
     * Spring MVC throws this when no handler is registered for a path and the
     * static-resource handler also finds nothing. Without an explicit handler here,
     * the catch-all {@code Exception.class} below would swallow it and return 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        log.debug("No handler found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Resource not found"));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An internal error occurred"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Strip verbose docker-java boilerplate from exception messages. */
    private static String cleanDockerMessage(DockerException ex) {
        String msg = ex.getMessage();
        if (msg == null) return "unknown Docker error";
        // docker-java often wraps the daemon response in extra context
        int idx = msg.indexOf("{");
        if (idx > 0) {
            // Try to extract the "message" field from the JSON body
            int msgIdx = msg.indexOf("\"message\"");
            if (msgIdx >= 0) {
                int start = msg.indexOf(":", msgIdx) + 2;
                int end   = msg.indexOf("\"", start + 1);
                if (start > 0 && end > start) return msg.substring(start, end);
            }
        }
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }
}
