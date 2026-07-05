# Phase 9 — Authentication & Authorization (JWT)

## Objective
Implement a complete authentication system with JWT tokens so ServerWatch cannot be accessed by anyone without valid credentials. Only registered users can view metrics, manage containers, use the terminal, or edit files. This is critical before deployment.

## Prerequisites
- Phase 8 completed — All backend features functional but unsecured

## Why This Is Critical
Right now `SecurityConfig` permits all requests. If you deploy without this phase, anyone who knows the URL can:
- See all your server metrics
- Stop/restart your containers
- Access your files
- Open a terminal on your VPS

**Do not deploy to a public IP without completing this phase.**

## Step 1: Add Dependencies

Add to `pom.xml`:
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

## Step 2: Database Migration

### V3__users_and_auth.sql
```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT false,
    user_agent      VARCHAR(500),
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE login_attempts (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50),
    ip_address      VARCHAR(45),
    success         BOOLEAN NOT NULL,
    user_agent      VARCHAR(500),
    attempted_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_login_attempts_username_time ON login_attempts(username, attempted_at);
CREATE INDEX idx_login_attempts_ip_time ON login_attempts(ip_address, attempted_at);

-- Insert default admin user (password will be set via CLI or first-boot flow)
-- password_hash is bcrypt of "changeme" — MUST BE CHANGED ON FIRST LOGIN
INSERT INTO users (username, email, password_hash, display_name, role)
VALUES ('admin', 'admin@localhost', '$2a$10$rBV2JDeWW3.vKyeQcM8fFOZUZuKBWtj8YRDcyR7HpOhOxLGvRvSPq',
        'Administrator', 'ADMIN');
```

## Step 3: Entities & Repositories

### User.java (JPA Entity)
```java
// Fields matching the users table
// Role is an enum: USER, ADMIN
// Implements UserDetails from Spring Security (getUsername, getPassword,
//   getAuthorities returning [ROLE_USER] or [ROLE_ADMIN], isEnabled, etc.)
```

### RefreshToken.java (JPA Entity)
Store token HASH, not the token itself. Compare hashes on validation.

### LoginAttempt.java (JPA Entity)
Audit trail for security monitoring.

### UserRepository.java
```java
Optional<User> findByUsername(String username);
Optional<User> findByEmail(String email);
boolean existsByUsername(String username);
```

### RefreshTokenRepository.java
```java
Optional<RefreshToken> findByTokenHashAndRevokedFalse(String hash);
List<RefreshToken> findByUserId(Long userId);
int deleteByExpiresAtBefore(Instant time);

@Modifying
@Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.userId = :userId")
void revokeAllForUser(@Param("userId") Long userId);
```

### LoginAttemptRepository.java
```java
long countByUsernameAndSuccessFalseAndAttemptedAtAfter(String username, Instant since);
long countByIpAddressAndSuccessFalseAndAttemptedAtAfter(String ip, Instant since);
```

## Step 4: DTOs

### AuthDTOs (records)
```java
public record LoginRequest(String username, String password) {}

public record RegisterRequest(
    String username, String email, String password, String displayName
) {}

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,          // seconds until access token expires
    UserDTO user
) {}

public record UserDTO(
    Long id,
    String username,
    String email,
    String displayName,
    String role,
    boolean enabled,
    Instant lastLoginAt,
    Instant createdAt
) {}

public record RefreshRequest(String refreshToken) {}

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
```

## Step 5: JWT Service

### JwtService.java

```java
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(ServerWatchProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecurity().getJwtSecret().getBytes());
        this.accessTokenExpirationMs = properties.getSecurity().getJwtExpirationMs();
        this.refreshTokenExpirationMs = accessTokenExpirationMs * 30; // 30x access token
    }

    public String generateAccessToken(User user) {
        return Jwts.builder()
            .subject(user.getUsername())
            .claim("userId", user.getId())
            .claim("role", user.getRole().name())
            .claim("type", "access")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusMillis(accessTokenExpirationMs)))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact();
    }

    public String generateRefreshToken(User user) {
        // Refresh token is a random UUID string, NOT a JWT
        // We store its hash in DB, and validate by looking up the hash
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }

    public String hashRefreshToken(String token) {
        // SHA-256 hash for DB storage
        return DigestUtils.sha256Hex(token);
    }
}
```

## Step 6: Authentication Service

### AuthService.java

