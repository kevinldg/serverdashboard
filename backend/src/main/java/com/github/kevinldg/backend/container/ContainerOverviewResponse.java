package com.github.kevinldg.backend.container;

import com.github.kevinldg.backend.gameserver.GameServerStatus;

import java.time.Instant;
import java.util.List;

public record ContainerOverviewResponse(Statistics statistics, List<ContainerSummary> containers) {

    /**
     * @param running     containers in state {@code running}
     * @param stopped     containers in state {@code created}, {@code exited} or {@code dead}
     * @param gameServers containers detected or classified as game servers
     */
    public record Statistics(int total, int running, int stopped, int gameServers) {
    }

    /**
     * @param state      Docker state, e.g. {@code running}, {@code exited}, {@code paused}, {@code restarting}
     * @param status     human-readable status from Docker, e.g. "Up 3 months (healthy)"
     * @param gameServer whether the container is a game server, and why
     */
    public record ContainerSummary(String id, String name, String image, String state, String status,
                                   Instant createdAt, GameServerStatus gameServer) {
    }
}
