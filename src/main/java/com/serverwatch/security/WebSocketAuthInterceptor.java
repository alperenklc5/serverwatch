package com.serverwatch.security;

import com.serverwatch.model.entity.Permission;
import com.serverwatch.model.entity.User;
import com.serverwatch.repository.UserRepository;
import com.serverwatch.service.JwtService;
import com.serverwatch.service.PermissionService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * STOMP channel interceptor that authenticates WebSocket CONNECT frames
 * and enforces TERMINAL_ACCESS permission on terminal message frames.
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepo;
    private final PermissionService permissionService;

    public WebSocketAuthInterceptor(JwtService jwtService,
                                    UserRepository userRepo,
                                    PermissionService permissionService) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
        this.permissionService = permissionService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Restore authentication on the current thread for every STOMP frame
        if (accessor != null) {
            Principal user = accessor.getUser();
            if (user != null) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication((Authentication) user);
                SecurityContextHolder.setContext(context);
            }
        }

        // Enforce TERMINAL_ACCESS permission on terminal SEND frames.
        // URL-based security matchers don't apply to STOMP message routing.
        if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/app/terminal/")) {
                Principal principal = accessor.getUser();
                if (!(principal instanceof Authentication auth)
                        || !(auth.getPrincipal() instanceof User user)) {
                    log.warn("WebSocket SEND to {} rejected: not authenticated", destination);
                    throw new SecurityException("WebSocket terminal access denied: authentication required");
                }
                if (!permissionService.hasPermission(user.getId(), Permission.TERMINAL_ACCESS)) {
                    log.warn("WebSocket SEND to {} rejected: TERMINAL_ACCESS not granted (user={})",
                            destination, user.getUsername());
                    throw new SecurityException("WebSocket terminal access denied: TERMINAL_ACCESS permission required");
                }
            }
        }

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SecurityException("WebSocket connection rejected: missing Authorization header");
        }

        String token = authHeader.substring(7);
        Claims claims = jwtService.parseToken(token);
        String username = claims.getSubject();
        String role = claims.get("role", String.class);

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new SecurityException("WebSocket connection rejected: user not found"));

        if (!user.isEnabled()) {
            throw new SecurityException("WebSocket connection rejected: account disabled");
        }

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        accessor.setUser(principal);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(principal);
        SecurityContextHolder.setContext(context);

        log.debug("WebSocket authenticated for user '{}'", username);
        return message;
    }
}
