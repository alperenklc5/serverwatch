package com.serverwatch.config;

import com.serverwatch.security.JwtAuthenticationFilter;
import com.serverwatch.security.SecurityHeadersFilter;
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
 * Security configuration — stateless JWT authentication.
 *
 * <p>Public endpoints: {@code /api/auth/login}, {@code /api/auth/refresh}, {@code /api/health}.
 * Everything else requires a valid {@code Authorization: Bearer <token>} header.
 * Authorization is enforced entirely via URL-based requestMatchers; no @PreAuthorize is used
 * because STOMP/WebSocket threads do not carry a SecurityContext.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final SecurityHeadersFilter securityHeadersFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          SecurityHeadersFilter securityHeadersFilter) {
        this.jwtFilter = jwtFilter;
        this.securityHeadersFilter = securityHeadersFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                // Health check
                .requestMatchers("/api/health").permitAll()
                // WebSocket handshake — JWT validated separately in WebSocketAuthInterceptor
                .requestMatchers("/ws/**").permitAll()
                // Dev static test page
                .requestMatchers("/ws-test.html").permitAll()

                // ── ADMIN-only endpoints ─────────────────────────────────────────
                // Terminal REST + WebSocket destinations enforced in WebSocketAuthInterceptor
                .requestMatchers("/api/terminal/**").hasRole("ADMIN")
                // Docker lifecycle operations
                .requestMatchers(HttpMethod.POST,
                        "/api/docker/containers/*/start",
                        "/api/docker/containers/*/stop",
                        "/api/docker/containers/*/restart",
                        "/api/docker/containers/*/pause",
                        "/api/docker/containers/*/unpause").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/docker/containers/**").hasRole("ADMIN")
                // File write / destructive operations
                .requestMatchers(HttpMethod.POST,
                        "/api/files/write",
                        "/api/files/create",
                        "/api/files/upload",
                        "/api/files/chmod",
                        "/api/files/move",
                        "/api/files/copy").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/files").hasRole("ADMIN")
                // User management
                .requestMatchers("/api/auth/register", "/api/auth/users/**").hasRole("ADMIN")
                // Git write operations
                .requestMatchers(HttpMethod.POST,
                        "/api/git/repos/clone",
                        "/api/git/repos/add",
                        "/api/git/repos/*/pull",
                        "/api/git/repos/*/push",
                        "/api/git/repos/*/checkout").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/git/repos/**").hasRole("ADMIN")
                // Alert rule management
                .requestMatchers(HttpMethod.POST, "/api/alerts/rules").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,   "/api/alerts/rules/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/alerts/rules/**").hasRole("ADMIN")

                // Everything else requires authentication (any role)
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
            "http://localhost:3000",   // Create React App
            "http://localhost:5173"    // Vite
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
