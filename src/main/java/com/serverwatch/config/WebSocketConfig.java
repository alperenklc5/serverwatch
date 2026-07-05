package com.serverwatch.config;

import com.serverwatch.security.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Configures the STOMP-over-WebSocket message broker.
 *
 * <p>Topic layout:
 * <ul>
 *   <li>{@code /topic/metrics/system}    — SystemMetricDTO every 2 s</li>
 *   <li>{@code /topic/metrics/network}   — List&lt;NetworkMetricDTO&gt; every 2 s</li>
 *   <li>{@code /topic/metrics/processes} — List&lt;ProcessInfoDTO&gt; every 5 s</li>
 *   <li>{@code /topic/containers}        — container stats (Phase 4)</li>
 *   <li>{@code /topic/alerts}            — alert events (Phase 6)</li>
 *   <li>{@code /user/queue/errors}       — per-session error messages</li>
 * </ul>
 *
 * <p>WebSocket connections are authenticated via {@link WebSocketAuthInterceptor}, which
 * validates the JWT sent in the STOMP CONNECT {@code Authorization} header.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Primary endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Raw WebSocket endpoint (native WS clients and the test HTML page)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024);    // 128 KB per message
        registration.setSendBufferSizeLimit(512 * 1024); // 512 KB outbound buffer
        registration.setSendTimeLimit(20 * 1000);        // 20 s send timeout
    }
}
