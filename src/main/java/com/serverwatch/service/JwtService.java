package com.serverwatch.service;

import com.serverwatch.config.ServerWatchProperties;
import com.serverwatch.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Handles JWT access-token generation and validation, plus refresh-token utilities.
 *
 * <p>Access tokens are signed JWTs (HS256). Refresh tokens are opaque UUIDs whose
 * SHA-256 hashes are stored in the database — the raw value is only ever seen by the client.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(ServerWatchProperties properties) {
        ServerWatchProperties.Security sec = properties.getSecurity();
        byte[] keyBytes = sec.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = sec.getJwtExpirationMs();
        this.refreshTokenExpirationMs = accessTokenExpirationMs * 30L; // 30× access lifetime
    }

    /**
     * Generates a signed JWT access token for the given user.
     * Claims: sub (username), userId, role, type=access.
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpirationMs)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Generates a random opaque refresh token string.
     * Callers are responsible for hashing it before persistence.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    /**
     * Parses and validates a JWT, returning its claims.
     *
     * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns {@code true} if the token is a valid, non-expired JWT; {@code false} otherwise.
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Extracts the username (subject) from a valid JWT. */
    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * Returns a SHA-256 hex digest of the raw refresh token.
     * This is what gets stored in the database.
     */
    public String hashRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public long getAccessTokenExpirationMs()  { return accessTokenExpirationMs; }
    public long getRefreshTokenExpirationMs() { return refreshTokenExpirationMs; }
}
