package com.serverwatch.collector;

import com.serverwatch.model.dto.NetworkMetricDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads network interface statistics from OSHI and computes per-second
 * traffic rates by comparing successive samples.
 */
@Component
public class NetworkMetricCollector {

    private static final Logger log = LoggerFactory.getLogger(NetworkMetricCollector.class);

    private final HardwareAbstractionLayer hardware;

    /** Stores [bytesRecv, bytesSent] from the previous cycle, keyed by interface name. */
    private final Map<String, long[]>    previousBytes      = new ConcurrentHashMap<>();
    private final Map<String, Instant>   previousTimestamps = new ConcurrentHashMap<>();

    public NetworkMetricCollector(HardwareAbstractionLayer hardware) {
        this.hardware = hardware;
    }

    /**
     * Returns metrics for all relevant network interfaces.
     * Loopback (lo) is excluded; docker bridge interfaces are included when present.
     *
     * @return list of {@link NetworkMetricDTO}, one per interface
     */
    public List<NetworkMetricDTO> collect() {
        List<NetworkIF> interfaces  = hardware.getNetworkIFs(true); // true = update stats
        Instant         now         = Instant.now();
        List<NetworkMetricDTO> result = new ArrayList<>();

        for (NetworkIF iface : interfaces) {
            String name = iface.getName();

            // Skip pure loopback
            if ("lo".equalsIgnoreCase(name) || name.startsWith("lo:")) {
                continue;
            }

            long bytesRecv = iface.getBytesRecv();
            long bytesSent = iface.getBytesSent();

            long recvPerSec = 0;
            long sentPerSec = 0;

            if (previousBytes.containsKey(name)) {
                long[] prev    = previousBytes.get(name);
                Instant prevTs = previousTimestamps.get(name);
                double elapsedSec = (now.toEpochMilli() - prevTs.toEpochMilli()) / 1000.0;

                if (elapsedSec > 0) {
                    long deltaRecv = Math.max(0, bytesRecv - prev[0]);
                    long deltaSent = Math.max(0, bytesSent - prev[1]);
                    recvPerSec = (long) (deltaRecv / elapsedSec);
                    sentPerSec = (long) (deltaSent / elapsedSec);
                }
            }

            previousBytes.put(name, new long[]{bytesRecv, bytesSent});
            previousTimestamps.put(name, now);

            List<String> ipv4 = List.copyOf(iface.getIPv4addr() != null
                    ? List.of(iface.getIPv4addr())
                    : List.of());

            result.add(new NetworkMetricDTO(
                    name,
                    iface.getDisplayName(),
                    iface.getMacaddr(),
                    ipv4,
                    bytesRecv,
                    bytesSent,
                    recvPerSec,
                    sentPerSec,
                    iface.getPacketsRecv(),
                    iface.getPacketsSent(),
                    iface.getSpeed(),
                    now
            ));
        }

        return result;
    }
}
