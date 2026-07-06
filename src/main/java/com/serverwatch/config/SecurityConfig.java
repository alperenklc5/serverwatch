package com.serverwatch.config;

import com.serverwatch.security.JwtAuthenticationFilter;
import com.serverwatch.security.SecurityHeadersFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Phase 9 security configuration — stateless JWT authentication.
 *
 * <p>Public endpoints: {@code /api/auth/login}, {@code /api/auth/refresh}, {@code /api/health}.
 * Everything else requires a valid {@code Authorization: Bearer <token>} header.
 * Admin-only operations are further restricted via {@code @PreAuthorize} on individual methods.
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
                // Everything else requires authentication
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
