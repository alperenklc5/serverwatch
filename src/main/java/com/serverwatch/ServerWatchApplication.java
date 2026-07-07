package com.serverwatch;

import com.serverwatch.config.ServerWatchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    org.springframework.security.config.annotation.method.configuration.PrePostMethodSecurityConfiguration.class
})
@EnableScheduling
@EnableConfigurationProperties(ServerWatchProperties.class)
public class ServerWatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerWatchApplication.class, args);
    }
}
