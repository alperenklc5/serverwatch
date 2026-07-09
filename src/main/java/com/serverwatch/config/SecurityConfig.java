package com.serverwatch.config;

import com.serverwatch.model.entity.Permission;
import com.serverwatch.security.JwtAuthenticationFilter;
import com.serverwatch.security.PermissionAuthorizationManager;
import com.serverwatch.security.SecurityHeadersFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration — stateless JWT + per-user permission system.
 *
 * <p>Phase 10 replaces the previous role-based {@code hasRole("ADMIN")} approach with
 * {@link PermissionAuthorizationManager} which performs a database-backed permission check
 * per user. This resolves a bug where Spring Security 6's {@code MvcRequestMatcher}
 * silently failed to match paths at startup, causing authorization rules to be bypassed.
 *
 * <p>Public endpoints: {@code /api/auth/login}, {@code /api/auth/refresh}, {@code /api/health}.
 * Everything else requires a valid {@code Authorization: Bearer <token>} header plus the
 * specific permission for that resource.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final SecurityHeadersFilter securityHeadersFilter;
    private final ApplicationContext applicationContext;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          SecurityHeadersFilter securityHeadersFilter,
                          ApplicationContext applicationContext) {
        this.jwtFilter = jwtFilter;
        this.securityHeadersFilter = securityHeadersFilter;
        this.applicationContext = applicationContext;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Resolved here (not at construction time) to break the circular dependency:
        // SecurityConfig → PermissionAuthorizationManager → PermissionService → UserRepository
        // → JPA/datasource infra → Spring Security → SecurityConfig
        PermissionAuthorizationManager authManager =
                applicationContext.getBean(PermissionAuthorizationManager.class);
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Public ──────────────────────────────────────────────────────
                .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/api/health").permitAll()
                // WebSocket handshake — JWT validated in WebSocketAuthInterceptor
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/ws-test.html").permitAll()

                // ── User management (USER_MANAGEMENT permission) ──────────────
                // Must come before anyRequest so these are explicitly guarded
                .requestMatchers("/api/auth/register", "/api/auth/users/**", "/api/users/**")
                    .access(authManager.requiring(Permission.USER_MANAGEMENT))

                // ── Terminal ──────────────────────────────────────────────────
                .requestMatchers("/api/terminal/**")
                    .access(authManager.requiring(Permission.TERMINAL_ACCESS))

                // ── Files ──────────────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST,
                        "/api/files/write", "/api/files/create", "/api/files/upload",
                        "/api/files/chmod", "/api/files/move", "/api/files/copy")
                    .access(authManager.requiring(Permission.FILES_WRITE))
                .requestMatchers(HttpMethod.DELETE, "/api/files")
                    .access(authManager.requiring(Permission.FILES_DELETE))
                .requestMatchers(HttpMethod.GET,
                        "/api/files/list", "/api/files/roots", "/api/files/breadcrumbs",
                        "/api/files/read", "/api/files/download", "/api/files/search",
                        "/api/files/size")
                    .access(authManager.requiring(Permission.FILES_VIEW))

                // ── Docker ─────────────────────────────────────────────────────
                // Specific lifecycle ops first (before broader GET /api/docker/**)
                .requestMatchers(HttpMethod.POST,
                        "/api/docker/containers/*/start",
                        "/api/docker/containers/*/stop",
                        "/api/docker/containers/*/restart",
                        "/api/docker/containers/*/pause",
                        "/api/docker/containers/*/unpause")
                    .access(authManager.requiring(Permission.DOCKER_CONTROL))
                .requestMatchers(HttpMethod.DELETE, "/api/docker/containers/**")
                    .access(authManager.requiring(Permission.DOCKER_DELETE))
                .requestMatchers(HttpMethod.GET, "/api/docker/**")
                    .access(authManager.requiring(Permission.DOCKER_VIEW))

                // ── Git ────────────────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST,
                        "/api/git/repos/clone", "/api/git/repos/add",
                        "/api/git/repos/*/pull", "/api/git/repos/*/push",
                        "/api/git/repos/*/fetch", "/api/git/repos/*/checkout",
                        "/api/git/repos/*/branches")
                    .access(authManager.requiring(Permission.GIT_WRITE))
                .requestMatchers(HttpMethod.DELETE, "/api/git/repos/**")
                    .access(authManager.requiring(Permission.GIT_WRITE))
                .requestMatchers(HttpMethod.GET, "/api/git/**")
                    .access(authManager.requiring(Permission.GIT_VIEW))

                // ── Alerts ─────────────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST,  "/api/alerts/rules/**")
                    .access(authManager.requiring(Permission.ALERTS_MANAGE))
                .requestMatchers(HttpMethod.PUT,   "/api/alerts/rules/**")
                    .access(authManager.requiring(Permission.ALERTS_MANAGE))
                .requestMatchers(HttpMethod.DELETE, "/api/alerts/rules/**")
                    .access(authManager.requiring(Permission.ALERTS_MANAGE))
                .requestMatchers(HttpMethod.GET, "/api/alerts/**")
                    .access(authManager.requiring(Permission.ALERTS_VIEW))

                // ── Dashboard metrics — any authenticated user ──────────────
                .requestMatchers("/api/metrics/**").authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"success\":false,\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"success\":false,\"message\":\"Access denied\"}");
                })
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:3000",
            "http://localhost:5173"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
