package com.serverwatch.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

/**
 * Configures and validates the Docker client bean.
 * If the Docker socket is unavailable the application continues without crashing —
 * Docker-dependent features will be disabled until the socket becomes available.
 */
@Configuration
public class DockerConfig {

    private static final Logger log = LoggerFactory.getLogger(DockerConfig.class);

    private final ServerWatchProperties properties;

    public DockerConfig(ServerWatchProperties properties) {
        this.properties = properties;
    }

    @Bean
    public DockerClient dockerClient() {
        String socketPath = properties.getDocker().getSocketPath();

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(socketPath)
            .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(URI.create(socketPath))
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(5))
            .responseTimeout(Duration.ofSeconds(30))
            .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    @PostConstruct
    public void verifyDockerConnectivity() {
        try {
            dockerClient().pingCmd().exec();
            log.info("Docker connectivity verified — socket reachable at {}",
                properties.getDocker().getSocketPath());
        } catch (Exception e) {
            log.warn("Docker socket not available at {} — Docker features will be disabled. Reason: {}",
                properties.getDocker().getSocketPath(), e.getMessage());
        }
    }
}
