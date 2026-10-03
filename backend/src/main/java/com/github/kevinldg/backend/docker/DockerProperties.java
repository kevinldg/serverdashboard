package com.github.kevinldg.backend.docker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Connection to the Docker Engine API.
 *
 * @param host            e.g. {@code unix:///var/run/docker.sock} (production) or {@code tcp://localhost:2375}
 *                        (development via SSH tunnel)
 * @param connectTimeout  how long to wait for a connection to the Docker host
 * @param responseTimeout how long to wait for a response from the Docker host; must exceed {@code stopTimeout}
 * @param stopTimeout     how long a container may take to shut down cleanly before Docker kills it
 */
@ConfigurationProperties("app.docker")
public record DockerProperties(String host, Duration connectTimeout, Duration responseTimeout, Duration stopTimeout) {
}
