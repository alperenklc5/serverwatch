package com.serverwatch.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.*;
import com.serverwatch.model.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core service for all Docker container operations.
 *
 * <h3>Safety guards</h3>
 * <ul>
 *   <li><b>Self-protection</b> — containers labelled {@code serverwatch.managed=true}
 *       or whose name contains "serverwatch" cannot be stopped, restarted, or removed.</li>
 *   <li><b>Rate limiting</b> — destructive operations (stop / restart / remove) are
 *       capped at {@value #RATE_LIMIT_MAX} per minute to prevent runaway automation.</li>
 *   <li><b>Force guard</b> — removing a running container requires {@code force=true}.</li>
 *   <li><b>Volume safety</b> — {@code removeContainer} never auto-removes volumes.</li>
 * </ul>
 *
 * <h3>Log streaming</h3>
 * Active log streams are tracked in {@code activeLogStreams} keyed by WebSocket
 * session ID. Call {@link #stopLogStream(String)} (or let
 * {@code WebSocketEventListener} do it on disconnect) to release resources.
 */
@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);

    private static final String SELF_LABEL        = "serverwatch.managed";
    private static final int    RATE_LIMIT_MAX     = 10;
    private static final int    STATS_TIMEOUT_SEC  = 5;
    private static final int    LOG_TIMEOUT_SEC    = 10;

    private final DockerClient       dockerClient;
    private final WebSocketPublisher wsPublisher;

    /** Session-ID → active log stream handle. */
    private final Map<String, Closeable> activeLogStreams  = new ConcurrentHashMap<>();
    /** Rolling count of destructive ops in the current minute window. */
    private final AtomicInteger          destructiveCount  = new AtomicInteger(0);

    public DockerService(DockerClient dockerClient, WebSocketPublisher wsPublisher) {
        this.dockerClient = dockerClient;
        this.wsPublisher  = wsPublisher;
    }

    // =========================================================================
    // Container listing
    // =========================================================================

    /**
     * Lists containers, optionally including stopped/exited ones.
     *
     * @param showAll {@code true} to include stopped containers
     * @return list of {@link ContainerInfoDTO}
     */
    public List<ContainerInfoDTO> listContainers(boolean showAll) {
        return dockerClient.listContainersCmd()
                .withShowAll(showAll)
                .exec()
                .stream()
                .map(this::toContainerInfo)
                .toList();
    }

    /**
     * Full container inspection including environment variables, mounts,
     * resource limits, restart policy, and health-check config.
     *
     * @param containerId container ID (short, full) or name
     * @return raw {@link InspectContainerResponse} — serialised to JSON as-is
     */
    public InspectContainerResponse inspectContainer(String containerId) {
        return dockerClient.inspectContainerCmd(containerId).exec();
    }

    // =========================================================================
    // Container stats
    // =========================================================================

    /**
     * Returns a single CPU/memory/network snapshot for one container.
     *
     * @param containerId container ID or name
     * @return {@link ContainerStatsDTO}
     */
    public ContainerStatsDTO getContainerStats(String containerId) {
        String name = resolveContainerName(containerId);
        return fetchStats(containerId, name);
    }

    /**
     * Returns stats for every running container, collecting them in parallel
     * to minimise wall-clock time.
     *
     * @return list of {@link ContainerStatsDTO}, skipping any container whose
     *         stats call fails
     */
    public List<ContainerStatsDTO> getAllContainerStats() {
        List<Container> running = dockerClient.listContainersCmd().withShowAll(false).exec();

        List<CompletableFuture<Optional<ContainerStatsDTO>>> futures = running.stream()
                .map(c -> {
                    String shortName = primaryName(c.getNames());
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            return Optional.of(fetchStats(c.getId(), shortName));
                        } catch (Exception e) {
                            log.debug("Stats unavailable for {}: {}", shortId(c.getId()), e.getMessage());
                            return Optional.<ContainerStatsDTO>empty();
                        }
                    });
                })
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    // =========================================================================
    // Container lifecycle
    // =========================================================================

    /**
     * Starts a stopped container.
     *
     * @param containerId container ID or name
     * @throws NotModifiedException if already running
     */
    public void startContainer(String containerId) {
        auditLog("START", containerId);
        dockerClient.startContainerCmd(containerId).exec();
        log.info("Container started: {}", shortId(containerId));
    }

    /**
     * Stops a running container, waiting up to {@code timeoutSeconds} before
     * sending SIGKILL.
     *
     * @param containerId    container ID or name
     * @param timeoutSeconds grace period before force-kill (default 10)
     * @throws NotModifiedException if already stopped
     */
    public void stopContainer(String containerId, int timeoutSeconds) {
        checkSelfProtection(containerId);
        checkRateLimit("stop");
        auditLog("STOP", containerId);
        dockerClient.stopContainerCmd(containerId).withTimeout(timeoutSeconds).exec();
        log.info("Container stopped: {} (timeout={}s)", shortId(containerId), timeoutSeconds);
    }

    /**
     * Restarts a container.
     *
     * @param containerId    container ID or name
     * @param timeoutSeconds grace period before force-kill during the stop phase
     */
    public void restartContainer(String containerId, int timeoutSeconds) {
        checkSelfProtection(containerId);
        checkRateLimit("restart");
        auditLog("RESTART", containerId);
        dockerClient.restartContainerCmd(containerId).withTimeout(timeoutSeconds).exec();
        log.info("Container restarted: {} (timeout={}s)", shortId(containerId), timeoutSeconds);
    }

    /**
     * Pauses all processes inside a container using SIGSTOP.
     *
     * @param containerId container ID or name
     */
    public void pauseContainer(String containerId) {
        auditLog("PAUSE", containerId);
        dockerClient.pauseContainerCmd(containerId).exec();
        log.info("Container paused: {}", shortId(containerId));
    }

    /**
     * Resumes a paused container.
     *
     * @param containerId container ID or name
     */
    public void unpauseContainer(String containerId) {
        auditLog("UNPAUSE", containerId);
        dockerClient.unpauseContainerCmd(containerId).exec();
        log.info("Container unpaused: {}", shortId(containerId));
    }

    /**
     * Removes a container. A running container requires {@code force=true}.
     * Volumes are never auto-removed.
     *
     * @param containerId container ID or name
     * @param force       {@code true} to remove even if running
     * @throws ConflictException        if running and force is false
     * @throws IllegalArgumentException if attempting force-remove of self-protected container
     */
    public void removeContainer(String containerId, boolean force) {
        checkSelfProtection(containerId);
        checkRateLimit("remove");
        auditLog("REMOVE", containerId);
        dockerClient.removeContainerCmd(containerId)
                .withForce(force)
                .withRemoveVolumes(false) // never auto-remove volumes
                .exec();
        log.info("Container removed: {} (force={})", shortId(containerId), force);
    }

    // =========================================================================
    // Container logs
    // =========================================================================

    /**
     * Fetches the last {@code tailLines} log lines from a container.
     *
     * @param containerId container ID or name
     * @param tailLines   how many trailing lines to return
     * @param stdout      include stdout
     * @param stderr      include stderr
     * @return {@link ContainerLogDTO} with collected lines
     */
    public ContainerLogDTO getContainerLogs(String containerId, int tailLines,
                                            boolean stdout, boolean stderr) {
        List<String> lines = new ArrayList<>();
        try {
            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    String line = new String(frame.getPayload(), StandardCharsets.UTF_8).stripTrailing();
                    if (!line.isBlank()) {
                        lines.add(line);
                    }
                }
            };
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(stdout)
                    .withStdErr(stderr)
                    .withTail(tailLines)
                    .withTimestamps(true)
                    .exec(callback)
                    .awaitCompletion(LOG_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Log fetch interrupted for {}", shortId(containerId));
        }
        return new ContainerLogDTO(
                shortId(containerId),
                List.copyOf(lines),
                Instant.now(),
                stdout,
                stderr
        );
    }

    // =========================================================================
    // Real-time log streaming via WebSocket
    // =========================================================================

    /**
     * Starts streaming container logs to a specific WebSocket session.
     * Sends the last 100 historical lines first, then follows new output.
     * Any previously active stream for the same session is closed first.
     *
     * <p>Each log line is pushed to {@code /user/{sessionId}/queue/container-logs}.
     *
     * @param containerId container ID or name
     * @param wsSessionId the STOMP session ID of the requesting client
     */
    public void streamContainerLogs(String containerId, String wsSessionId) {
        stopLogStream(wsSessionId); // close any existing stream

        ResultCallback.Adapter<Frame> handle = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                String line = new String(frame.getPayload(), StandardCharsets.UTF_8).stripTrailing();
                if (!line.isBlank()) {
                    boolean isStderr = frame.getStreamType() == StreamType.STDERR;
                    wsPublisher.sendContainerLogLine(wsSessionId, shortId(containerId), line, isStderr);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Log stream error for container {}: {}", shortId(containerId), t.getMessage());
                activeLogStreams.remove(wsSessionId);
            }

            @Override
            public void onComplete() {
                log.debug("Log stream ended for container {}", shortId(containerId));
                activeLogStreams.remove(wsSessionId);
            }
        };

        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withTail(100)
                .exec(handle);

        activeLogStreams.put(wsSessionId, handle);
        log.info("Log stream started: container={} session={}", shortId(containerId), wsSessionId);
    }

    /**
     * Stops the active log stream for the given WebSocket session, if any.
     *
     * @param wsSessionId the STOMP session ID
     */
    public void stopLogStream(String wsSessionId) {
        Closeable stream = activeLogStreams.remove(wsSessionId);
        if (stream != null) {
            try {
                stream.close();
                log.debug("Log stream closed for session {}", wsSessionId);
            } catch (IOException e) {
                log.warn("Error closing log stream for session {}: {}", wsSessionId, e.getMessage());
            }
        }
    }

    // =========================================================================
    // Image operations
    // =========================================================================

    /**
     * Lists all locally available Docker images.
     *
     * @return list of {@link ImageDTO}
     */
    public List<ImageDTO> listImages() {
        return dockerClient.listImagesCmd().exec()
                .stream()
                .map(this::toImageDTO)
                .toList();
    }

    // =========================================================================
    // Docker system info
    // =========================================================================

    /**
     * Returns Docker daemon system information.
     *
     * @return {@link DockerInfoDTO}
     */
    public DockerInfoDTO getDockerInfo() {
        Info info = dockerClient.infoCmd().exec();
        String apiVersion = "unknown";
        try {
            apiVersion = dockerClient.versionCmd().exec().getApiVersion();
        } catch (Exception ignored) {}

        return new DockerInfoDTO(
                info.getServerVersion(),
                apiVersion,
                nvl(info.getContainersRunning()),
                nvl(info.getContainersPaused()),
                nvl(info.getContainersStopped()),
                nvl(info.getImages()),
                info.getDriver(),
                info.getOperatingSystem(),
                info.getArchitecture(),
                nvl(info.getMemTotal()),
                info.getName()
        );
    }

    /**
     * Returns the number of currently running containers, or {@code -1} if
     * the Docker socket is unavailable.
     */
    public int getRunningContainerCount() {
        try {
            return dockerClient.listContainersCmd().withShowAll(false).exec().size();
        } catch (Exception e) {
            return -1;
        }
    }

    // =========================================================================
    // Rate-limit reset (runs every 60 s)
    // =========================================================================

    @Scheduled(fixedDelay = 60_000)
    public void resetDestructiveOpsCounter() {
        int prev = destructiveCount.getAndSet(0);
        if (prev > 0) {
            log.debug("Rate-limit counter reset ({} destructive ops in last window)", prev);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    // --- Stats calculation ---------------------------------------------------

    private ContainerStatsDTO fetchStats(String containerId, String containerName) {
        AtomicReference<Statistics> ref = new AtomicReference<>();
        try {
            ResultCallback.Adapter<Statistics> cb = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Statistics s) { ref.set(s); }
            };
            dockerClient.statsCmd(containerId).withNoStream(true)
                    .exec(cb).awaitCompletion(STATS_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stats collection interrupted for " + shortId(containerId));
        }
        Statistics stats = ref.get();
        if (stats == null) {
            throw new IllegalStateException("No stats received for " + shortId(containerId));
        }
        return buildStatsDTO(shortId(containerId), containerName, stats);
    }

    private ContainerStatsDTO buildStatsDTO(String shortId, String name, Statistics s) {
        // ── CPU ──────────────────────────────────────────────────────────────
        double cpuPct = 0.0;
        var cpu    = s.getCpuStats();
        var preCpu = s.getPreCpuStats();
        if (cpu != null && preCpu != null
                && cpu.getCpuUsage() != null && preCpu.getCpuUsage() != null) {
            long  cpuDelta = safeL(cpu.getCpuUsage().getTotalUsage())
                           - safeL(preCpu.getCpuUsage().getTotalUsage());
            long  sysDelta = safeL(cpu.getSystemCpuUsage())
                           - safeL(preCpu.getSystemCpuUsage());
            long  numCpus  = safeL(cpu.getOnlineCpus(), 1L);
            if (sysDelta > 0) {
                cpuPct = (cpuDelta * 1.0 / sysDelta) * numCpus * 100.0;
            }
        }

        // ── Memory ───────────────────────────────────────────────────────────
        long memUsage = 0, memLimit = 0;
        double memPct = 0.0;
        var mem = s.getMemoryStats();
        if (mem != null) {
            long raw  = safeL(mem.getUsage());
            memLimit  = safeL(mem.getLimit());
            // Subtract page cache reported by cgroup v1 (StatsConfig.getCache()).
            // On cgroup v2 / Docker Desktop this is null — falls back to 0.
            var sc = mem.getStats();
            long cache = (sc != null && sc.getCache() != null) ? sc.getCache() : 0L;
            memUsage = Math.max(0, raw - cache);
            if (memLimit > 0) memPct = memUsage * 100.0 / memLimit;
        }

        // ── Network I/O ───────────────────────────────────────────────────────
        long rxBytes = 0, txBytes = 0;
        var nets = s.getNetworks();
        if (nets != null) {
            for (var n : nets.values()) {
                rxBytes += safeL(n.getRxBytes());
                txBytes += safeL(n.getTxBytes());
            }
        }

        // ── Block I/O ─────────────────────────────────────────────────────────
        long blkRead = 0, blkWrite = 0;
        var blkio = s.getBlkioStats();
        if (blkio != null && blkio.getIoServiceBytesRecursive() != null) {
            for (var entry : blkio.getIoServiceBytesRecursive()) {
                if ("Read".equalsIgnoreCase(entry.getOp()))  blkRead  += safeL(entry.getValue());
                if ("Write".equalsIgnoreCase(entry.getOp())) blkWrite += safeL(entry.getValue());
            }
        }

        // ── PIDs ─────────────────────────────────────────────────────────────
        int pids = 0;
        if (s.getPidsStats() != null) pids = (int) safeL(s.getPidsStats().getCurrent());

        return new ContainerStatsDTO(
                shortId, name,
                round2(cpuPct),
                memUsage, memLimit, round2(memPct),
                rxBytes, txBytes,
                blkRead, blkWrite,
                pids,
                Instant.now()
        );
    }

    // --- Container → DTO mapping --------------------------------------------

    private ContainerInfoDTO toContainerInfo(Container c) {
        String fullId  = c.getId();
        String shortId = shortId(fullId);
        String name    = primaryName(c.getNames());

        // Ports
        List<PortMapping> ports = List.of();
        if (c.getPorts() != null) {
            ports = Arrays.stream(c.getPorts())
                    .map(p -> new PortMapping(
                            nvl(p.getPrivatePort()),
                            nvl(p.getPublicPort()),
                            p.getType(),
                            p.getIp() != null ? p.getIp() : ""))
                    .toList();
        }

        // Networks
        List<String> networks = List.of();
        if (c.getNetworkSettings() != null && c.getNetworkSettings().getNetworks() != null) {
            networks = new ArrayList<>(c.getNetworkSettings().getNetworks().keySet());
        }

        // Volumes / mounts
        List<String> volumes = List.of();
        if (c.getMounts() != null) {
            volumes = c.getMounts().stream()   // getMounts() returns List<ContainerMount>
                    .map(m -> m.getSource() + ":" + m.getDestination())
                    .toList();
        }

        Map<String, String> labels = c.getLabels() != null ? c.getLabels() : Map.of();

        return new ContainerInfoDTO(
                shortId, fullId, name,
                c.getImage(),
                c.getState(), c.getStatus(),
                Instant.ofEpochSecond(c.getCreated()),
                ports, networks, volumes, labels,
                List.of() // envVars intentionally empty for list; populated on inspect
        );
    }

    private ImageDTO toImageDTO(Image img) {
        String fullId   = img.getId() != null ? img.getId().replace("sha256:", "") : "";
        String shortId  = fullId.length() >= 12 ? fullId.substring(0, 12) : fullId;
        List<String> tags = img.getRepoTags() != null ? Arrays.asList(img.getRepoTags()) : List.of();
        double sizeMb   = img.getSize() != null ? img.getSize() / 1_048_576.0 : 0.0;
        Instant created = img.getCreated() != null ? Instant.ofEpochSecond(img.getCreated()) : Instant.EPOCH;
        return new ImageDTO(shortId, tags, round2(sizeMb), created);
    }

    // --- Safety guards -------------------------------------------------------

    /**
     * Throws {@link IllegalStateException} if the container is protected
     * (labelled {@code serverwatch.managed=true} or name contains "serverwatch").
     */
    private void checkSelfProtection(String containerId) {
        try {
            InspectContainerResponse r = dockerClient.inspectContainerCmd(containerId).exec();
            // Label check
            var cfg = r.getConfig();
            if (cfg != null && cfg.getLabels() != null) {
                if ("true".equalsIgnoreCase(cfg.getLabels().get(SELF_LABEL))) {
                    throw new IllegalStateException(
                            "Operation refused: container '" + containerId
                            + "' is the ServerWatch process itself (label " + SELF_LABEL + "=true)");
                }
            }
            // Name check
            String cname = r.getName() != null ? r.getName().replace("/", "").toLowerCase() : "";
            if (cname.contains("serverwatch")) {
                throw new IllegalStateException(
                        "Operation refused: container '" + cname
                        + "' appears to be the ServerWatch container. "
                        + "Use the host process manager to manage it.");
            }
        } catch (NotFoundException | IllegalStateException e) {
            throw e; // propagate both
        } catch (Exception e) {
            // If inspect fails for any other reason, allow the operation
            log.debug("Self-protection check inconclusive for {}: {}", containerId, e.getMessage());
        }
    }

    /**
     * Enforces the per-minute rate limit on destructive operations.
     *
     * @param opName human-readable operation name for error messages
     * @throws IllegalStateException if the rate limit is exceeded
     */
    private void checkRateLimit(String opName) {
        int current = destructiveCount.incrementAndGet();
        if (current > RATE_LIMIT_MAX) {
            throw new IllegalStateException(
                    "Rate limit exceeded: max " + RATE_LIMIT_MAX
                    + " destructive Docker operations per minute. "
                    + "Operation '" + opName + "' rejected.");
        }
    }

    // --- Utility helpers -----------------------------------------------------

    /** Returns the first container name without the leading "/" */
    private static String primaryName(String[] names) {
        if (names == null || names.length == 0) return "unknown";
        return names[0].startsWith("/") ? names[0].substring(1) : names[0];
    }

    /** Short 12-char container ID — works even if input is already short. */
    private static String shortId(String id) {
        if (id == null) return "unknown";
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    /** Resolves a container name from its ID via inspect; falls back to shortId. */
    private String resolveContainerName(String containerId) {
        try {
            String raw = dockerClient.inspectContainerCmd(containerId).exec().getName();
            return raw != null ? raw.replace("/", "") : shortId(containerId);
        } catch (Exception e) {
            return shortId(containerId);
        }
    }

    /** Structured audit log entry for every lifecycle operation. */
    private static void auditLog(String action, String containerId) {
        log.info("[AUDIT] action={} containerId={} at={}", action, shortId(containerId), Instant.now());
    }

    /** Null-safe Long → long, default 0. */
    private static long safeL(Long v) { return v != null ? v : 0L; }
    /** Null-safe Long → long with explicit default. */
    private static long safeL(Long v, long def) { return v != null ? v : def; }
    /** Null-safe Long → long (for Integer->Long context). */
    private static long safeL(Number v) { return v != null ? v.longValue() : 0L; }

    /** Null-safe Integer → int, default 0. */
    private static int nvl(Integer v)  { return v != null ? v : 0; }
    /** Null-safe Long → long, default 0. */
    private static long nvl(Long v)    { return v != null ? v : 0L; }

    /** Rounds to 2 decimal places. */
    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
