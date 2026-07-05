package com.serverwatch.collector;

import com.serverwatch.model.dto.ProcessInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.List;

/**
 * Collects information about the top OS processes sorted by CPU usage.
 */
@Component
public class ProcessCollector {

    private static final Logger log = LoggerFactory.getLogger(ProcessCollector.class);

    private final OperatingSystem os;
    private final HardwareAbstractionLayer hardware;

    public ProcessCollector(OperatingSystem os, HardwareAbstractionLayer hardware) {
        this.os       = os;
        this.hardware = hardware;
    }

    /**
     * Returns the top {@code limit} processes ordered by descending CPU usage.
     *
     * @param limit maximum number of processes to return
     * @return list of {@link ProcessInfoDTO}
     */
    public List<ProcessInfoDTO> collectTopProcesses(int limit) {
        long memTotal = hardware.getMemory().getTotal();

        List<OSProcess> processes = os.getProcesses(
                null,
                OperatingSystem.ProcessSorting.CPU_DESC,
                limit
        );

        return processes.stream()
                .map(p -> toDto(p, memTotal))
                .toList();
    }

    // -------------------------------------------------------------------------

    private ProcessInfoDTO toDto(OSProcess p, long memTotal) {
        double cpuPercent = p.getProcessCpuLoadBetweenTicks(null) * 100.0;
        long   rss        = p.getResidentSetSize();
        double memPercent = memTotal > 0 ? (rss * 100.0) / memTotal : 0.0;

        Instant startTime;
        try {
            startTime = Instant.ofEpochMilli(p.getStartTime());
        } catch (Exception e) {
            startTime = Instant.EPOCH;
        }

        String cmdLine = p.getCommandLine();
        if (cmdLine == null || cmdLine.isBlank()) {
            cmdLine = p.getName();
        }

        return new ProcessInfoDTO(
                p.getProcessID(),
                p.getName(),
                Math.round(cpuPercent * 100.0) / 100.0,
                rss,
                Math.round(memPercent * 100.0) / 100.0,
                p.getUser(),
                p.getState().name(),
                startTime,
                cmdLine
        );
    }
}
