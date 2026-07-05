package com.serverwatch.security;

import com.serverwatch.repository.UserRepository;
import com.serverwatch.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts and validates the JWT from the {@code Authorization: Bearer <token>} header.
 * On success, loads the user and populates the {@link SecurityContextHolder}.
 * Invalid or missing tokens are silently ignored here — downstream authorization
 * rules will reject unauthenticated requests.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepo;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepo) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
    }

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

            // Only set authentication if not already set in this request
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                userRepo.findByUsername(username).ifPresent(user -> {
                    if (user.isEnabled()) {
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        user, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                });
            }
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
