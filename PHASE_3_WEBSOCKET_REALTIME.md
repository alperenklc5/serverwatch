# Phase 3 — WebSocket Real-Time Streaming

## Objective
Implement STOMP over WebSocket so that the frontend can subscribe to real-time metric channels and receive live updates every 2 seconds without polling. This phase converts the pull-based REST model from Phase 2 into a push-based real-time stream.

## Prerequisites
- Phase 2 completed — all collectors working, MetricService caching latest values

## Architecture
```
Client (Browser)
   │
   ├── CONNECT ws://host:8090/ws
   │
   ├── SUBSCRIBE /topic/metrics/system     → SystemMetricDTO every 2s
   ├── SUBSCRIBE /topic/metrics/network    → List<NetworkMetricDTO> every 2s
   ├── SUBSCRIBE /topic/metrics/processes  → List<ProcessInfoDTO> every 5s
   ├── SUBSCRIBE /topic/containers         → List<ContainerInfoDTO> every 3s (Phase 4)
   ├── SUBSCRIBE /topic/alerts             → AlertEvent on trigger (Phase 6)
   │
   └── SUBSCRIBE /user/queue/errors        → personal error messages
```

## Step 1: WebSocket Configuration

### WebSocketConfig.java
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for /topic (broadcast) and /queue (user-specific)
        config.enableSimpleBroker("/topic", "/queue");
        // Application destination prefix for @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Primary endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Raw WebSocket endpoint (for clients that don't need SockJS)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024); // 128KB
        registration.setSendBufferSizeLimit(512 * 1024); // 512KB
        registration.setSendTimeLimit(20 * 1000); // 20 seconds
    }
}
```

**Important:** Also update `SecurityConfig.java` to permit the `/ws/**` endpoint without authentication.

## Step 2: WebSocket Publisher Service

### WebSocketPublisher.java
This service is responsible for pushing data to WebSocket topics. It is called by the scheduled collectors.

```java
@Service
public class WebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishSystemMetrics(SystemMetricDTO metrics) {
        messagingTemplate.convertAndSend("/topic/metrics/system", metrics);
    }

    public void publishNetworkMetrics(List<NetworkMetricDTO> metrics) {
        messagingTemplate.convertAndSend("/topic/metrics/network", metrics);
    }

    public void publishProcesses(List<ProcessInfoDTO> processes) {
        messagingTemplate.convertAndSend("/topic/metrics/processes", processes);
    }

    public void publishContainerStats(List<ContainerInfoDTO> containers) {
        messagingTemplate.convertAndSend("/topic/containers", containers);
    }

    public void publishAlert(AlertEventDTO alert) {
        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }

    // Send error to a specific user session
    public void sendErrorToUser(String sessionId, String message) {
        messagingTemplate.convertAndSendToUser(
            sessionId, "/queue/errors",
            Map.of("error", message, "timestamp", Instant.now())
        );
    }
}
```

## Step 3: Modify MetricService to Publish

Update `MetricService.collectAll()` to call `WebSocketPublisher` after collecting metrics:

```java
@Scheduled(fixedDelayString = "${serverwatch.collector.interval-ms}")
public void collectAll() {
    try {
        SystemMetricDTO systemMetric = systemCollector.collect();
        latestSystemMetric = systemMetric;
        webSocketPublisher.publishSystemMetrics(systemMetric);

        List<NetworkMetricDTO> networkMetrics = networkCollector.collect();
        latestNetworkMetrics = networkMetrics;
        webSocketPublisher.publishNetworkMetrics(networkMetrics);
    } catch (Exception e) {
        log.error("Error collecting metrics", e);
    }
}

// Separate schedule for processes (less frequent, more expensive)
@Scheduled(fixedDelay = 5000)
public void collectProcesses() {
    try {
        List<ProcessInfoDTO> processes = processCollector.collectTopProcesses(20);
        webSocketPublisher.publishProcesses(processes);
    } catch (Exception e) {
        log.error("Error collecting process info", e);
    }
}
```

## Step 4: WebSocket Event Listener

### WebSocketEventListener.java
Track connected sessions for monitoring and debugging.

```java
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);
    private final AtomicInteger connectedClients = new AtomicInteger(0);

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        int count = connectedClients.incrementAndGet();
        log.info("WebSocket client connected. Total clients: {}", count);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        int count = connectedClients.decrementAndGet();
        log.info("WebSocket client disconnected. Total clients: {}", count);
    }

    public int getConnectedClientCount() {
        return connectedClients.get();
    }
}
```

## Step 5: WebSocket Message Controller (optional interactive commands)

### MetricWebSocketController.java
Allow clients to send commands via WebSocket (not just receive).

```java
@Controller
public class MetricWebSocketController {

    private final MetricService metricService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/metrics/request")
    @SendTo("/topic/metrics/system")
    public SystemMetricDTO requestImmediate() {
        // Client can request an immediate metric snapshot
        return metricService.getLatestSystemMetric();
    }

    @MessageMapping("/metrics/history")
    public void requestHistory(@Payload Map<String, String> request,
                                SimpMessageHeaderAccessor headerAccessor) {
        String type = request.get("type");
        int hours = Integer.parseInt(request.getOrDefault("hours", "1"));

        List<MetricSnapshot> history = metricService.getHistory(
            type,
            Instant.now().minus(hours, ChronoUnit.HOURS),
            Instant.now()
        );

        // Send to the requesting user only
        messagingTemplate.convertAndSendToUser(
            headerAccessor.getSessionId(),
            "/queue/history",
            history,
            createHeaders(headerAccessor.getSessionId())
        );
    }

    private MessageHeaders createHeaders(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return accessor.getMessageHeaders();
    }
}
```

## Step 6: Update Health Endpoint

Add WebSocket client count to the health endpoint:
```json
{
  "status": "UP",
  "version": "1.0.0",
  "timestamp": "...",
  "docker": true,
  "database": true,
  "websocketClients": 2
}
```

## Step 7: Simple HTML Test Page (for manual testing)

Create `src/main/resources/static/ws-test.html` — a minimal HTML page that:
1. Connects to `ws://localhost:8090/ws` using SockJS + STOMP.js
2. Subscribes to `/topic/metrics/system`
3. Displays incoming messages as JSON in a `<pre>` tag
4. Shows connection status (connected/disconnected)
5. Has a "Disconnect" button

Include these CDN scripts:
```html
<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1.6.1/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7.0.0/bundles/stomp.umd.min.js"></script>
```

This test page is temporary — it validates the WebSocket layer before the React frontend is built.

## Acceptance Criteria
- [ ] WebSocket endpoint at `ws://localhost:8090/ws` accepts connections
- [ ] Subscribing to `/topic/metrics/system` receives `SystemMetricDTO` every 2 seconds
- [ ] Subscribing to `/topic/metrics/network` receives network data every 2 seconds
- [ ] Subscribing to `/topic/metrics/processes` receives process list every 5 seconds
- [ ] Multiple clients can connect simultaneously and all receive broadcasts
- [ ] Client disconnect is logged, connected count updates correctly
- [ ] `@MessageMapping("/metrics/request")` returns an immediate snapshot on demand
- [ ] REST endpoints from Phase 2 still work (WebSocket is additive, not a replacement)
- [ ] Test HTML page at `http://localhost:8090/ws-test.html` shows live metrics
- [ ] No memory leak on repeated connect/disconnect cycles

## Files to Create/Modify
```
CREATE:
src/main/java/com/serverwatch/
├── config/
│   └── WebSocketConfig.java
├── service/
│   └── WebSocketPublisher.java
├── websocket/
│   ├── MetricWebSocketController.java
│   └── WebSocketEventListener.java
└── src/main/resources/static/
    └── ws-test.html

MODIFY:
├── config/SecurityConfig.java    — permit /ws/** endpoints
├── service/MetricService.java    — inject and call WebSocketPublisher
└── controller/HealthController.java — add wsClients count
```
