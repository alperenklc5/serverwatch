package com.serverwatch.collector;

import com.serverwatch.model.dto.DiskInfo;
import com.serverwatch.model.dto.SystemMetricDTO;
import com.serverwatch.model.dto.UptimeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.VirtualMemory;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads CPU, memory, swap, and disk metrics from the host via OSHI.
 *
 * <p>CPU measurement is non-blocking: on the first call prevTicks is initialised
 * from the processor; subsequent calls compute the load between the previous and
 * current tick arrays, then store the new ticks for the next cycle.
 */
@Component
public class SystemMetricCollector {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricCollector.class);

    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private long[] prevTicks;

    public SystemMetricCollector(HardwareAbstractionLayer hardware, OperatingSystem os) {
        this.hardware  = hardware;
        this.os        = os;
        this.prevTicks = hardware.getProcessor().getSystemCpuLoadTicks();
    }

    /**
     * Collects a full system metric snapshot.
     *
     * @return current {@link SystemMetricDTO}
     */
    public SystemMetricDTO collect() {
        CentralProcessor processor = hardware.getProcessor();

        // --- CPU ---
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks);
        prevTicks = processor.getSystemCpuLoadTicks();
        double cpuPercent = round(cpuLoad * 100.0);
        int    coreCount  = processor.getLogicalProcessorCount();
        String cpuModel   = processor.getProcessorIdentifier().getName().trim();

        // --- Memory ---
        GlobalMemory  memory = hardware.getMemory();
        long memTotal     = memory.getTotal();
        long memAvailable = memory.getAvailable();
        long memUsed      = memTotal - memAvailable;
        double memPercent = memTotal > 0 ? round((memUsed * 100.0) / memTotal) : 0.0;

        // --- Swap ---
        VirtualMemory vm        = memory.getVirtualMemory();
        long swapTotal = vm.getSwapTotal();
        long swapUsed  = vm.getSwapUsed();

        // --- Disks ---
        List<DiskInfo> diskInfos = new ArrayList<>();
        for (OSFileStore store : os.getFileSystem().getFileStores(true)) {
            long total   = store.getTotalSpace();
            long usable  = store.getUsableSpace();
            long used    = total - usable;
            double usage = total > 0 ? round((used * 100.0) / total) : 0.0;
            diskInfos.add(new DiskInfo(
                    store.getName(),
                    store.getMount(),
                    total,
                    usable,
                    usage,
                    store.getType()
            ));
        }

        return new SystemMetricDTO(
                cpuPercent, coreCount, cpuModel,
                memTotal, memUsed, memAvailable, memPercent,
                swapTotal, swapUsed,
                diskInfos,
                Instant.now()
        );
    }

    /**
     * Collects uptime and OS identification information.
     *
     * @return current {@link UptimeDTO}
     */
    public UptimeDTO collectUptime() {
        long uptimeSeconds = os.getSystemUptime();
        Instant bootTime   = Instant.ofEpochSecond(os.getSystemBootTime());

        String formatted = formatUptime(uptimeSeconds);

        oshi.software.os.OperatingSystem.OSVersionInfo versionInfo = os.getVersionInfo();
        String osName    = os.getFamily();
        String osVersion = versionInfo.getVersion() + " " + versionInfo.getBuildNumber();
        String hostname  = os.getNetworkParams().getHostName();

        return new UptimeDTO(uptimeSeconds, bootTime, formatted, osName, osVersion, hostname);
    }

    // -------------------------------------------------------------------------

    private static double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static String formatUptime(long seconds) {
        long days    = seconds / 86400;
        long hours   = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600)  / 60;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }
}
