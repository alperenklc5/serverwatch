# Phase 2 — System Metric Collection

## Objective
Implement metric collectors using OSHI library that read CPU, RAM, disk, and network data from the host system on a scheduled interval. Expose these metrics via REST endpoints. Store periodic snapshots in PostgreSQL.

## Prerequisites
- Phase 1 completed — app starts, connects to DB, Docker client configured

## Context
OSHI (`oshi-core`) is a pure Java library that reads hardware/OS metrics without native dependencies. It provides `SystemInfo` as the entry point, from which you access `HardwareAbstractionLayer` (CPU, memory, disks, network) and `OperatingSystem` (processes, file system).

## Step 1: DTOs

### SystemMetricDTO.java
```java
// Fields:
// - cpuUsagePercent (double) — overall CPU usage 0-100
// - cpuCoreCount (int)
// - cpuModelName (String)
// - memoryTotalBytes (long)
// - memoryUsedBytes (long)
// - memoryFreeBytes (long)
// - memoryUsagePercent (double)
// - swapTotalBytes (long)
// - swapUsedBytes (long)
// - diskInfos (List<DiskInfo>)
// - timestamp (Instant)
```

### DiskInfo.java (nested or separate DTO)
```java
// - name (String)
// - mountPoint (String)
// - totalBytes (long)
// - usableBytes (long)
// - usagePercent (double)
// - type (String) — e.g., "ext4", "xfs"
```

### NetworkMetricDTO.java
```java
// - interfaceName (String)
// - displayName (String)
// - macAddress (String)
// - ipv4Addresses (List<String>)
// - bytesReceived (long) — cumulative
// - bytesSent (long) — cumulative
// - receivedPerSecond (long) — calculated delta
// - sentPerSecond (long) — calculated delta
// - packetsReceived (long)
// - packetsSent (long)
// - speed (long) — link speed in bps
// - timestamp (Instant)
```

### ProcessInfoDTO.java
```java
// - pid (int)
// - name (String)
// - cpuPercent (double)
// - memoryBytes (long)
// - memoryPercent (double)
// - user (String)
// - state (String)
// - startTime (Instant)
// - commandLine (String)
```

### UptimeDTO.java
```java
// - uptimeSeconds (long)
// - bootTime (Instant)
// - formattedUptime (String) — e.g., "3d 14h 22m"
// - osName (String)
// - osVersion (String)
// - hostname (String)
```

## Step 2: OSHI Configuration

### OshiConfig.java
Create a `@Configuration` class that exposes a singleton `SystemInfo` bean and a `HardwareAbstractionLayer` bean. OSHI's `SystemInfo` is thread-safe for reading.

```java
@Configuration
public class OshiConfig {

    @Bean
    public SystemInfo systemInfo() {
        return new SystemInfo();
    }

    @Bean
    public HardwareAbstractionLayer hardware(SystemInfo si) {
        return si.getHardware();
    }

    @Bean
    public OperatingSystem operatingSystem(SystemInfo si) {
        return si.getOperatingSystem();
    }
}
```

## Step 3: Collectors

Each collector is a `@Component` with a method that reads and returns the current metric state. They do NOT schedule themselves — the `MetricService` orchestrates the collection cycle.

### SystemMetricCollector.java

**CPU Usage Calculation:** OSHI's `CentralProcessor.getSystemCpuLoad(long delay)` blocks for the specified delay. Instead, use the tick-based approach:
1. On first call, store `prevTicks = processor.getSystemCpuLoadTicks()`
2. On subsequent calls, compute `processor.getSystemCpuLoadBetweenTicks(prevTicks)` and update `prevTicks`
3. This gives non-blocking, accurate CPU measurement

```java
@Component
public class SystemMetricCollector {

    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private long[] prevTicks;

    // Constructor injection

    public SystemMetricDTO collect() {
        // 1. CPU: use getSystemCpuLoadBetweenTicks(prevTicks)
        //    multiply by 100 for percentage, round to 2 decimal places
        //    update prevTicks after calculation
        //
        // 2. Memory: hardware.getMemory()
        //    total = memory.getTotal()
        //    available = memory.getAvailable()
        //    used = total - available
        //    percent = (used * 100.0) / total
        //
        // 3. Swap: memory.getVirtualMemory()
        //    swapTotal = vm.getSwapTotal()
        //    swapUsed = vm.getSwapUsed()
        //
        // 4. Disks: os.getFileSystem().getFileStores()
        //    for each store: totalSpace, usableSpace, mount, type
        //
        // 5. Uptime/OS info: os.getSystemUptime(), os.toString()
        //
        // Return a fully populated SystemMetricDTO
    }
}
```

### NetworkMetricCollector.java

**Traffic rate calculation:** Store previous byte counts and timestamps. On each collection, compute delta bytes / delta time = bytes per second.

```java
@Component
public class NetworkMetricCollector {

    private final HardwareAbstractionLayer hardware;
    private final Map<String, long[]> previousBytes = new ConcurrentHashMap<>();
    private final Map<String, Instant> previousTimestamps = new ConcurrentHashMap<>();

    public List<NetworkMetricDTO> collect() {
        // hardware.getNetworkIFs(true) — true = update stats
        // For each NetworkIF:
        //   1. Get current bytesRecv, bytesSent
        //   2. Look up previous values in the map
        //   3. Calculate delta / elapsed seconds
        //   4. Store current as previous for next cycle
        //   5. Filter out loopback (lo) and docker bridge (docker0, br-xxx) interfaces
        //      unless they have significant traffic
        //   6. Build NetworkMetricDTO
    }
}
```

