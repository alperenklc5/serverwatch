package com.serverwatch.service;

import com.serverwatch.model.dto.*;
import com.serverwatch.model.entity.LoginAttempt;
import com.serverwatch.model.entity.RefreshToken;
import com.serverwatch.model.entity.Role;
import com.serverwatch.model.entity.User;
import com.serverwatch.repository.LoginAttemptRepository;
import com.serverwatch.repository.RefreshTokenRepository;
import com.serverwatch.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Core authentication service: login, logout, token refresh, registration, and password changes.
 *
 * <p>Brute-force protection: max 5 failed attempts per username or 10 per IP within 15 minutes.
 * Refresh tokens are rotated on every use (old token revoked, new one issued).
 */
@Slf4j
@Service
public class AuthService {

    private static final int MAX_ATTEMPTS_PER_USERNAME = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 10;
    private static final int LOCKOUT_WINDOW_MINUTES = 15;

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final LoginAttemptRepository attemptRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepo,
                       RefreshTokenRepository refreshRepo,
                       LoginAttemptRepository attemptRepo,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.attemptRepo = attemptRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Authenticates a user and returns a fresh access + refresh token pair.
     *
     * @throws SecurityException if credentials are invalid, the account is disabled,
     *                           or rate limits are exceeded
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.username();
        String ip = extractIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // --- brute-force check ---
        Instant window = Instant.now().minus(LOCKOUT_WINDOW_MINUTES, ChronoUnit.MINUTES);
        long failedByUsername = attemptRepo.countByUsernameAndSuccessFalseAndAttemptedAtAfter(username, window);
        long failedByIp = attemptRepo.countByIpAddressAndSuccessFalseAndAttemptedAtAfter(ip, window);

        if (failedByUsername >= MAX_ATTEMPTS_PER_USERNAME || failedByIp >= MAX_ATTEMPTS_PER_IP) {
            recordAttempt(username, ip, userAgent, false);
            throw new SecurityException("Too many failed attempts. Try again in " + LOCKOUT_WINDOW_MINUTES + " minutes.");
        }

        // --- credential check (constant-time to prevent timing attacks) ---
        User user = userRepo.findByUsername(username).orElse(null);
        log.debug("Login attempt for username='{}': user_found={}", username, user != null);
        if (user != null) {
            boolean matches = passwordEncoder.matches(request.password(), user.getPasswordHash());
            log.debug("passwordEncoder.matches() = {} | stored hash prefix = '{}'",
                    matches, user.getPasswordHash().substring(0, Math.min(20, user.getPasswordHash().length())));
        }
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordAttempt(username, ip, userAgent, false);
            throw new SecurityException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            recordAttempt(username, ip, userAgent, false);
            throw new SecurityException("Account disabled");
        }

        // --- issue tokens ---
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = jwtService.generateRefreshToken();
        String hashRefresh = jwtService.hashRefreshToken(rawRefresh);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(hashRefresh);
        rt.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()));
        rt.setUserAgent(userAgent);
        rt.setIpAddress(ip);
        refreshRepo.save(rt);

        user.setLastLoginAt(Instant.now());
        userRepo.save(user);
        recordAttempt(username, ip, userAgent, true);

        log.info("Successful login for user '{}' from {}", username, ip);
        return new AuthResponse(
                accessToken,
                rawRefresh,
                jwtService.getAccessTokenExpirationMs() / 1000,
                toDTO(user));
    }

    /**
     * Issues a new access + refresh token pair using a valid refresh token.
     * The supplied refresh token is revoked and a new one is issued (rotation).
     *
     * @throws SecurityException if the token is invalid, expired, or already revoked
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        RefreshToken stored = refreshRepo.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new SecurityException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new SecurityException("Refresh token expired");
        }

        User user = userRepo.findById(stored.getUserId())
                .orElseThrow(() -> new SecurityException("User not found"));

        if (!user.isEnabled()) {
            throw new SecurityException("Account disabled");
        }

        // Rotate: revoke old, issue new
        stored.setRevoked(true);
        refreshRepo.save(stored);

        String accessToken = jwtService.generateAccessToken(user);
        String newRaw = jwtService.generateRefreshToken();

        RefreshToken newRt = new RefreshToken();
        newRt.setUserId(user.getId());
        newRt.setTokenHash(jwtService.hashRefreshToken(newRaw));
        newRt.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()));
        newRt.setUserAgent(stored.getUserAgent());
        newRt.setIpAddress(stored.getIpAddress());
        refreshRepo.save(newRt);

        return new AuthResponse(
                accessToken,
                newRaw,
                jwtService.getAccessTokenExpirationMs() / 1000,
                toDTO(user));
    }

    /**
     * Revokes a single refresh token (single-device logout).
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        refreshRepo.findByTokenHashAndRevokedFalse(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshRepo.save(rt);
        });
    }

    /**
     * Revokes all refresh tokens for a user (all-device logout).
     */
    @Transactional
    public void logoutAllSessions(Long userId) {
        refreshRepo.revokeAllForUser(userId);
        log.info("Revoked all sessions for userId={}", userId);
    }

    /**
     * Creates a new user account with {@code USER} role.
     * Only callable by ADMIN (enforced at the controller layer via {@code @PreAuthorize}).
     *
     * @throws IllegalArgumentException if username or email is already taken, or password is too short
     */
    @Transactional
    public UserDTO register(RegisterRequest request) {
        if (userRepo.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepo.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(Role.USER);
        user.setEnabled(true);

        return toDTO(userRepo.save(user));
    }

    /**
     * Changes the authenticated user's password and revokes all their refresh tokens,
     * forcing re-login on all devices.
     *
     * @throws SecurityException if {@code currentPassword} does not match
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new SecurityException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepo.save(user);

        // Force re-login everywhere
        refreshRepo.revokeAllForUser(userId);
        log.info("Password changed for userId={}; all sessions revoked", userId);
    }

    /** Returns all users. Caller must hold ADMIN role. */
    public List<UserDTO> getAllUsers() {
        return userRepo.findAll().stream().map(this::toDTO).toList();
    }

    /** Enables a user account. Caller must hold ADMIN role. */
    @Transactional
    public void enableUser(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEnabled(true);
        userRepo.save(user);
    }

    /** Disables a user account and revokes all their sessions. Caller must hold ADMIN role. */
    @Transactional
    public void disableUser(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEnabled(false);
        userRepo.save(user);
        refreshRepo.revokeAllForUser(userId);
    }

    /** Deletes a user and all their tokens. Caller must hold ADMIN role. */
    @Transactional
    public void deleteUser(Long userId) {
        userRepo.deleteById(userId); // cascade deletes refresh_tokens
    }

    /** Nightly cleanup of expired refresh tokens (3 AM). */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = refreshRepo.deleteByExpiresAtBefore(Instant.now());
        log.info("Cleaned up {} expired refresh tokens", deleted);
    }

    // ---- helpers ----

    private void recordAttempt(String username, String ip, String userAgent, boolean success) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsername(username);
        attempt.setIpAddress(ip);
        attempt.setUserAgent(userAgent);
        attempt.setSuccess(success);
        attemptRepo.save(attempt);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Converts a {@link User} entity to its public-facing DTO. */
    public UserDTO toDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isEnabled(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
