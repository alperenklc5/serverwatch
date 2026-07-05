# Phase 4 — Docker Container Management

## Objective
Implement full Docker container management: list containers with live stats, start/stop/restart containers, view real-time logs, inspect container details, and manage environment variables — all through REST and WebSocket APIs. This is the "no more SSH" feature that makes ServerWatch a real management tool.

## Prerequisites
- Phase 3 completed — WebSocket streaming operational
- Docker socket accessible at `/var/run/docker.sock`
- `com.github.docker-java` dependencies in pom.xml (added in Phase 1)

## Step 1: DTOs

### ContainerInfoDTO.java
```java
// Fields:
// - containerId (String) — short 12-char ID
// - containerIdFull (String) — full SHA
// - name (String) — container name without leading "/"
// - image (String) — image name:tag
// - state (String) — "running", "stopped", "paused", "restarting", "exited"
// - status (String) — human-readable, e.g., "Up 3 hours", "Exited (0) 5 minutes ago"
// - created (Instant)
// - ports (List<PortMapping>) — host:container port mappings
// - networks (List<String>) — network names
// - volumes (List<String>) — volume mount paths
// - labels (Map<String, String>)
// - envVars (List<String>) — environment variables (key=value format)
```

### PortMapping.java (nested DTO)
```java
// - privatePort (int) — container port
// - publicPort (int) — host port (0 if not published)
// - type (String) — "tcp" or "udp"
// - ip (String) — binding IP
```

### ContainerStatsDTO.java
```java
// - containerId (String)
// - containerName (String)
// - cpuPercent (double) — calculated CPU usage percentage
// - memoryUsageBytes (long)
// - memoryLimitBytes (long)
// - memoryPercent (double)
// - networkRxBytes (long) — total received
// - networkTxBytes (long) — total transmitted
// - blockReadBytes (long)
// - blockWriteBytes (long)
// - pidCount (int) — number of processes in container
// - timestamp (Instant)
```

### ContainerLogDTO.java
```java
// - containerId (String)
// - lines (List<String>) — log lines
// - since (Instant)
// - stdout (boolean)
// - stderr (boolean)
```

### ContainerActionDTO.java (request body for actions)
```java
// - containerId (String)
// - action (String) — "start", "stop", "restart", "pause", "unpause", "remove"
// - force (boolean) — for stop/remove, force kill
// - timeout (int) — seconds to wait before force kill on stop (default 10)
```

## Step 2: DockerService

### DockerService.java

This is the core service. All Docker operations go through here.