### ProcessCollector.java

```java
@Component
public class ProcessCollector {

    private final OperatingSystem os;

    public List<ProcessInfoDTO> collectTopProcesses(int limit) {
        // os.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC, limit)
        // For each OSProcess:
        //   pid, name, cpuPercent (getProcessCpuLoadBetweenTicks),
        //   residentSetSize (RSS), user, state, startTime, commandLine
        // Return top N by CPU usage
    }
}
```

## Step 4: MetricService

### MetricService.java

This is the orchestrator. It runs on a fixed schedule, calls all collectors, caches the latest result, and optionally persists snapshots.

```java
@Service
public class MetricService {

    private final SystemMetricCollector systemCollector;
    private final NetworkMetricCollector networkCollector;
    private final ProcessCollector processCollector;
    private final MetricSnapshotRepository snapshotRepo;
    private final ServerWatchProperties properties;

    // Cache the latest metrics in-memory (AtomicReference or volatile)
    private volatile SystemMetricDTO latestSystemMetric;
    private volatile List<NetworkMetricDTO> latestNetworkMetrics;

    @Scheduled(fixedDelayString = "${serverwatch.collector.interval-ms}")
    public void collectAll() {
        // 1. Collect system metrics
        // 2. Collect network metrics
        // 3. Update cached values
        // 4. Every 60 seconds (use a counter), persist a snapshot to DB
        //    — this avoids writing every 2 seconds
        // 5. Log at DEBUG level
    }

    // Getter methods for latest metrics
    public SystemMetricDTO getLatestSystemMetric() { ... }
    public List<NetworkMetricDTO> getLatestNetworkMetrics() { ... }
    public List<ProcessInfoDTO> getTopProcesses(int limit) { ... }

    // Historical queries
    public List<MetricSnapshot> getHistory(String metricType, Instant from, Instant to) {
        return snapshotRepo.findByMetricTypeAndRecordedAtBetween(metricType, from, to);
    }
}
```

## Step 5: Repository

### MetricSnapshotRepository.java
```java
@Repository
public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {

    List<MetricSnapshot> findByMetricTypeAndRecordedAtBetween(
        String metricType, Instant from, Instant to
    );

    @Modifying
    @Query("DELETE FROM MetricSnapshot m WHERE m.recordedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
```

### Scheduled cleanup — add to MetricService:
```java
@Scheduled(cron = "0 0 * * * *") // every hour
public void cleanupOldSnapshots() {
    Instant cutoff = Instant.now().minus(
        properties.getCollector().getHistoryRetentionHours(), ChronoUnit.HOURS
    );
    int deleted = snapshotRepo.deleteOlderThan(cutoff);
    log.info("Cleaned up {} old metric snapshots", deleted);
}
```

## Step 6: REST Controllers

### MetricController.java
```
GET /api/metrics/system          → SystemMetricDTO (latest cached)
GET /api/metrics/network         → List<NetworkMetricDTO> (latest cached)
GET /api/metrics/processes       → List<ProcessInfoDTO> (top 20)
GET /api/metrics/processes?limit=50  → adjustable limit
GET /api/metrics/uptime          → UptimeDTO
GET /api/metrics/history?type=CPU&hours=6  → List<MetricSnapshot>
```

All endpoints return JSON with proper HTTP status codes. Wrap in a standard `ApiResponse<T>` wrapper:
```java
public record ApiResponse<T>(
    boolean success,
    T data,
    String message,
    Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now());
    }
}
```

## Step 7: Global Exception Handler

### GlobalExceptionHandler.java
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Handle:
    // - IllegalArgumentException → 400
    // - EntityNotFoundException → 404
    // - Exception → 500
    // All return ApiResponse.error(message) with appropriate HTTP status
}
```

## Acceptance Criteria
- [ ] `GET /api/metrics/system` returns current CPU, RAM, disk data with realistic values
- [ ] `GET /api/metrics/network` returns network interface data with traffic rates (bytes/sec)
- [ ] `GET /api/metrics/processes` returns top processes sorted by CPU usage
- [ ] `GET /api/metrics/uptime` returns system uptime and OS information
- [ ] Metrics update every 2 seconds (visible in DEBUG logs)
- [ ] Snapshots persist to `metric_snapshots` table every 60 seconds
- [ ] `GET /api/metrics/history?type=CPU_USAGE&hours=1` returns historical data
- [ ] CPU usage calculation is non-blocking (tick-based, not delay-based)
- [ ] Network traffic shows bytes/second (delta calculation working)
- [ ] Application does not crash if a collector fails — errors are logged, other collectors continue

## Files to Create
```
src/main/java/com/serverwatch/
├── config/
│   └── OshiConfig.java
├── model/
│   ├── dto/
│   │   ├── SystemMetricDTO.java
│   │   ├── DiskInfo.java
│   │   ├── NetworkMetricDTO.java
│   │   ├── ProcessInfoDTO.java
│   │   ├── UptimeDTO.java
│   │   └── ApiResponse.java
│   └── entity/
│       └── MetricSnapshot.java
├── collector/
│   ├── SystemMetricCollector.java
│   ├── NetworkMetricCollector.java
│   └── ProcessCollector.java
├── repository/
│   └── MetricSnapshotRepository.java
├── service/
│   └── MetricService.java
├── controller/
│   └── MetricController.java
└── exception/
    └── GlobalExceptionHandler.java
```
