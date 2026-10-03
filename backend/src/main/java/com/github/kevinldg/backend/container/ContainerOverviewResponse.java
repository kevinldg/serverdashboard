package com.github.kevinldg.backend.container;

import java.time.Instant;
import java.util.List;

public record ContainerOverviewResponse(Statistics statistics, List<ContainerSummary> containers) {

    /**
     * @param running containers in state {@code running}
     * @param stopped containers in state {@code created}, {@code exited} or {@code dead}
     */
    public record Statistics(int total, int running, int stopped) {
    }

    /**
     * @param state  Docker state, e.g. {@code running}, {@code exited}, {@code paused}, {@code restarting}
     * @param status human-readable status from Docker, e.g. "Up 3 months (healthy)"
     */
    public record ContainerSummary(String id, String name, String image, String state, String status,
                                   Instant createdAt) {
    }
}