```java
@Service
public class AuthService {

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final LoginAttemptRepository attemptRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Rate limiting: max 5 failed attempts per username per 15 minutes
    // Max 10 failed attempts per IP per 15 minutes

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.username();
        String ip = extractIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // 1. Check rate limits
        Instant window = Instant.now().minus(15, ChronoUnit.MINUTES);
        long failedByUsername = attemptRepo.countByUsernameAndSuccessFalseAndAttemptedAtAfter(username, window);
        long failedByIp = attemptRepo.countByIpAddressAndSuccessFalseAndAttemptedAtAfter(ip, window);

        if (failedByUsername >= 5 || failedByIp >= 10) {
            recordAttempt(username, ip, userAgent, false);
            throw new SecurityException("Too many failed attempts. Try again later.");
        }

        // 2. Look up user
        User user = userRepo.findByUsername(username).orElse(null);

        // 3. Verify password (constant-time compare to avoid timing attacks)
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordAttempt(username, ip, userAgent, false);
            // Use same error for both cases to avoid user enumeration
            throw new SecurityException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            recordAttempt(username, ip, userAgent, false);
            throw new SecurityException("Account disabled");
        }

        // 4. Success — generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenRaw = jwtService.generateRefreshToken(user);
        String refreshTokenHash = jwtService.hashRefreshToken(refreshTokenRaw);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(refreshTokenHash);
        rt.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()));
        rt.setUserAgent(userAgent);
        rt.setIpAddress(ip);
        refreshRepo.save(rt);

        // 5. Update last login
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        recordAttempt(username, ip, userAgent, true);

        return new AuthResponse(
            accessToken,
            refreshTokenRaw,
            jwtService.getAccessTokenExpirationMs() / 1000,
            toDTO(user)
        );
    }

    public AuthResponse refresh(String refreshTokenRaw) {
        String hash = jwtService.hashRefreshToken(refreshTokenRaw);
        RefreshToken stored = refreshRepo.findByTokenHashAndRevokedFalse(hash)
            .orElseThrow(() -> new SecurityException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new SecurityException("Refresh token expired");
        }

        User user = userRepo.findById(stored.getUserId())
            .orElseThrow(() -> new SecurityException("User not found"));

        if (!user.isEnabled()) throw new SecurityException("Account disabled");

        // Rotate refresh token: revoke old, issue new
        stored.setRevoked(true);
        refreshRepo.save(stored);

        // Generate new tokens (same logic as login)
        // Return new AuthResponse
    }

    public void logout(String refreshToken) {
        String hash = jwtService.hashRefreshToken(refreshToken);
        refreshRepo.findByTokenHashAndRevokedFalse(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshRepo.save(rt);
        });
    }

    public void logoutAllSessions(Long userId) {
        refreshRepo.revokeAllForUser(userId);
    }

    public UserDTO register(RegisterRequest request) {
        // Only ADMIN can create new users (checked in controller)
        // Validate: username unique, email unique, password min 8 chars
        // Create User with role=USER by default
        // Return UserDTO
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepo.findById(userId).orElseThrow();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new SecurityException("Current password incorrect");
        }

        if (request.newPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepo.save(user);

        // Revoke all refresh tokens to force re-login on all devices
        refreshRepo.revokeAllForUser(userId);
    }

    private void recordAttempt(String username, String ip, String userAgent, boolean success) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsername(username);
        attempt.setIpAddress(ip);
        attempt.setUserAgent(userAgent);
        attempt.setSuccess(success);
        attemptRepo.save(attempt);
    }

    private String extractIp(HttpServletRequest request) {
        // Check X-Forwarded-For header first (behind reverse proxy)
        // Fall back to request.getRemoteAddr()
    }

    // Scheduled cleanup
    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    public void cleanupExpiredTokens() {
        int deleted = refreshRepo.deleteByExpiresAtBefore(Instant.now());
        log.info("Cleaned up {} expired refresh tokens", deleted);
    }
}
```

## Step 7: JWT Filter

### JwtAuthenticationFilter.java

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            Claims claims = jwtService.parseToken(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            User user = userRepo.findByUsername(username).orElse(null);
            if (user != null && user.isEnabled()) {
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                        user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            // Token invalid — continue as anonymous, authorization filter will reject
            log.debug("JWT validation failed: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
```

## Step 8: Update Security Config

### SecurityConfig.java (rewrite)

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfig()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/api/health").permitAll()
                // WebSocket — auth handled separately below
                .requestMatchers("/ws/**").permitAll()
                // Static test page in dev only — remove in prod
                .requestMatchers("/ws-test.html").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Authentication required\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Access denied\"}");
                })
            );

        return http.build();
    }

    private CorsConfigurationSource corsConfig() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:3000", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

## Step 9: WebSocket Authentication

### WebSocketAuthInterceptor.java

WebSocket connections must also be authenticated. Client sends JWT in the STOMP CONNECT frame headers.

```java
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepo;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new SecurityException("Missing token");
            }

            String token = authHeader.substring(7);
            Claims claims = jwtService.parseToken(token);
            String username = claims.getSubject();

            User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new SecurityException("Invalid token"));

            // Attach user to session
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
            ));
        }

        return message;
    }
}
```

Register in `WebSocketConfig`:
```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(webSocketAuthInterceptor);
}
```

## Step 10: Auth Controller

### AuthController.java

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // POST /api/auth/login — public
    // POST /api/auth/refresh — public
    // POST /api/auth/logout — authenticated
    // POST /api/auth/logout-all — authenticated
    // GET /api/auth/me — authenticated, returns current user
    // POST /api/auth/change-password — authenticated
    // POST /api/auth/register — ADMIN only
    // GET /api/auth/users — ADMIN only, lists all users
    // PATCH /api/auth/users/{id}/enable — ADMIN only
    // PATCH /api/auth/users/{id}/disable — ADMIN only
    // DELETE /api/auth/users/{id} — ADMIN only
}
```

