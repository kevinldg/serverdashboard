package com.github.kevinldg.backend.container;

import java.util.List;

public record ContainerLogsResponse(List<LogLine> lines) {

    /**
     * @param timestamp RFC 3339 timestamp from Docker, or null if missing
     * @param stream    {@code stdout} or {@code stderr}
     */
    public record LogLine(String timestamp, String stream, String message) {
    }
}