```java
@Service
public class DockerService {

    private final DockerClient dockerClient;
    private final WebSocketPublisher wsPublisher;

    // ====== CONTAINER LISTING ======

    public List<ContainerInfoDTO> listContainers(boolean showAll) {
        // dockerClient.listContainersCmd().withShowAll(showAll).exec()
        // Map each Container to ContainerInfoDTO
        // showAll=true includes stopped containers
        // Strip leading "/" from container names
    }

    // ====== CONTAINER STATS ======

    public ContainerStatsDTO getContainerStats(String containerId) {
        // Use dockerClient.statsCmd(containerId).withNoStream(true).exec(callback)
        // Calculate CPU percent from stats:
        //   cpuDelta = stats.getCpuStats().getCpuUsage().getTotalUsage()
        //              - stats.getPreCpuStats().getCpuUsage().getTotalUsage()
        //   systemDelta = stats.getCpuStats().getSystemCpuUsage()
        //                 - stats.getPreCpuStats().getSystemCpuUsage()
        //   cpuPercent = (cpuDelta / systemDelta) * numCpus * 100.0
        //
        // Memory: stats.getMemoryStats().getUsage() / getLimit()
        // Network: sum all interface stats
        // Block I/O: sum read/write ops
    }

    public List<ContainerStatsDTO> getAllContainerStats() {
        // List running containers, get stats for each
        // Execute in parallel using CompletableFuture for performance
        // Return aggregated list
    }

    // ====== CONTAINER LIFECYCLE ======

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
        log.info("Started container: {}", containerId);
    }

    public void stopContainer(String containerId, int timeoutSeconds) {
        dockerClient.stopContainerCmd(containerId)
                    .withTimeout(timeoutSeconds)
                    .exec();
        log.info("Stopped container: {}", containerId);
    }

    public void restartContainer(String containerId, int timeoutSeconds) {
        dockerClient.restartContainerCmd(containerId)
                    .withTimeout(timeoutSeconds)
                    .exec();
        log.info("Restarted container: {}", containerId);
    }

    public void pauseContainer(String containerId) {
        dockerClient.pauseContainerCmd(containerId).exec();
    }

    public void unpauseContainer(String containerId) {
        dockerClient.unpauseContainerCmd(containerId).exec();
    }

    public void removeContainer(String containerId, boolean force) {
        dockerClient.removeContainerCmd(containerId)
                    .withForce(force)
                    .withRemoveVolumes(false) // never auto-remove volumes
                    .exec();
        log.info("Removed container: {}", containerId);
    }

    // ====== CONTAINER INSPECTION ======

    public InspectContainerResponse inspectContainer(String containerId) {
        return dockerClient.inspectContainerCmd(containerId).exec();
        // Returns full container config: env vars, mounts, network settings,
        // restart policy, resource limits, health check config, etc.
    }

    // ====== CONTAINER LOGS ======

    public List<String> getContainerLogs(String containerId, int tailLines, boolean stdout, boolean stderr) {
        // Use ResultCallback.Adapter to collect log frames
        // dockerClient.logContainerCmd(containerId)
        //     .withStdOut(stdout)
        //     .withStdErr(stderr)
        //     .withTail(tailLines)
        //     .withTimestamps(true)
        //     .exec(callback)
        //     .awaitCompletion(10, TimeUnit.SECONDS);
        //
        // Each Frame has a streamType (STDOUT, STDERR) and payload
        // Collect into a list of strings, preserving order
    }

    // ====== REAL-TIME LOG STREAMING (WebSocket) ======

    public void streamContainerLogs(String containerId, String wsSessionId) {
        // dockerClient.logContainerCmd(containerId)
        //     .withStdOut(true)
        //     .withStdErr(true)
        //     .withFollowStream(true)
        //     .withTail(100) // send last 100 lines first, then follow
        //     .exec(new ResultCallback.Adapter<Frame>() {
        //         @Override
        //         public void onNext(Frame frame) {
        //             // Send each log line to the specific user via WebSocket
        //             // /user/{sessionId}/queue/container-logs
        //         }
        //     });
        //
        // Store the callback handle in a Map<String, Closeable> so we can
        // stop streaming when the client disconnects or requests stop
    }

    public void stopLogStream(String wsSessionId) {
        // Close the stored callback for this session
    }

    // ====== IMAGE OPERATIONS ======

    public List<ImageDTO> listImages() {
        // dockerClient.listImagesCmd().exec()
        // Map to simplified DTO: id, repoTags, size, created
    }

    // ====== DOCKER SYSTEM INFO ======

    public DockerInfoDTO getDockerInfo() {
        // dockerClient.infoCmd().exec()
        // Return: docker version, total containers (running/paused/stopped),
        // total images, storage driver, OS, architecture, total memory
    }
}
```

## Step 3: Scheduled Container Stats Broadcasting

Add to `MetricService` or create a new `ContainerMetricScheduler`:

```java
@Component
public class ContainerMetricScheduler {

    private final DockerService dockerService;
    private final WebSocketPublisher wsPublisher;

    @Scheduled(fixedDelay = 3000) // every 3 seconds
    public void broadcastContainerStats() {
        try {
            List<ContainerStatsDTO> stats = dockerService.getAllContainerStats();
            wsPublisher.publishContainerStats(stats);
        } catch (Exception e) {
            log.warn("Failed to collect container stats: {}", e.getMessage());
        }
    }
}
```

## Step 4: REST Controller

