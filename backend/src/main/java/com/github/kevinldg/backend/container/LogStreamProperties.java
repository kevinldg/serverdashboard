package com.github.kevinldg.backend.container;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Limits for live log streams.
 *
 * @param maxDuration       a stream ends after this time (the user can resume it), so forgotten tabs do not keep it open
 * @param heartbeatInterval keep-alive interval; the user's access is also re-checked at this interval
 * @param maxConcurrent     maximum number of simultaneous streams across all users
 */
@ConfigurationProperties("app.log-stream")
public record LogStreamProperties(Duration maxDuration, Duration heartbeatInterval, int maxConcurrent) {
}