Use `@PreAuthorize("hasRole('ADMIN')")` on admin-only endpoints.

## Step 11: Method Security for Sensitive Operations

Add `@PreAuthorize` to controllers for role-based restrictions:

```java
// TerminalController — admin only (shell access is dangerous)
@PreAuthorize("hasRole('ADMIN')")
public TerminalSessionDTO createSession(...) { ... }

// FileController — write operations require ADMIN
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/write")
public FileEntryDTO writeFile(...) { ... }

// DockerController — destructive operations require ADMIN
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/containers/{id}")
public void removeContainer(...) { ... }
```

## Step 12: Initial Setup Flow

For a fresh deployment, users need to set the admin password. Options:

**Option A: Environment variable seed**
```yaml
serverwatch:
  security:
    initial-admin-password: ${INITIAL_ADMIN_PASSWORD:}
```

On startup, if `admin` user still has the default password AND this env var is set, update it.

**Option B: First-boot setup page**
Detect if `admin` user has the default password, redirect all requests to `/setup` where the password can be changed. Complex, skip for now.

**Recommended:** Option A with clear docs telling the user to set `INITIAL_ADMIN_PASSWORD` in their `.env` file before first startup.

## Step 13: Security Headers

Add a filter that sets security headers on all responses:

```java
// Content-Security-Policy: default-src 'self'
// X-Frame-Options: DENY
// X-Content-Type-Options: nosniff
// Strict-Transport-Security: max-age=31536000; includeSubDomains (HTTPS only)
// Referrer-Policy: strict-origin-when-cross-origin
```

## Acceptance Criteria
- [ ] `POST /api/auth/login` with valid credentials returns access + refresh tokens
- [ ] `POST /api/auth/login` with wrong password returns 401
- [ ] After 5 failed logins, the account is temporarily locked (15 min)
- [ ] After 10 failed logins from one IP, that IP is blocked (15 min)
- [ ] Requests without JWT to protected endpoints return 401
- [ ] Requests with expired JWT return 401
- [ ] Requests with valid JWT succeed
- [ ] `POST /api/auth/refresh` with valid refresh token issues new access token
- [ ] Refresh token is rotated on each use (old one revoked)
- [ ] `POST /api/auth/logout` revokes the refresh token
- [ ] Admin-only endpoints return 403 for USER role
- [ ] WebSocket connections without JWT fail
- [ ] WebSocket connections with valid JWT succeed
- [ ] Changing password revokes all refresh tokens
- [ ] Password is stored hashed (bcrypt), never plaintext
- [ ] `GET /api/auth/me` returns current user info from JWT
- [ ] Login attempts are recorded in DB
- [ ] Expired refresh tokens are cleaned up daily

## Files to Create/Modify
```
CREATE:
src/main/resources/db/migration/V3__users_and_auth.sql

src/main/java/com/serverwatch/
├── model/entity/
│   ├── User.java
│   ├── RefreshToken.java
│   └── LoginAttempt.java
├── model/dto/
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   ├── AuthResponse.java
│   ├── UserDTO.java
│   ├── RefreshRequest.java
│   └── ChangePasswordRequest.java
├── repository/
│   ├── UserRepository.java
│   ├── RefreshTokenRepository.java
│   └── LoginAttemptRepository.java
├── service/
│   ├── AuthService.java
│   └── JwtService.java
├── security/
│   ├── JwtAuthenticationFilter.java
│   ├── WebSocketAuthInterceptor.java
│   └── SecurityHeadersFilter.java
└── controller/
    └── AuthController.java

MODIFY:
├── pom.xml — add jjwt dependencies
├── config/SecurityConfig.java — complete rewrite with JWT
├── config/WebSocketConfig.java — add auth interceptor
├── controller/TerminalController.java — @PreAuthorize ADMIN
├── controller/FileController.java — @PreAuthorize on write ops
└── controller/DockerController.java — @PreAuthorize on destructive ops
```