### DockerController.java
```
GET    /api/docker/containers              → List<ContainerInfoDTO>  (running only)
GET    /api/docker/containers?all=true      → List<ContainerInfoDTO>  (include stopped)
GET    /api/docker/containers/{id}          → InspectContainerResponse (full details)
GET    /api/docker/containers/{id}/stats    → ContainerStatsDTO
GET    /api/docker/containers/{id}/logs     → ContainerLogDTO
       Query params: tail=100, stdout=true, stderr=true

POST   /api/docker/containers/{id}/start    → 200 OK
POST   /api/docker/containers/{id}/stop     → 200 OK (body: {"timeout": 10})
POST   /api/docker/containers/{id}/restart  → 200 OK (body: {"timeout": 10})
POST   /api/docker/containers/{id}/pause    → 200 OK
POST   /api/docker/containers/{id}/unpause  → 200 OK
DELETE /api/docker/containers/{id}          → 200 OK (query: ?force=false)

GET    /api/docker/images                   → List<ImageDTO>
GET    /api/docker/info                     → DockerInfoDTO
```

**Error handling:** Docker operations can throw `NotFoundException` (container doesn't exist), `NotModifiedException` (container already started/stopped), `ConflictException` (container is running, can't remove). Map these to proper HTTP status codes:
- NotFoundException → 404
- NotModifiedException → 304
- ConflictException → 409

## Step 5: WebSocket Log Streaming Controller

### ContainerWebSocketController.java
```java
@Controller
public class ContainerWebSocketController {

    private final DockerService dockerService;

    @MessageMapping("/container/logs/start")
    public void startLogStream(@Payload Map<String, String> request,
                                SimpMessageHeaderAccessor accessor) {
        String containerId = request.get("containerId");
        String sessionId = accessor.getSessionId();
        dockerService.streamContainerLogs(containerId, sessionId);
    }

    @MessageMapping("/container/logs/stop")
    public void stopLogStream(SimpMessageHeaderAccessor accessor) {
        dockerService.stopLogStream(accessor.getSessionId());
    }
}
```

**Clean up on disconnect:** In `WebSocketEventListener.handleDisconnect()`, call `dockerService.stopLogStream(sessionId)` to close any active log streams for that session.

## Step 6: Safety Guards

Add validation and safety measures:

1. **Never allow removing a running container without force=true** — return 409 with a clear message
2. **Container name validation** — accept both container ID (short/full) and container name
3. **Rate limiting on destructive operations** — max 10 stop/restart operations per minute (use a simple counter)
4. **Audit logging** — log every start/stop/restart/remove with timestamp and container details
5. **Self-protection** — detect if a container is ServerWatch itself (by label or name) and refuse to stop/remove it. Add a label `serverwatch.managed=true` to the ServerWatch container in docker-compose.yml.

## Acceptance Criteria
- [ ] `GET /api/docker/containers` lists all running containers with names, images, ports, and state
- [ ] `GET /api/docker/containers?all=true` includes stopped/exited containers
- [ ] `GET /api/docker/containers/{id}/stats` returns CPU%, memory%, network I/O for a specific container
- [ ] `POST /api/docker/containers/{id}/stop` actually stops a running container
- [ ] `POST /api/docker/containers/{id}/start` starts a stopped container
- [ ] `POST /api/docker/containers/{id}/restart` restarts a container
- [ ] `GET /api/docker/containers/{id}/logs?tail=50` returns the last 50 log lines
- [ ] WebSocket subscription to `/topic/containers` receives container stats every 3 seconds
- [ ] WebSocket log streaming works: sending to `/app/container/logs/start` starts live log feed
- [ ] Disconnecting a WebSocket session cleans up any active log streams
- [ ] ServerWatch cannot stop/remove itself
- [ ] All Docker errors return appropriate HTTP status codes with clear error messages

## Files to Create/Modify
```
CREATE:
src/main/java/com/serverwatch/
├── model/dto/
│   ├── ContainerInfoDTO.java
│   ├── ContainerStatsDTO.java
│   ├── ContainerLogDTO.java
│   ├── ContainerActionDTO.java
│   ├── PortMapping.java
│   ├── ImageDTO.java
│   └── DockerInfoDTO.java
├── service/
│   └── DockerService.java
├── controller/
│   └── DockerController.java
├── websocket/
│   └── ContainerWebSocketController.java
└── scheduler/
    └── ContainerMetricScheduler.java

MODIFY:
├── websocket/WebSocketEventListener.java — cleanup log streams on disconnect
├── controller/HealthController.java — add container count to health
└── exception/GlobalExceptionHandler.java — add Docker-specific exception handlers
```
