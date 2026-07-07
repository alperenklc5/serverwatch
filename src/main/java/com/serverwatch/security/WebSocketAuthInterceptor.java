package com.serverwatch.security;

import com.serverwatch.model.entity.User;
import com.serverwatch.repository.UserRepository;
import com.serverwatch.service.JwtService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP channel interceptor that authenticates WebSocket CONNECT frames.
 *
 * <p>Clients must send the JWT in the STOMP CONNECT headers:
 * <pre>
 *   Authorization: Bearer &lt;accessToken&gt;
 * </pre>
 * The token is validated and the user principal is attached to the STOMP session.
 * All subsequent SUBSCRIBE / SEND frames in the same session inherit that principal.
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepo;

    public WebSocketAuthInterceptor(JwtService jwtService, UserRepository userRepo) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SecurityException("WebSocket connection rejected: missing Authorization header");
        }

        String token = authHeader.substring(7);
        Claims claims = jwtService.parseToken(token); // throws if invalid/expired
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
