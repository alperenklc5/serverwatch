package com.serverwatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

/**
 * Exposes singleton OSHI beans.
 * {@link SystemInfo} is thread-safe for concurrent reads.
 */
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
